package com.sentinel.platform.ruleconfig.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.ruleconfig.service.WorkflowService;
import com.sentinel.platform.ruleconfig.web.dto.WorkflowRequest;
import com.sentinel.platform.ruleconfig.web.dto.WorkflowView;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    @PreAuthorize("hasRole('viewer') or hasRole('config-admin')")
    public List<WorkflowView> list() {
        return workflowService.listViews();
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('viewer') or hasRole('config-admin')")
    public ResponseEntity<WorkflowView> get(@PathVariable String key) {
        return workflowService.findViewByKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('config-admin')")
    public ResponseEntity<WorkflowView> create(@Valid @RequestBody WorkflowRequest request) {
        var created = workflowService.createWorkflow(request);
        return ResponseEntity.ok(workflowService.toView(created));
    }
}
