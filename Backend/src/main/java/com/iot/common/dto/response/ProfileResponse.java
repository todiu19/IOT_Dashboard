package com.iot.common.dto.response;

public record ProfileResponse(
        Integer id,
        String fullname,
        String username,
        String msv,
        String email,
        String github,
        String postman,
        String figma,
        String pdf
) {
}
