package com.sentinel.platform.aggregation.web.dto;

import java.util.List;

public record WallboardGroupTile(
        String label,
        String groupHash,
        String status,
        int inFlight,
        int late,
        int failed,
        List<WallboardCountdown> countdowns
) {
}
