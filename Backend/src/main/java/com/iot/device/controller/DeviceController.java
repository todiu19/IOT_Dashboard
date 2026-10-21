package com.iot.device.controller;

import com.iot.common.dto.request.ControlRequest;
import com.iot.common.dto.ApiResponse;
import com.iot.common.dto.response.ControlResponse;
import com.iot.config.OpenApiConfig;
import com.iot.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api")
@Tag(name = "Devices")
@SecurityRequirement(name = OpenApiConfig.JWT_COOKIE_SCHEME)
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @PostMapping("/device/{id}/control")
    @Operation(summary = "Điều khiển thiết bị", description = "Gửi lệnh ON hoặc OFF tới thiết bị qua MQTT.")
    public ApiResponse<ControlResponse> control(
            @Parameter(description = "ID thiết bị", example = "1") @PathVariable Integer id,
            @Valid @RequestBody ControlRequest request,
            Principal principal
    ) {
        ApiResponse<ControlResponse> result = new ApiResponse<>();
        result.setData(devices.control(id, request.action(), principal.getName()));
        return result;
    }
}
