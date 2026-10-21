package com.iot.common.dto.response;

import com.iot.common.enums.*;
import java.time.LocalDateTime;

public record ActionHistoryResponse(
        Integer id,
        Integer deviceId,
        String deviceName,
        Integer userId,
        String username,
        DeviceState action,
        ActionResult status,
        LocalDateTime performedAt
) {
}
