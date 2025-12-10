package com.sentinel.platform.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Simple value object that expresses a closed-open interval for querying wallboard data.
 */
public record DateRange(Instant start, Instant end, boolean allDays) {

    public static DateRange unbounded() {
        return new DateRange(null, null, true);
    }

    public static DateRange forDay(LocalDate day) {
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(start, end, false);
    }

    public boolean isAllDays() {
        return allDays;
    }
}
