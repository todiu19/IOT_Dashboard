package com.iot.device.service;

import com.iot.common.enums.DeviceState;
import com.iot.domain.repository.SensorRepository;
import com.iot.mqtt.MqttClientService;
import com.iot.mqtt.SensorReadingEvent;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class LightAlertService {
    private static final Logger log = LoggerFactory.getLogger(LightAlertService.class);
    private static final int ALERT_DEVICE_ID = 4;
    private static final BigDecimal THRESHOLD = new BigDecimal("4000");

    private final SensorRepository sensors;
    private final MqttClientService mqtt;
    private DeviceState lastPublishedCommand = DeviceState.OFF;

    public LightAlertService(SensorRepository sensors, MqttClientService mqtt) {
        this.sensors = sensors;
        this.mqtt = mqtt;
    }

    @EventListener
    public synchronized void receiveReading(SensorReadingEvent event) {
        if (!mqtt.isEnabled()) return;
        var sensor = sensors.findById(event.sensorId()).orElse(null);
        if (sensor == null || !"Độ sáng (lux)".equals(sensor.getName())) return;

        // Event đã chứa giá trị ánh sáng sau chuyển đổi, giống trên dashboard.
        int comparison = event.value().compareTo(THRESHOLD);
        DeviceState command = comparison >= 0 ? DeviceState.ON : DeviceState.OFF;
        // Nhiều mẫu vượt ngưỡng liên tiếp thuộc cùng một đợt cảnh báo.
        if (command == lastPublishedCommand) return;
        try {
            mqtt.publishControlAsync(ALERT_DEVICE_ID, command);
            lastPublishedCommand = command;
        } catch (RuntimeException exception) {
            log.warn("Không thể gửi lệnh LED cảnh báo: {}", exception.getMessage());
        }
    }
}
