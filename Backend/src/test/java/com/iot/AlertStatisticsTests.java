package com.iot;

import com.iot.common.enums.DeviceState;
import com.iot.common.exception.ApiException;
import com.iot.dashboard.service.AlertStatisticsService;
import com.iot.device.service.DeviceService;
import com.iot.domain.entity.AlertHistory;
import com.iot.domain.repository.AlertHistoryRepository;
import com.iot.domain.repository.ActionHistoryRepository;
import com.iot.domain.repository.UserRepository;
import com.iot.history.service.ActionHistoryService;
import com.iot.common.enums.ActionResult;
import com.iot.mqtt.DeviceStatusEvent;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_alert_statistics;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("test")
@Transactional
class AlertStatisticsTests {
    @Autowired private DeviceService devices;
    @Autowired private AlertHistoryRepository histories;
    @Autowired private ActionHistoryRepository actionHistories;
    @Autowired private UserRepository users;
    @Autowired private ActionHistoryService actionHistoryService;
    @Autowired private AlertStatisticsService statistics;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        histories.deleteAll();
        actionHistories.deleteAll();
        jdbc.update("INSERT INTO devices (id, devicename, status) VALUES (4, 'Cảnh báo', 'OFF')");
    }

    @Test
    void onlyConfirmedStateTransitionsCountAndDuplicateAcksAreIgnored() {
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.OFF));
        assertEquals(0, histories.count());
        assertEquals(0, actionHistories.count());
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.ON));
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.ON));
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.ON));
        assertEquals(1, histories.count());
        assertEquals(1, actionHistories.count());
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.OFF));
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.OFF));
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.ON));
        devices.receiveDeviceStatus(new DeviceStatusEvent(1, DeviceState.OFF));
        assertEquals(3, histories.count());
        assertEquals(3, actionHistories.count());
        assertEquals(1, users.findAll().stream().filter(user -> user.getUsername().equals("iot-system")).count());
        var saved = actionHistoryService.search(4, null, null, null, 0, 20);
        assertEquals(3, saved.totalElements());
        assertTrue(saved.content().stream().allMatch(item -> item.username().equals("iot-system")));
        assertTrue(saved.content().stream().allMatch(item -> item.status() == ActionResult.valueOf(item.action().name())));
        assertEquals(2, actionHistoryService.search(4, DeviceState.ON, ActionResult.ON, null, 0, 20).totalElements());
        assertEquals(1, actionHistoryService.search(4, DeviceState.OFF, ActionResult.OFF, null, 0, 20).totalElements());
        var month = statistics.getMonth(YearMonth.now().toString());
        assertEquals(2, month.totalOn());
        assertEquals(1, month.totalOff());
    }

    @Test
    void actionHistoryApiIncludesConfirmedAutomaticActions() throws Exception {
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.ON));
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.ON));
        devices.receiveDeviceStatus(new DeviceStatusEvent(4, DeviceState.OFF));
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/action-history").with(user("admin")).param("deviceId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].username").value("iot-system"));
    }

    @Test
    void monthlyCountsGroupEachDayAndExcludeOtherMonthsIncludingLeapDay() {
        histories.save(new AlertHistory(DeviceState.ON, LocalDateTime.parse("2028-01-31T23:59:59")));
        histories.save(new AlertHistory(DeviceState.ON, LocalDateTime.parse("2028-02-01T00:00:00")));
        histories.save(new AlertHistory(DeviceState.OFF, LocalDateTime.parse("2028-02-01T10:00:00")));
        histories.save(new AlertHistory(DeviceState.ON, LocalDateTime.parse("2028-02-29T12:00:00")));
        histories.save(new AlertHistory(DeviceState.ON, LocalDateTime.parse("2028-02-29T23:59:59")));
        histories.save(new AlertHistory(DeviceState.OFF, LocalDateTime.parse("2028-03-01T00:00:00")));

        var month = statistics.getMonth("2028-02");
        assertEquals(29, month.days().size());
        assertEquals(3, month.totalOn());
        assertEquals(1, month.totalOff());
        assertEquals(1, month.days().getFirst().onCount());
        assertEquals(1, month.days().getFirst().offCount());
        assertEquals(0, month.days().get(1).onCount());
        assertEquals(2, month.days().getLast().onCount());
        assertEquals("2028-02-29", month.days().getLast().date().toString());
        assertEquals(1, statistics.getMonth("2028-03").totalOff());
    }

    @Test
    void emptyMonthContainsAllDaysWithZerosAndInvalidMonthsAreRejected() {
        var month = statistics.getMonth("2026-02");
        assertEquals(28, month.days().size());
        assertEquals(0, month.totalOn());
        assertEquals(0, month.totalOff());
        assertTrue(month.days().stream().allMatch(day -> day.onCount() == 0 && day.offCount() == 0));
        for (String invalid : new String[]{"2026-13", "2026-1", "0000-01", "bad", ""}) {
            assertThrows(ApiException.class, () -> statistics.getMonth(invalid));
        }
    }

    @Test
    void endpointRequiresLoginReturnsDailyCountsAndValidatesMonth() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/dashboard/alert-statistics").param("month", "2026-10"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/alert-statistics").with(user("admin")).param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.days.length()").value(31))
                .andExpect(jsonPath("$.data.totalOn").value(0));
        mvc.perform(get("/api/dashboard/alert-statistics").with(user("admin")).param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mvc.perform(get("/api/dashboard/alert-statistics").with(user("admin")))
                .andExpect(status().isBadRequest());
    }
}
