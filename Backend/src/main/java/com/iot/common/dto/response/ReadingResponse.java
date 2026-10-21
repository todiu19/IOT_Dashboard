package com.iot.common.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReadingResponse(
        Integer id,
        Integer sensorId,
        String sensorName,
        BigDecimal value,
        LocalDateTime recordedAt
) {
}
