package com.sentinel.platform.ruleengine.web.dto;

import java.util.List;

public record WorkflowInstancePage(
        List<WorkflowInstanceView> items,
        int page,
        int size,
        boolean hasMore
) {
    public static WorkflowInstancePage empty() {
        return new WorkflowInstancePage(List.of(), 0, 0, false);
    }
}
