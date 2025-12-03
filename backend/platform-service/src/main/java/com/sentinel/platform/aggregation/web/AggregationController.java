package com.sentinel.platform.aggregation.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AggregationController {

    private final JdbcTemplate jdbcTemplate;

    public AggregationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/workflows/{id}/aggregates")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<Map<String, Object>> aggregates(@PathVariable Long id,
                                                @RequestParam(value = "limit", defaultValue = "50") int limit,
                                                @RequestParam(value = "groupHash", required = false) String groupHash) {
        if (groupHash != null) {
            return jdbcTemplate.queryForList("""
                    select * from stage_aggregate where workflow_version_id = ? and group_dim_hash = ? order by bucket_start desc limit ?
                    """, id, groupHash, limit);
        }
        return jdbcTemplate.queryForList("select * from stage_aggregate where workflow_version_id = ? order by bucket_start desc limit ?", id, limit);
    }

    @GetMapping("/wallboard")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<Map<String, Object>> wallboard(@RequestParam(value = "limit", defaultValue = "200") int limit) {
        return jdbcTemplate.queryForList("""
                select * from stage_aggregate order by bucket_start desc limit ?
                """, limit);
    }
}
