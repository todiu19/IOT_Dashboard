package com.iot.common.dto.request;

import com.iot.common.enums.DeviceState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ControlRequest(
        @Schema(description = "Lệnh điều khiển", allowableValues = {"ON", "OFF"}, example = "ON")
        @NotNull DeviceState action
) {
}
