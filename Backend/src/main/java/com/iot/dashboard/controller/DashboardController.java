package com.iot.dashboard.controller;

import com.iot.common.dto.response.DashboardResponse;
import com.iot.common.dto.ApiResponse;
import com.iot.config.OpenApiConfig;
import com.iot.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
@SecurityRequirement(name = OpenApiConfig.JWT_COOKIE_SCHEME)
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    @Operation(summary = "Lấy dữ liệu Dashboard", description = "Trả về reading mới nhất, dữ liệu biểu đồ, thiết bị và các lệnh đang chờ.")
    public ApiResponse<DashboardResponse> getDashboard() {
        ApiResponse<DashboardResponse> result = new ApiResponse<>();
        result.setData(dashboard.getDashboard());
        return result;
    }
    
}

