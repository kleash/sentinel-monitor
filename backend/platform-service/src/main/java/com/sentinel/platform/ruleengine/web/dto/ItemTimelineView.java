package com.sentinel.platform.ruleengine.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ItemTimelineView(
        String workflowId,
        Long workflowVersionId,
        String workflowKey,
        String workflowName,
        String correlationKey,
        String status,
        String currentStage,
        Instant startedAt,
        Instant updatedAt,
        String groupHash,
        String groupLabel,
        Map<String, Object> group,
        List<TimelineEventView> events,
        List<TimelineExpectationView> pendingExpectations,
        List<TimelineAlertView> alerts
) {
}
