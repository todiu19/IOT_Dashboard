package com.iot;

import jakarta.servlet.http.Cookie;
import com.iot.common.enums.DeviceState;
import com.iot.domain.entity.ActionHistory;
import com.iot.domain.entity.SensorData;
import com.iot.domain.repository.ActionHistoryRepository;
import com.iot.domain.repository.DeviceRepository;
import com.iot.domain.repository.SensorDataRepository;
import com.iot.domain.repository.SensorRepository;
import com.iot.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class IotDashboardApplicationTests {

	@Autowired
	private WebApplicationContext context;
	@Autowired private SensorDataRepository sensorDataRepository;
	@Autowired private SensorRepository sensorRepository;
	@Autowired private ActionHistoryRepository actionHistoryRepository;
	@Autowired private DeviceRepository deviceRepository;
	@Autowired private UserRepository userRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void swaggerDocumentationIsPublicAndListsCurrentApis() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("IoT Dashboard API"))
				.andExpect(jsonPath("$.components.securitySchemes.jwtCookie.name").value("jwt"))
				.andExpect(jsonPath("$['paths']['/api/dashboard']").exists())
				.andExpect(jsonPath("$['paths']['/api/device/{id}/control']").exists())
				.andExpect(jsonPath("$['paths']['/api/devices']").doesNotExist())
				.andExpect(jsonPath("$['paths']['/actuator/health']").doesNotExist());

		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	void cookieJwtLoginAndFilteredPagesWorkWithoutCsrf() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		mvc.perform(options("/api/dashboard")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		mvc.perform(get("/api/dashboard"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
		mvc.perform(get("/api/profile"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
		mvc.perform(get("/api/profile").cookie(new Cookie("jwt", "invalid-token")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

		mvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.data.username").exists());

		mvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.data").doesNotExist());

		var loginResponse = mvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.username").value("admin"))
				.andExpect(jsonPath("$.errorCode").doesNotExist())
				.andExpect(jsonPath("$.message").doesNotExist())
				.andReturn().getResponse();
		Cookie jwtCookie = loginResponse.getCookie("jwt");
		assertNotNull(jwtCookie);
		assertTrue(jwtCookie.isHttpOnly());
		assertNull(loginResponse.getCookie("XSRF-TOKEN"));

		mvc.perform(options("/api/device/1/control")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
		mvc.perform(get("/api/auth/csrf").cookie(jwtCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("ENDPOINT_NOT_FOUND"));

		mvc.perform(get("/api/auth/me").cookie(jwtCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("ENDPOINT_NOT_FOUND"));
		mvc.perform(get("/api/dashboard").cookie(jwtCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.devices.length()").value(3));
		mvc.perform(get("/api/devices").cookie(jwtCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("ENDPOINT_NOT_FOUND"));
		mvc.perform(get("/api/profile").cookie(jwtCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.username").value("admin"))
				.andExpect(jsonPath("$.data.fullname").exists())
				.andExpect(jsonPath("$.data.msv").value("TEST001"))
				.andExpect(jsonPath("$.data.password").doesNotExist());
		mvc.perform(put("/api/profile")
					.cookie(jwtCookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullname\":\"Nguyễn Đình Tô\",\"email\":null}"))
				.andExpect(status().isMethodNotAllowed());
		mvc.perform(get("/api/unknown").cookie(jwtCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("ENDPOINT_NOT_FOUND"));

		mvc.perform(get("/api/data-sensor")
					.cookie(jwtCookie)
					.param("search", "27.3")
					.param("page", "0")
					.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1));
		long readingsBeforePost = sensorDataRepository.count();
		mvc.perform(post("/api/data-sensor")
					.cookie(jwtCookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"sensorId\":1,\"value\":30.5}"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
		assertEquals(readingsBeforePost, sensorDataRepository.count());

		mvc.perform(post("/api/device/1/control")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"OFF\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
		mvc.perform(post("/api/device/1/control")
					.cookie(new Cookie("jwt", "invalid-token"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"OFF\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

		mvc.perform(get("/api/action-history").cookie(jwtCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));

		mvc.perform(post("/api/device/1/control")
					.cookie(jwtCookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"OFF\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OFF"));

		mvc.perform(get("/api/action-history")
					.cookie(jwtCookie)
					.param("deviceId", "1")
					.param("action", "OFF")
					.param("status", "OFF"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].username").value("admin"));
		mvc.perform(get("/api/action-history")
					.cookie(jwtCookie)
					.param("deviceId", "1")
					.param("action", "OFF")
					.param("status", "OFF")
					.param("search", "unused-history-search")
					.param("page", "0")
					.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].deviceId").value(1))
				.andExpect(jsonPath("$.data.size").value(1));

		LocalDateTime fixedTime = LocalDateTime.parse("2026-10-06T22:46:12");
		sensorDataRepository.save(new SensorData(sensorRepository.findById(1).orElseThrow(), new BigDecimal("26.50"), fixedTime));
		ActionHistory fixedHistory = new ActionHistory(deviceRepository.findById(2).orElseThrow(),
				userRepository.findById(1).orElseThrow(), DeviceState.ON);
		ReflectionTestUtils.setField(fixedHistory, "performedAt", fixedTime);
		fixedHistory.complete(DeviceState.ON);
		actionHistoryRepository.save(fixedHistory);
		for (String prefix : new String[] {"2026-10-06", "2026-10-06 22:46", "2026-10-06 22:46:1", "2026-10-06 22:46:12"}) {
			mvc.perform(get("/api/data-sensor").cookie(jwtCookie).param("time", prefix))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(1));
			mvc.perform(get("/api/action-history").cookie(jwtCookie).param("time", prefix))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(1));
		}
		for (String prefix : new String[] {"2026", "2026-1", "2026-10", "2026-10-0", "2026-10-06 2", "2026-10-06 22:4"}) {
			mvc.perform(get("/api/data-sensor").cookie(jwtCookie).param("time", prefix).param("search", "26.5"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(1));
			mvc.perform(get("/api/action-history").cookie(jwtCookie).param("time", prefix).param("deviceId", "2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(1));
		}
		mvc.perform(get("/api/data-sensor").cookie(jwtCookie).param("time", "2026-10-06 22:47"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mvc.perform(get("/api/data-sensor").cookie(jwtCookie).param("time", "2026-02-30"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

		var logoutResponse = mvc.perform(post("/api/auth/logout").cookie(jwtCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andReturn().getResponse();
		Cookie clearedJwt = logoutResponse.getCookie("jwt");
		assertNotNull(clearedJwt);
		assertEquals(0, clearedJwt.getMaxAge());
		assertEquals("", clearedJwt.getValue());
		assertNull(logoutResponse.getCookie("XSRF-TOKEN"));
		mvc.perform(get("/api/profile").cookie(clearedJwt))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
	}

	@Test
	@Transactional
	void timeFiltersNarrowFromYearToSecondWithoutDateAndKeepPagination() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		var loginResponse = mvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
				.andExpect(status().isOk()).andReturn().getResponse();
		Cookie jwtCookie = loginResponse.getCookie("jwt");
		assertNotNull(jwtCookie);
		var sensor = sensorRepository.findById(2).orElseThrow();
		var device = deviceRepository.findById(3).orElseThrow();
		var user = userRepository.findById(1).orElseThrow();
		String[] timestamps = {
				"2025-12-31T23:59:59", "2026-09-30T23:59:59", "2026-10-05T23:59:59",
				"2026-10-06T21:59:59", "2026-10-06T22:45:59", "2026-10-06T22:46:11",
				"2026-10-06T22:46:12", "2026-10-06T22:46:12.500", "2026-10-06T22:46:13",
				"2026-10-06T22:47:00", "2026-10-06T23:00:00", "2026-10-07T00:00:00",
				"2026-11-01T00:00:00", "2027-01-01T00:00:00"
		};
		for (String timestamp : timestamps) {
			LocalDateTime recordedAt = LocalDateTime.parse(timestamp);
			sensorDataRepository.save(new SensorData(sensor, new BigDecimal("42.00"), recordedAt));
			ActionHistory history = new ActionHistory(device, user, DeviceState.ON);
			ReflectionTestUtils.setField(history, "performedAt", recordedAt);
			history.complete(DeviceState.ON);
			actionHistoryRepository.save(history);
		}
		String[] prefixes = {
				"2026", "2026-10", "2026-10-06", "2026-10-06 22",
				"2026-10-06 22:46", "2026-10-06 22:46:12"
		};
		int[] expectedCounts = {12, 10, 8, 6, 4, 2};
		for (int index = 0; index < prefixes.length; index++) {
			mvc.perform(get("/api/data-sensor").cookie(jwtCookie)
						.param("sensor", sensor.getName()).param("time", prefixes[index]).param("size", "2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(expectedCounts[index]))
					.andExpect(jsonPath("$.data.content.length()").value(2))
					.andExpect(jsonPath("$.data.totalPages").value(expectedCounts[index] / 2));
			mvc.perform(get("/api/action-history").cookie(jwtCookie)
						.param("deviceId", "3").param("action", "ON").param("status", "ON")
						.param("time", prefixes[index]).param("page", "0").param("size", "2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(expectedCounts[index]))
					.andExpect(jsonPath("$.data.content.length()").value(2))
					.andExpect(jsonPath("$.data.totalPages").value(expectedCounts[index] / 2));
		}
		mvc.perform(get("/api/data-sensor").cookie(jwtCookie)
					.param("sensor", sensor.getName()).param("time", "2026").param("date", "2025-01-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(12));
		mvc.perform(get("/api/action-history").cookie(jwtCookie)
					.param("deviceId", "3").param("time", "2026").param("date", "2025-01-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(12));
	}

	@Test
	void legacyCsrfCookieDoesNotBlockLoginProfileOrLogout() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		Cookie legacyCsrfCookie = new Cookie("XSRF-TOKEN", "legacy-token");
		var loginResponse = mvc.perform(post("/api/auth/login")
				.cookie(legacyCsrfCookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
				.andExpect(status().isOk()).andReturn().getResponse();
		Cookie jwtCookie = loginResponse.getCookie("jwt");
		assertNotNull(jwtCookie);
		assertNull(loginResponse.getCookie("XSRF-TOKEN"));
		for (int attempt = 0; attempt < 2; attempt++) {
			var profileResponse = mvc.perform(get("/api/profile").cookie(jwtCookie, legacyCsrfCookie))
					.andExpect(status().isOk()).andReturn().getResponse();
			assertNull(profileResponse.getCookie("XSRF-TOKEN"));
		}
		var logoutResponse = mvc.perform(post("/api/auth/logout").cookie(jwtCookie, legacyCsrfCookie))
				.andExpect(status().isOk()).andReturn().getResponse();
		assertNotNull(logoutResponse.getCookie("jwt"));
		assertEquals(0, logoutResponse.getCookie("jwt").getMaxAge());
		assertNull(logoutResponse.getCookie("XSRF-TOKEN"));
	}
}
