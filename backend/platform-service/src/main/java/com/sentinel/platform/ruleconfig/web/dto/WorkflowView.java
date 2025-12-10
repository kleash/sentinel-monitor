package com.sentinel.platform.ruleconfig.web.dto;

import java.util.List;
import java.util.Map;

public record WorkflowView(
        String id,
        String key,
        String name,
        String status,
        String activeVersion,
        Map<String, Object> graph,
        List<String> groupDimensions
) {
}
