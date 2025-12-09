package com.sentinel.platform.ruleengine.web;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.ruleengine.service.ItemTimelineService;

@RestController
public class ItemController {
    private final ItemTimelineService itemTimelineService;

    public ItemController(ItemTimelineService itemTimelineService) {
        this.itemTimelineService = itemTimelineService;
    }

    @GetMapping("/items/{correlationKey}")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public Map<String, Object> timeline(@PathVariable String correlationKey,
                                        @RequestParam(value = "workflowVersionId", required = false) Long workflowVersionId) {
        return itemTimelineService.timeline(correlationKey, workflowVersionId);
    }
}
