package com.iot.mqtt;

import com.iot.config.MqttProperties;
import com.iot.common.enums.DeviceState;
import com.iot.common.exception.ApiException;
import com.iot.domain.entity.Sensor;
import com.iot.domain.repository.SensorRepository;
import jakarta.annotation.*;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.*;
import org.springframework.context.ApplicationEventPublisher;
import com.iot.common.exception.ErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

@Service
public class MqttClientService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttClientService.class);

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final SensorRepository sensors;
    private MqttAsyncClient client;

    public MqttClientService(
            MqttProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events,
            SensorRepository sensors
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.events = events;
        this.sensors = sensors;
    }

    @PostConstruct
    void initialize() {
        if (!properties.enabled()) return;
        try {
            client = new MqttAsyncClient(properties.brokerUrl(), properties.clientId());
            client.setCallback(this);
            connect();
        } catch (MqttException exception) {
            log.warn("Không thể khởi tạo MQTT client: {}", exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.mqtt.reconnect-delay:10s}")
    synchronized void reconnect() {
        if (properties.enabled() && client != null && !client.isConnected()) connect();
    }

    private void connect() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            if (properties.username() != null && !properties.username().isBlank()) {
                options.setUserName(properties.username());
                options.setPassword(properties.password().toCharArray());
            }
            client.connect(options).waitForCompletion(5000);
        } catch (MqttException exception) {
            log.warn("Không thể kết nối MQTT broker {}: {}", properties.brokerUrl(), exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void publishControl(Integer deviceId, DeviceState action) {
        publishControl(deviceId, action, true);
    }

    // Dùng trong callback nhận data: không chặn luồng Paho đang xử lý MQTT.
    public void publishControlAsync(Integer deviceId, DeviceState action) {
        publishControl(deviceId, action, false);
    }

    private void publishControl(Integer deviceId, DeviceState action, boolean waitForAck) {
        if (!properties.enabled()) return;
        if (client == null || !client.isConnected()) {
            throw new ApiException(ErrorCode.MQTT_UNAVAILABLE, "MQTT Broker chưa kết nối.");
        }

        try {
            String payload = objectMapper.createObjectNode()
                    .put("deviceId", deviceId)
                    .put("command", action.name())
                    .toString();
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            IMqttDeliveryToken delivery = client.publish(properties.controldeviceTopic(), message);
            if (waitForAck) delivery.waitForCompletion(5000);
            log.info("Đã gửi MQTT control: topic={}, deviceId={}, command={}",
                    properties.controldeviceTopic(), deviceId, action);
        } catch (MqttException exception) {
            throw new ApiException(ErrorCode.MQTT_UNAVAILABLE, "Không thể gửi lệnh tới MQTT Broker.");
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(properties.sensordataTopic(), 1);
            client.subscribe(properties.statustopic(), 1);
            log.info("MQTT connected to {}", serverURI);
        } catch (MqttException exception) {
            log.error("Không thể subscribe MQTT topic", exception);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Mất kết nối MQTT: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
            if (matches(topic, properties.sensordataTopic())) {
                JsonNode json = objectMapper.readTree(payload);
                publishSensorReadings(json);
            } else if (matches(topic, properties.statustopic())) {
                JsonNode json = objectMapper.readTree(payload);
                JsonNode deviceId = json.path("deviceId");
                JsonNode status = json.path("status");
                if (!deviceId.canConvertToInt() || !status.isString()) {
                    throw new IllegalArgumentException("Payload status cần deviceId và status.");
                }
                log.info("Nhận MQTT status: deviceId={}, status={}", deviceId.asInt(), status.stringValue());
                events.publishEvent(new DeviceStatusEvent(
                        deviceId.asInt(),
                        DeviceState.valueOf(status.stringValue().toUpperCase())
                ));
            }
        } catch (Exception exception) {
            log.warn("Bỏ qua MQTT payload không hợp lệ tại {}: {}", topic, exception.getMessage());
        }
    }

    private void publishSensorReadings(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("Payload sensor_data phải là một JSON object.");
        }

        if (json.has("sensorId") || json.has("value")) {
            JsonNode sensorId = json.path("sensorId");
            JsonNode value = json.path("value");
            if (!sensorId.canConvertToInt() || sensorId.asInt() <= 0 || !value.isNumber()) {
                throw new IllegalArgumentException(
                        "Payload sensor_data cần sensorId là số nguyên dương và value là số."
                );
            }
            events.publishEvent(new SensorReadingEvent(sensorId.asInt(), value.decimalValue()));
            return;
        }

        Map<String, String> sensorNames = Map.of(
                "temperature", "Nhiệt độ (°C)",
                "humidity", "Độ ẩm (%)",
                "light", "Độ sáng (lux)"
        );
        var existingSensors = sensors.findAll();
        var readings = new ArrayList<SensorReadingEvent>();
        for (var entry : sensorNames.entrySet()) {
            JsonNode value = json.path(entry.getKey());
            if (!value.isNumber()) {
                throw new IllegalArgumentException(
                        "Payload sensor_data cần trường " + entry.getKey() + " là số."
                );
            }
            if (entry.getKey().equals("light")) {
                value = objectMapper.getNodeFactory().numberNode(
                        BigDecimal.valueOf(4095).subtract(value.decimalValue())
                );
            }
            Sensor sensor = existingSensors.stream()
                    .filter(candidate -> candidate.getName().equals(entry.getValue()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy sensor " + entry.getValue() + " trong database."
                    ));
            readings.add(new SensorReadingEvent(sensor.getId(), value.decimalValue()));
        }
        readings.forEach(events::publishEvent);
    }

    private boolean matches(String topic, String pattern) {
        String regex = pattern.replace("+", "[^/]+").replace("#", ".*");
        return topic.matches(regex);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    void shutdown() {
        if (client == null) return;
        try {
            if (client.isConnected()) client.disconnect().waitForCompletion(2000);
            client.close();
        } catch (MqttException exception) {
            log.debug("Lỗi khi đóng MQTT client", exception);
        }
    }
}
