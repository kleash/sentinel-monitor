package com.sentinel.platform.aggregation.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.aggregation.service.AggregationQueryService;
import com.sentinel.platform.aggregation.service.WallboardViewService;
import com.sentinel.platform.aggregation.web.dto.StageAggregateView;
import com.sentinel.platform.aggregation.web.dto.WallboardView;
import com.sentinel.platform.shared.time.DateRange;
import com.sentinel.platform.shared.time.DateRangeParser;

@RestController
public class AggregationController {

    private final AggregationQueryService aggregationQueryService;
    private final WallboardViewService wallboardViewService;
    private final DateRangeParser dateRangeParser;

    public AggregationController(AggregationQueryService aggregationQueryService,
                                 WallboardViewService wallboardViewService,
                                 DateRangeParser dateRangeParser) {
        this.aggregationQueryService = aggregationQueryService;
        this.wallboardViewService = wallboardViewService;
        this.dateRangeParser = dateRangeParser;
    }

    @GetMapping("/workflows/{id}/aggregates")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<StageAggregateView> aggregates(@PathVariable Long id,
                                           @RequestParam(value = "limit", defaultValue = "50") int limit,
                                           @RequestParam(value = "groupHash", required = false) String groupHash,
                                           @RequestParam(value = "date", required = false) String date,
                                           @RequestParam(value = "allDays", defaultValue = "false") boolean allDays) {
        DateRange range = dateRangeParser.resolve(date, allDays);
        return aggregationQueryService.findAggregates(id, limit, groupHash, range)
                .stream()
                .map(StageAggregateView::from)
                .toList();
    }

    @GetMapping("/wallboard")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public WallboardView wallboard(@RequestParam(value = "limit", defaultValue = "200") int limit,
                                   @RequestParam(value = "date", required = false) String date,
                                   @RequestParam(value = "allDays", defaultValue = "false") boolean allDays) {
        DateRange range = dateRangeParser.resolve(date, allDays);
        return wallboardViewService.buildWallboard(limit, range);
    }
}
