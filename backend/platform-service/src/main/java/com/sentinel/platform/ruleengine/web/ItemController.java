package com.sentinel.platform.ruleengine.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.ruleengine.service.ItemTimelineService;
import com.sentinel.platform.ruleengine.web.dto.ItemTimelineView;

@RestController
public class ItemController {
    private final ItemTimelineService itemTimelineService;

    public ItemController(ItemTimelineService itemTimelineService) {
        this.itemTimelineService = itemTimelineService;
    }

    @GetMapping("/items/{correlationKey}")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public ResponseEntity<ItemTimelineView> timeline(@PathVariable String correlationKey,
                                                     @RequestParam(value = "workflowVersionId", required = false) Long workflowVersionId) {
        ItemTimelineView timeline = itemTimelineService.timeline(correlationKey, workflowVersionId);
        if (timeline == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(timeline);
    }
}
