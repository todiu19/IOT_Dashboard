package com.iot.history.controller;

import com.iot.common.enums.DeviceState;
import com.iot.common.enums.ActionResult;
import com.iot.common.dto.ApiResponse;
import com.iot.common.dto.response.ActionHistoryResponse;
import com.iot.common.dto.response.PageResponse;
import com.iot.config.OpenApiConfig;
import com.iot.history.service.ActionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/action-history")
@Tag(name = "Action History")
@SecurityRequirement(name = OpenApiConfig.JWT_COOKIE_SCHEME)
public class ActionHistoryController {

    private final ActionHistoryService histories;

    public ActionHistoryController(ActionHistoryService histories) {
        this.histories = histories;
    }

    @GetMapping
    @Operation(summary = "Tìm lịch sử điều khiển", description = "Lọc lịch sử theo thiết bị, hành động, trạng thái, thời gian và phân trang.")
    public ApiResponse<PageResponse<ActionHistoryResponse>> search(
            @Parameter(description = "ID thiết bị") @RequestParam(required = false) Integer deviceId,
            @Parameter(description = "Hành động ON hoặc OFF") @RequestParam(required = false) DeviceState action,
            @Parameter(description = "Trạng thái LOADING, ON, OFF hoặc FAILED") @RequestParam(required = false) ActionResult status,
            @Parameter(description = "Tiền tố thời gian từ YYYY đến YYYY-MM-DD HH:mm:ss") @RequestParam(required = false) String time,
            @Parameter(description = "Trang bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang, tối đa 100") @RequestParam(defaultValue = "7") int size
    ) {
        ApiResponse<PageResponse<ActionHistoryResponse>> result = new ApiResponse<>();
        result.setData(histories.search(deviceId, action, status, time, page, size));
        return result;
    }
}
