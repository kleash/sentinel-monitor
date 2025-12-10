package com.sentinel.platform.ruleengine.web.dto;

import java.time.Instant;

public record TimelineExpectationView(
        String from,
        String to,
        Instant dueAt,
        String severity,
        String status
) {
}
