package com.sentinel.platform.aggregation.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.aggregation.model.StageAggregate;
import com.sentinel.platform.aggregation.service.AggregationQueryService;

@RestController
public class AggregationController {

    private final AggregationQueryService aggregationQueryService;

    public AggregationController(AggregationQueryService aggregationQueryService) {
        this.aggregationQueryService = aggregationQueryService;
    }

    @GetMapping("/workflows/{id}/aggregates")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<StageAggregate> aggregates(@PathVariable Long id,
                                           @RequestParam(value = "limit", defaultValue = "50") int limit,
                                           @RequestParam(value = "groupHash", required = false) String groupHash) {
        return aggregationQueryService.findAggregates(id, limit, groupHash);
    }

    @GetMapping("/wallboard")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<StageAggregate> wallboard(@RequestParam(value = "limit", defaultValue = "200") int limit) {
        return aggregationQueryService.wallboard(limit);
    }
}
