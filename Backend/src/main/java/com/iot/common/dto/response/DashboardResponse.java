package com.iot.common.dto.response;

import java.util.List;

public record DashboardResponse(
        List<ReadingResponse> latestReadings,
        List<ReadingResponse> chartData,
        List<DeviceResponse> devices,
        List<ControlResponse> pendingControls
) {
}
