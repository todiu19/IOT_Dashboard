package com.iot.profile.controller;

import com.iot.common.dto.ApiResponse;
import com.iot.common.dto.response.ProfileResponse;
import com.iot.config.OpenApiConfig;
import com.iot.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile")
@SecurityRequirement(name = OpenApiConfig.JWT_COOKIE_SCHEME)
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "Lấy hồ sơ", description = "Trả về hồ sơ của người dùng đang đăng nhập.")
    public ApiResponse<ProfileResponse> get(Principal principal) {
        ApiResponse<ProfileResponse> result = new ApiResponse<>();
        result.setData(profiles.get(principal.getName()));
        return result;
    }

}
