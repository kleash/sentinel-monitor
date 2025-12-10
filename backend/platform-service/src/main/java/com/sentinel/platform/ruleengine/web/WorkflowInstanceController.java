package com.sentinel.platform.ruleengine.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.ruleengine.service.WorkflowInstanceQueryService;
import com.sentinel.platform.ruleengine.web.dto.WorkflowInstancePage;
import com.sentinel.platform.shared.time.DateRange;
import com.sentinel.platform.shared.time.DateRangeParser;

@RestController
@RequestMapping("/workflows/{key}/correlations")
public class WorkflowInstanceController {

    private final WorkflowInstanceQueryService workflowInstanceQueryService;
    private final DateRangeParser dateRangeParser;

    public WorkflowInstanceController(WorkflowInstanceQueryService workflowInstanceQueryService,
                                      DateRangeParser dateRangeParser) {
        this.workflowInstanceQueryService = workflowInstanceQueryService;
        this.dateRangeParser = dateRangeParser;
    }

    @GetMapping
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public WorkflowInstancePage correlations(@PathVariable String key,
                                             @RequestParam(value = "groupHash", required = false) String groupHash,
                                             @RequestParam(value = "stage", required = false) String stage,
                                             @RequestParam(value = "page", defaultValue = "0") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size,
                                             @RequestParam(value = "date", required = false) String date,
                                             @RequestParam(value = "allDays", defaultValue = "false") boolean allDays) {
        DateRange range = dateRangeParser.resolve(date, allDays);
        return workflowInstanceQueryService.findInstances(key, groupHash, stage, range, page, size);
    }
}
