package com.iot.common.dto.response;

import com.iot.common.enums.ActionResult;
import com.iot.common.enums.DeviceState;
import java.time.LocalDateTime;

public record ControlResponse(
        Integer historyId,
        Integer deviceId,
        String deviceName,
        DeviceState action,
        ActionResult status,
        LocalDateTime performedAt
) {
}
