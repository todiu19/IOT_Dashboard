package com.iot.common.dto.response;

public record UserResponse(
        Integer id,
        String username,
        String fullname,
        String email
) {
}
