package com.iot.common.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ReadingRequest(
        @NotNull @Positive Integer sensorId,
        @NotNull BigDecimal value
) {
}
