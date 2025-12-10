package com.sentinel.platform.aggregation.web.dto;

import java.time.Instant;
import java.util.List;

public record WallboardView(List<WallboardWorkflowTile> workflows, Instant updatedAt) {
    public static WallboardView empty() {
        return new WallboardView(List.of(), Instant.now());
    }
}
