package com.iot.common.dto.response;

import java.time.LocalDate;
import java.util.List;

public record AlertStatisticsResponse(String month, long totalOn, long totalOff, List<Day> days) {
    public record Day(LocalDate date, long onCount, long offCount) {}
}
