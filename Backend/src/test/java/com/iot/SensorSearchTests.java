package com.iot;

import com.iot.domain.entity.SensorData;
import com.iot.domain.repository.SensorDataRepository;
import com.iot.domain.repository.SensorRepository;
import com.iot.sensor.service.SensorDataService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_sensor_search;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("test")
@Transactional
class SensorSearchTests {
    @Autowired private SensorDataService service;
    @Autowired private SensorDataRepository readings;
    @Autowired private SensorRepository sensors;

    @BeforeEach
    void seed() {
        readings.deleteAll();
        readings.save(new SensorData(sensors.findById(1).orElseThrow(), new BigDecimal("2026"),
                LocalDateTime.parse("2025-01-01T10:00:00")));
        readings.save(new SensorData(sensors.findById(2).orElseThrow(), new BigDecimal("50"),
                LocalDateTime.parse("2026-10-21T17:00:12")));
        readings.save(new SensorData(sensors.findById(3).orElseThrow(), new BigDecimal("3000"),
                LocalDateTime.parse("2024-03-01T10:00:00")));
    }

    @Test
    void allSearchMatchesTimeOrNumericValueRatherThanRequiringBoth() {
        assertEquals(2, service.search("2026", null, null, 0, 20).totalElements());
        assertEquals(1, service.search("2026-10-21 17:00", null, null, 0, 20).totalElements());
        assertEquals(2, service.search("2026", null, null, 0, 1).totalPages());
    }

    @Test
    void namesAndValuesStillWorkAndInvalidTimeIsAnOrdinarySearchTerm() {
        assertEquals(1, service.search("độ sáng", null, null, 0, 20).totalElements());
        assertEquals(1, service.search("50.0", null, null, 0, 20).totalElements());
        assertEquals(0, service.search("2026-13", null, null, 0, 20).totalElements());
        assertEquals(0, service.search("unknown", null, null, 0, 20).totalElements());
    }

    @Test
    void sensorAndExplicitTimeFiltersStillRestrictCombinedSearch() {
        assertEquals(1, service.search("2026", "Nhiệt độ (°C)", null, 0, 20).totalElements());
        assertEquals(0, service.search("2026-10", "Nhiệt độ (°C)", null, 0, 20).totalElements());
        assertEquals(1, service.search(null, null, "2026-10", 0, 20).totalElements());
    }

    @Test
    void twoParameterSearchWithModeWorksSeamlessly() {
        assertEquals(2, service.search("2026", null, 0, 20).totalElements());
        assertEquals(1, service.search("2026-10", "time", 0, 20).totalElements());
        assertEquals(1, service.search(null, "Nhiệt độ (°C)", 0, 20).totalElements());
    }
}
