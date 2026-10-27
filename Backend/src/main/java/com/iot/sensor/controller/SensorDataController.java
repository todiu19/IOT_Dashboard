package com.iot.sensor.controller;

import com.iot.common.dto.ApiResponse;
import com.iot.common.dto.response.PageResponse;
import com.iot.common.dto.response.ReadingResponse;
import com.iot.config.OpenApiConfig;
import com.iot.sensor.service.SensorDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-sensor")
@Tag(name = "Sensors")
@SecurityRequirement(name = OpenApiConfig.JWT_COOKIE_SCHEME)
public class SensorDataController {

    private final SensorDataService service;

    public SensorDataController(SensorDataService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Tìm dữ liệu cảm biến", description = "Tìm kiếm theo từ khóa và chế độ lọc ('all', 'time', hoặc tên cảm biến), phân trang các reading.")
    public ApiResponse<PageResponse<ReadingResponse>> search(
            @Parameter(description = "Từ khóa tìm kiếm hoặc thời gian YYYY-MM-DD HH:mm:ss") @RequestParam(required = false) String search,
            @Parameter(description = "Chế độ lọc: 'time', tên cảm biến (ví dụ 'Nhiệt độ (°C)'), hoặc để trống / 'all'") @RequestParam(required = false) String mode,
            @Parameter(hidden = true) @RequestParam(required = false) String sensor,
            @Parameter(hidden = true) @RequestParam(required = false) String time,
            @Parameter(description = "Trang bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang, tối đa 100") @RequestParam(defaultValue = "7") int size
    ) {
        if (mode != null && !mode.isBlank()) {
            if ("time".equalsIgnoreCase(mode.trim())) {
                time = search;
                search = null;
            } else if (!"all".equalsIgnoreCase(mode.trim())) {
                sensor = mode.trim();
            }
        }
        ApiResponse<PageResponse<ReadingResponse>> result = new ApiResponse<>();
        result.setData(service.search(search, sensor, time, page, size));
        return result;
    }
}
