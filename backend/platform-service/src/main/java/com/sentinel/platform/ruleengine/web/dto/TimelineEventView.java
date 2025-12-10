package com.sentinel.platform.ruleengine.web.dto;

import java.time.Instant;

public record TimelineEventView(
        String node,
        Instant eventTime,
        Instant receivedAt,
        boolean late,
        boolean orderViolation,
        String payloadExcerpt
) {
}
