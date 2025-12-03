package com.sentinel.platform.ruleengine.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemController {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ItemController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/items/{correlationKey}")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public Map<String, Object> timeline(@PathVariable String correlationKey,
                                        @RequestParam(value = "workflowVersionId", required = false) Long workflowVersionId) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> runs = workflowVersionId == null
                ? jdbcTemplate.queryForList("select * from workflow_run where correlation_key = ? order by updated_at desc limit 1", correlationKey)
                : jdbcTemplate.queryForList("select * from workflow_run where correlation_key = ? and workflow_version_id = ? order by updated_at desc limit 1", correlationKey, workflowVersionId);
        if (runs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> run = runs.get(0);
        Long runId = ((Number) run.get("id")).longValue();
        response.put("workflowVersionId", run.get("workflow_version_id"));
        response.put("correlationKey", correlationKey);
        response.put("status", run.get("status"));
        response.put("group", parseJson((String) run.get("group_dims")));
        response.put("events", jdbcTemplate.queryForList("select node_key, event_time_utc, received_at, is_late, is_duplicate, order_violation from event_occurrence where workflow_run_id = ? order by received_at", runId));
        response.put("expectations", jdbcTemplate.queryForList("select from_node_key, to_node_key, due_at, status, severity from expectation where workflow_run_id = ? order by due_at", runId));
        response.put("alerts", jdbcTemplate.queryForList("select id, severity, state, dedupe_key, last_triggered_at from alert where correlation_key = ? and workflow_version_id = ?", correlationKey, run.get("workflow_version_id")));
        return response;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
