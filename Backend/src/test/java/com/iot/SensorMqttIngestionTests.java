package com.iot;

import com.iot.common.dto.response.ReadingResponse;
import com.iot.domain.repository.SensorDataRepository;
import com.iot.mqtt.MqttClientService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_sensor_mqtt;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "debug=false",
        "logging.level.root=ERROR"
})
@ActiveProfiles("test")
class SensorMqttIngestionTests {
    @Autowired private MqttClientService mqtt;
    @Autowired private SensorDataRepository readings;
    @MockitoBean private SimpMessagingTemplate messaging;

    @Test
    void esp32PayloadSavesAllReadingsAndBroadcastsSensorUpdates() {
        long countBefore = readings.count();
        mqtt.messageArrived("sensor_data", new MqttMessage(
                "{\"temperature\":30.50,\"humidity\":47.00,\"light\":1370}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(countBefore + 3, readings.count());
        ArgumentCaptor<ReadingResponse> updates = ArgumentCaptor.forClass(ReadingResponse.class);
        verify(messaging, times(3)).convertAndSend(eq("/topic/sensors"), updates.capture());
        Map<String, BigDecimal> expected = Map.of(
                "Nhiệt độ (°C)", new BigDecimal("30.50"),
                "Độ ẩm (%)", new BigDecimal("47.00"),
                "Độ sáng (lux)", new BigDecimal("2725"));
        for (ReadingResponse update : updates.getAllValues()) {
            assertNotNull(update.id());
            assertNotNull(update.sensorId());
            assertNotNull(update.recordedAt());
            assertEquals(0, expected.get(update.sensorName()).compareTo(update.value()));
            var saved = readings.findById(update.id()).orElseThrow();
            assertEquals(0, saved.getValue().compareTo(update.value()));
        }
    }

    @Test
    void invalidEsp32PayloadDoesNotSavePartialReadingsOrBroadcast() {
        long countBefore = readings.count();
        mqtt.messageArrived("sensor_data", new MqttMessage(
                "{\"temperature\":30.50,\"humidity\":\"invalid\",\"light\":1370}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(countBefore, readings.count());
        verifyNoInteractions(messaging);
    }
}
