package com.iot.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu gửi lên không hợp lệ."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Chưa đăng nhập."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền truy cập."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "Không tìm thấy API."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng."),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Không tìm thấy thiết bị."),
    DEVICE_BUSY(HttpStatus.CONFLICT, "DEVICE_BUSY", "Thiết bị đang chờ phản hồi lệnh trước."),
    SENSOR_NOT_FOUND(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND", "Không tìm thấy cảm biến."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ."),
    MQTT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MQTT_UNAVAILABLE", "MQTT Broker chưa kết nối."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Đã xảy ra lỗi trong hệ thống.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
