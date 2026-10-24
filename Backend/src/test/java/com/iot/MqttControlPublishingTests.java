package com.iot;

import com.iot.common.enums.DeviceState;
import com.iot.config.MqttProperties;
import com.iot.domain.repository.SensorRepository;
import com.iot.mqtt.MqttClientService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MqttControlPublishingTests {
    private final MqttAsyncClient client = mock(MqttAsyncClient.class);
    private final IMqttDeliveryToken delivery = mock(IMqttDeliveryToken.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttClientService mqtt;

    @BeforeEach
    void setUp() throws Exception {
        var properties = new MqttProperties(true, "tcp://localhost:1883", "test", "", "",
                "sensor_data", "control_device", "status", Duration.ofSeconds(30));
        mqtt = new MqttClientService(properties, mapper,
                mock(ApplicationEventPublisher.class), mock(SensorRepository.class));
        ReflectionTestUtils.setField(mqtt, "client", client);
        when(client.isConnected()).thenReturn(true);
        when(client.publish(eq("control_device"), any(MqttMessage.class))).thenReturn(delivery);
    }

    @Test
    void callbackPublishUsesCorrectPayloadWithoutWaitingForAck() throws Exception {
        mqtt.publishControlAsync(4, DeviceState.ON);

        var message = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq("control_device"), message.capture());
        var payload = mapper.readTree(new String(message.getValue().getPayload(), StandardCharsets.UTF_8));
        assertEquals(4, payload.path("deviceId").asInt());
        assertEquals("ON", payload.path("command").stringValue());
        assertEquals(1, message.getValue().getQos());
        verifyNoInteractions(delivery);
    }

    @Test
    void manualControlStillWaitsForBrokerAck() throws Exception {
        mqtt.publishControl(1, DeviceState.OFF);
        verify(delivery).waitForCompletion(5000);
    }
}
