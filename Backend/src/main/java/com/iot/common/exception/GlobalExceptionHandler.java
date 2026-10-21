package com.iot.common.exception;

import com.iot.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApi(ApiException exception) {
        return response(exception.getErrorCode(), exception.getMessage(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException exception) {
        return response(ErrorCode.INVALID_CREDENTIALS, null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(ErrorCode.VALIDATION_ERROR, null, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        return response(ErrorCode.INVALID_REQUEST, null, null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiResponse<Void>> handleNotFound(Exception exception) {
        return response(ErrorCode.ENDPOINT_NOT_FOUND, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, null, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Lỗi khi xử lý API", exception);
        return response(ErrorCode.INTERNAL_ERROR, null, null);
    }

    private <T> ResponseEntity<ApiResponse<T>> response(ErrorCode errorCode, String message, T data) {
        ApiResponse<T> result = new ApiResponse<>();
        result.setCode(errorCode.getHttpStatus().value());
        result.setErrorCode(errorCode.getCode());
        result.setMessage(message != null ? message : errorCode.getDefaultMessage());
        result.setData(data);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(result);
    }
}
