package com.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("app.mqtt")
public record MqttProperties(
        boolean enabled,
        String brokerUrl,
        String clientId,
        String username,
        String password,
        String sensordataTopic,
        String controldeviceTopic,
        String statustopic,
        Duration controlTimeout
) {
    public MqttProperties {
        if (controlTimeout == null) controlTimeout = Duration.ofSeconds(30);
        if (controlTimeout.isZero() || controlTimeout.isNegative()) {
            throw new IllegalArgumentException("control-timeout phải lớn hơn 0.");
        }
    }
}
