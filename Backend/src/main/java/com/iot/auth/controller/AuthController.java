package com.iot.auth.controller;

import com.iot.common.dto.request.LoginRequest;
import com.iot.common.dto.ApiResponse;
import com.iot.common.dto.response.UserResponse;
import com.iot.common.exception.ErrorCode;
import com.iot.common.exception.ApiException;
import com.iot.domain.repository.UserRepository;
import com.iot.auth.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Xác thực tài khoản và đặt JWT vào cookie HttpOnly tên jwt.")
    public ApiResponse<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        String token = jwtService.generateToken(authentication.getName());
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtService.getExpirationMs()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        ApiResponse<UserResponse> result = new ApiResponse<>();
        result.setData(currentUser(authentication));
        return result;
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", description = "Xóa cookie JWT hiện tại.")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new ApiResponse<>();
    }

    private UserResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return users.findByUsername(authentication.getName())
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getFullname(),
                        user.getEmail()
                ))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Phiên đăng nhập không hợp lệ."));
    }
}
