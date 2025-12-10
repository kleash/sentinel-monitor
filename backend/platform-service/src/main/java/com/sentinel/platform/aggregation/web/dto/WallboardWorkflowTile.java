package com.sentinel.platform.aggregation.web.dto;

import java.util.List;

public record WallboardWorkflowTile(
        String workflowId,
        String workflowKey,
        String name,
        String status,
        List<WallboardGroupTile> groups
) {
}
