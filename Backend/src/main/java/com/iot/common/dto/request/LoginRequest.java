package com.iot.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "Tên đăng nhập", example = "admin") @NotBlank String username,
        @Schema(description = "Mật khẩu", example = "admin123") @NotBlank String password
) {
}
