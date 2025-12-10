package com.sentinel.platform.shared.time;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

@Service
public class DateRangeParser {

    public DateRange resolve(String date, boolean allDays) {
        if (allDays || (date != null && "all".equalsIgnoreCase(date.trim()))) {
            return DateRange.unbounded();
        }
        LocalDate day;
        try {
            if (date == null || date.isBlank() || "today".equalsIgnoreCase(date.trim())) {
                day = LocalDate.now(ZoneOffset.UTC);
            } else {
                day = LocalDate.parse(date.trim());
            }
        } catch (Exception ex) {
            day = LocalDate.now(ZoneOffset.UTC);
        }
        return DateRange.forDay(day);
    }
}
