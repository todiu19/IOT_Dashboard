package com.iot.common.filter;

import com.iot.common.exception.ApiException;
import com.iot.common.exception.ErrorCode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.regex.Pattern;

/** Matches the visible timestamp format YYYY-MM-DD HH:mm:ss from left to right. */
public final class TimePrefixFilter {
    private static final Pattern PREFIX = Pattern.compile(
            "^(?:\\d{1,4}|\\d{4}-\\d{0,2}|\\d{4}-\\d{2}-\\d{0,2}|\\d{4}-\\d{2}-\\d{2} \\d{0,2}|\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{0,2}|\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{0,2})$"
    );
    private TimePrefixFilter() {
    }

    public static Predicate matches(String value, Path<LocalDateTime> timestamp, CriteriaBuilder builder) {
        String prefix = value.trim();
        if (!PREFIX.matcher(prefix).matches()) throw invalidTime();
        validate(prefix);

        try {
            String[] parts = prefix.split("[- :]", -1);
            int precision = parts.length - 1;
            while (precision > 0 && parts[precision].isEmpty()) precision--;

            int[] values = {1, 1, 1, 0, 0, 0};
            for (int index = 0; index < precision; index++) values[index] = Integer.parseInt(parts[index]);

            String last = parts[precision];
            int multiplier = (int) Math.pow(10, (precision == 0 ? 4 : 2) - last.length());
            int firstValue = Integer.parseInt(last) * multiplier;
            int lastValue = firstValue + multiplier - 1;
            int minimum = precision < 3 ? 1 : 0;
            int maximum = switch (precision) {
                case 0 -> 9999;
                case 1 -> 12;
                case 2 -> YearMonth.of(values[0], values[1]).lengthOfMonth();
                case 3 -> 23;
                default -> 59;
            };
            if (lastValue < minimum || firstValue > maximum) {
                if (multiplier == 1) throw invalidTime();
                return builder.disjunction();
            }
            firstValue = Math.max(firstValue, minimum);
            lastValue = Math.min(lastValue, maximum);

            values[precision] = firstValue;
            LocalDateTime start = LocalDateTime.of(values[0], values[1], values[2], values[3], values[4], values[5]);
            values[precision] = lastValue;
            LocalDateTime endBase = LocalDateTime.of(values[0], values[1], values[2], values[3], values[4], values[5]);
            LocalDateTime end = switch (precision) {
                case 0 -> endBase.plusYears(1);
                case 1 -> endBase.plusMonths(1);
                case 2 -> endBase.plusDays(1);
                case 3 -> endBase.plusHours(1);
                case 4 -> endBase.plusMinutes(1);
                default -> endBase.plusSeconds(1);
            };
            return builder.and(
                    builder.greaterThanOrEqualTo(timestamp, start),
                    builder.lessThan(timestamp, end)
            );
        } catch (DateTimeException exception) {
            throw invalidTime();
        }
    }

    private static void validate(String prefix) {
        String[] parts = prefix.split("[- :]", -1);
        String year = parts[0];
        String month = parts.length > 1 ? parts[1] : null;
        String day = parts.length > 2 ? parts[2] : null;
        String hour = parts.length > 3 ? parts[3] : null;
        String minute = parts.length > 4 ? parts[4] : null;
        String second = parts.length > 5 ? parts[5] : null;
        if (year.length() == 4 && Integer.parseInt(year) < 1) throw invalidTime();
        if (outside(month, 1, 12) || outside(day, 1, 31) || outside(hour, 0, 23)
                || outside(minute, 0, 59) || outside(second, 0, 59)) throw invalidTime();
        if (year.length() == 4 && month != null && month.length() == 2
                && day != null && day.length() == 2) {
            try {
                LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            } catch (DateTimeException exception) {
                throw invalidTime();
            }
        }
    }

    private static boolean outside(String value, int min, int max) {
        return value != null && value.length() == 2
                && (Integer.parseInt(value) < min || Integer.parseInt(value) > max);
    }

    private static ApiException invalidTime() {
        return new ApiException(ErrorCode.INVALID_REQUEST, "Thời gian phải theo thứ tự YYYY-MM-DD HH:mm:ss.");
    }
}
