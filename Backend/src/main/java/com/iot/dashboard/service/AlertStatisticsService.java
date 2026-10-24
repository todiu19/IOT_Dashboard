package com.iot.dashboard.service;

import com.iot.common.dto.response.AlertStatisticsResponse;
import com.iot.common.enums.DeviceState;
import com.iot.common.exception.ApiException;
import com.iot.common.exception.ErrorCode;
import com.iot.domain.repository.AlertHistoryRepository;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertStatisticsService {
    private final AlertHistoryRepository histories;

    public AlertStatisticsService(AlertHistoryRepository histories) {
        this.histories = histories;
    }

    @Transactional(readOnly = true)
    public AlertStatisticsResponse getMonth(String value) {
        YearMonth month;
        try {
            if (value == null || !value.matches("[0-9]{4}-[0-9]{2}")) throw new DateTimeException("month");
            month = YearMonth.parse(value);
            if (month.getYear() < 1) throw new DateTimeException("year");
        } catch (DateTimeException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Tháng phải có dạng YYYY-MM, ví dụ 2026-10.");
        }
        long[] on = new long[month.lengthOfMonth() + 1];
        long[] off = new long[month.lengthOfMonth() + 1];
        for (var count : histories.countByDay(month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay())) {
            if (count.getState() == DeviceState.ON) on[count.getDay()] = count.getTotal();
            else off[count.getDay()] = count.getTotal();
        }
        var days = new ArrayList<AlertStatisticsResponse.Day>();
        long totalOn = 0, totalOff = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            days.add(new AlertStatisticsResponse.Day(month.atDay(day), on[day], off[day]));
            totalOn += on[day];
            totalOff += off[day];
        }
        return new AlertStatisticsResponse(month.toString(), totalOn, totalOff, days);
    }
}
