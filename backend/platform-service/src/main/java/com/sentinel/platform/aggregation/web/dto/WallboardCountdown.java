package com.sentinel.platform.aggregation.web.dto;

import java.time.Instant;

public record WallboardCountdown(
        String label,
        Instant dueAt,
        long remainingSec,
        String severity
) {
}
