package com.sentinel.platform.ruleengine.web.dto;

import java.time.Instant;

public record TimelineAlertView(
        String id,
        String nodeKey,
        String severity,
        String state,
        String title,
        String correlationKey,
        Instant triggeredAt,
        Instant lastTriggeredAt,
        String reason
) {
}
