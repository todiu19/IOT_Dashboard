package com.iot.mqtt;

import java.math.BigDecimal;

public record SensorReadingEvent(Integer sensorId, BigDecimal value) {
}

