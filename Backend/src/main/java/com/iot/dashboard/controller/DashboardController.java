package com.iot.dashboard.controller;

import com.iot.common.dto.response.DashboardResponse;
import com.iot.common.dto.ApiResponse;
import com.iot.config.OpenApiConfig;
import com.iot.dashboard.service.DashboardService;
import com.iot.dashboard.service.AlertStatisticsService;
import com.iot.common.dto.response.AlertStatisticsResponse;
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
    private final AlertStatisticsService alerts;

    public DashboardController(DashboardService dashboard, AlertStatisticsService alerts) {
        this.dashboard = dashboard;
        this.alerts = alerts;
    }

    @GetMapping
    @Operation(summary = "Lấy dữ liệu Dashboard", description = "Trả về reading mới nhất, dữ liệu biểu đồ, thiết bị và các lệnh đang chờ.")
    public ApiResponse<DashboardResponse> getDashboard() {
        ApiResponse<DashboardResponse> result = new ApiResponse<>();
        result.setData(dashboard.getDashboard());
        return result;
    }
    @GetMapping("/alert-statistics")
    @Operation(summary = "Thống kê bật/tắt cảnh báo theo ngày trong tháng", description = "Đếm chuyển trạng thái LED 4 đã được ESP32 xác nhận; month có dạng YYYY-MM.")
    public ApiResponse<AlertStatisticsResponse> getAlertStatistics(@RequestParam(required = false) String month) {
        ApiResponse<AlertStatisticsResponse> result = new ApiResponse<>();
        result.setData(alerts.getMonth(month));
        return result;
    }
}

