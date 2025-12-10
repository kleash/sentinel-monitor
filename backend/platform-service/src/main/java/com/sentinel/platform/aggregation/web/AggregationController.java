package com.sentinel.platform.aggregation.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.aggregation.model.StageAggregate;
import com.sentinel.platform.aggregation.service.AggregationQueryService;
import com.sentinel.platform.aggregation.service.WallboardViewService;
import com.sentinel.platform.aggregation.web.dto.StageAggregateView;
import com.sentinel.platform.aggregation.web.dto.WallboardView;

@RestController
public class AggregationController {

    private final AggregationQueryService aggregationQueryService;
    private final WallboardViewService wallboardViewService;

    public AggregationController(AggregationQueryService aggregationQueryService,
                                 WallboardViewService wallboardViewService) {
        this.aggregationQueryService = aggregationQueryService;
        this.wallboardViewService = wallboardViewService;
    }

    @GetMapping("/workflows/{id}/aggregates")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<StageAggregateView> aggregates(@PathVariable Long id,
                                           @RequestParam(value = "limit", defaultValue = "50") int limit,
                                           @RequestParam(value = "groupHash", required = false) String groupHash) {
        return aggregationQueryService.findAggregates(id, limit, groupHash)
                .stream()
                .map(StageAggregateView::from)
                .toList();
    }

    @GetMapping("/wallboard")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public WallboardView wallboard(@RequestParam(value = "limit", defaultValue = "200") int limit) {
        return wallboardViewService.buildWallboard(limit);
    }
}
