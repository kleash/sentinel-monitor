package com.sentinel.platform.ruleengine.web.dto;

import java.time.Instant;

public record WorkflowInstanceView(
        String correlationId,
        Long workflowVersionId,
        String workflowId,
        String workflowKey,
        String workflowName,
        String status,
        String currentStage,
        Instant startedAt,
        Instant updatedAt,
        Instant lastEventAt,
        String groupHash,
        String groupLabel,
        boolean late,
        boolean orderViolation
) {
}
