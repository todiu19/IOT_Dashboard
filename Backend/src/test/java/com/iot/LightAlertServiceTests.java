package com.iot;

import com.iot.common.enums.DeviceState;
import com.iot.common.exception.ApiException;
import com.iot.common.exception.ErrorCode;
import com.iot.device.service.LightAlertService;
import com.iot.domain.entity.Sensor;
import com.iot.domain.repository.SensorRepository;
import com.iot.mqtt.MqttClientService;
import com.iot.mqtt.SensorReadingEvent;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class LightAlertServiceTests {
    private final SensorRepository sensors = mock(SensorRepository.class);
    private final MqttClientService mqtt = mock(MqttClientService.class);
    private final LightAlertService alert = new LightAlertService(sensors, mqtt);

    @BeforeEach
    void setUp() {
        when(mqtt.isEnabled()).thenReturn(true);
        when(sensors.findById(3)).thenReturn(Optional.of(new Sensor("Độ sáng (lux)")));
        when(sensors.findById(1)).thenReturn(Optional.of(new Sensor("Nhiệt độ (°C)")));
    }

    private void reading(String value) {
        alert.receiveReading(new SensorReadingEvent(3, new BigDecimal(value)));
    }

    @Test
    void consecutiveHighReadingsAreOneAlertUntilOffThenHighAgain() {
        reading("4001");
        reading("4050");
        reading("4095");
        reading("4000"); // Bằng ngưỡng không kết thúc đợt hiện tại.
        reading("3999");
        reading("2000");
        reading("4050");
        reading("4095");

        var order = inOrder(mqtt);
        order.verify(mqtt).publishControlAsync(4, DeviceState.ON);
        order.verify(mqtt).publishControlAsync(4, DeviceState.OFF);
        order.verify(mqtt).publishControlAsync(4, DeviceState.ON);
        verify(mqtt, times(2)).publishControlAsync(4, DeviceState.ON);
        verify(mqtt, times(1)).publishControlAsync(4, DeviceState.OFF);
        verify(mqtt, never()).publishControl(any(), any());
    }

    @Test
    void lowReadingsDoNotSendOffBeforeAnyAlertHasStarted() {
        reading("2000");
        reading("2500");
        reading("3000");
        reading("3500");
        reading("3999.99");
        verify(mqtt, never()).publishControlAsync(any(), any());
    }

    @Test
    void exactThresholdStartsAlertAndOffIsOnlySentAfterOn() {
        reading("4000");
        reading("4000.00");
        reading("3999.99");
        reading("2000");
        reading("4000");

        var order = inOrder(mqtt);
        order.verify(mqtt).publishControlAsync(4, DeviceState.ON);
        order.verify(mqtt).publishControlAsync(4, DeviceState.OFF);
        order.verify(mqtt).publishControlAsync(4, DeviceState.ON);
        verify(mqtt, times(2)).publishControlAsync(4, DeviceState.ON);
        verify(mqtt, times(1)).publishControlAsync(4, DeviceState.OFF);
    }

    @Test
    void otherSensorsDoNotPublish() {
        alert.receiveReading(new SensorReadingEvent(1, new BigDecimal("4000")));
        verify(mqtt, never()).publishControlAsync(any(), any());
    }

    @Test
    void brokerFailureIsCaughtAndNextReadingTriesAgain() {
        doThrow(new ApiException(ErrorCode.MQTT_UNAVAILABLE))
                .doNothing().when(mqtt).publishControlAsync(4, DeviceState.ON);
        reading("4000");
        reading("4000");
        reading("4050"); // Sau lần gửi thành công, không gửi trùng nữa.
        verify(mqtt, times(2)).publishControlAsync(4, DeviceState.ON);
    }

    @Test
    void failedOffDoesNotStartAnotherOnEpisode() {
        reading("4000");
        doThrow(new ApiException(ErrorCode.MQTT_UNAVAILABLE))
                .doNothing().when(mqtt).publishControlAsync(4, DeviceState.OFF);
        reading("2000");
        reading("4000");
        verify(mqtt, times(1)).publishControlAsync(4, DeviceState.ON);
        reading("2000");
        reading("4000");
        verify(mqtt, times(2)).publishControlAsync(4, DeviceState.OFF);
        verify(mqtt, times(2)).publishControlAsync(4, DeviceState.ON);
    }

    @Test
    void noCommandWhenMqttDisabled() {
        when(mqtt.isEnabled()).thenReturn(false);
        reading("4000");
        verify(mqtt, never()).publishControlAsync(any(), any());
    }
}
