package com.sentinel.platform.ruleengine.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sentinel.platform.shared.group.GroupLabelService;
import com.sentinel.platform.ruleengine.web.dto.ItemTimelineView;
import com.sentinel.platform.ruleengine.web.dto.TimelineAlertView;
import com.sentinel.platform.ruleengine.web.dto.TimelineEventView;
import com.sentinel.platform.ruleengine.web.dto.TimelineExpectationView;

@Service
public class ItemTimelineService {
    /**
     * Read model composer for the item timeline API. TODO: replace remaining JdbcTemplate
     * queries with dedicated JPA projections for workflow run, occurrences, expectations, and alerts.
     */
    private final JdbcTemplate jdbcTemplate;
    private final GroupLabelService groupLabelService;

    public ItemTimelineService(JdbcTemplate jdbcTemplate, GroupLabelService groupLabelService) {
        this.jdbcTemplate = jdbcTemplate;
        this.groupLabelService = groupLabelService;
    }

    public ItemTimelineView timeline(String correlationKey, Long workflowVersionId) {
        List<Map<String, Object>> runs = workflowVersionId == null
                ? jdbcTemplate.queryForList("""
                        select wr.*, w.id as workflow_id, w.key as workflow_key, w.name as workflow_name
                        from workflow_run wr
                        join workflow_version v on wr.workflow_version_id = v.id
                        join workflow w on v.workflow_id = w.id
                        where wr.correlation_key = ?
                        order by wr.updated_at desc limit 1
                        """, correlationKey)
                : jdbcTemplate.queryForList("""
                        select wr.*, w.id as workflow_id, w.key as workflow_key, w.name as workflow_name
                        from workflow_run wr
                        join workflow_version v on wr.workflow_version_id = v.id
                        join workflow w on v.workflow_id = w.id
                        where wr.correlation_key = ? and wr.workflow_version_id = ?
                        order by wr.updated_at desc limit 1
                        """, correlationKey, workflowVersionId);
        if (runs.isEmpty()) {
            return null;
        }
        Map<String, Object> run = runs.get(0);
        Long runId = ((Number) run.get("id")).longValue();
        Map<String, Object> group = groupLabelService.parseGroupJson((String) run.get("group_dims"));
        String groupHash = groupLabelService.hashGroup(group);
        String groupLabel = groupLabelService.formatGroupLabel(group);
        String currentStage = run.get("last_node_key") != null ? run.get("last_node_key").toString() : null;
        List<TimelineEventView> events = jdbcTemplate.query("""
                        select node_key, event_time_utc, received_at, is_late, is_duplicate, order_violation, payload_excerpt
                        from event_occurrence where workflow_run_id = ?
                        order by received_at
                        """,
                (rs, rowNum) -> new TimelineEventView(
                        rs.getString("node_key"),
                        getInstant(rs.getTimestamp("event_time_utc")),
                        getInstant(rs.getTimestamp("received_at")),
                        rs.getBoolean("is_late"),
                        rs.getBoolean("order_violation"),
                        rs.getString("payload_excerpt")
                ),
                runId);
        if (currentStage == null && !events.isEmpty()) {
            currentStage = events.get(events.size() - 1).node();
        }
        List<TimelineExpectationView> expectations = jdbcTemplate.query("""
                        select from_node_key, to_node_key, due_at, status, severity
                        from expectation where workflow_run_id = ? and status = 'pending'
                        order by due_at
                        """,
                (rs, rowNum) -> new TimelineExpectationView(
                        rs.getString("from_node_key"),
                        rs.getString("to_node_key"),
                        getInstant(rs.getTimestamp("due_at")),
                        rs.getString("severity"),
                        rs.getString("status")
                ),
                runId);
        List<TimelineAlertView> alerts = jdbcTemplate.query("""
                        select id, node_key, severity, state, correlation_key, first_triggered_at, last_triggered_at, acked_by, acked_at
                        from alert where correlation_key = ? and workflow_version_id = ?
                        order by last_triggered_at desc
                        """,
                (rs, rowNum) -> new TimelineAlertView(
                        String.valueOf(rs.getLong("id")),
                        rs.getString("node_key"),
                        rs.getString("severity"),
                        rs.getString("state"),
                        rs.getString("node_key"),
                        rs.getString("correlation_key"),
                        getInstant(rs.getTimestamp("first_triggered_at")),
                        getInstant(rs.getTimestamp("last_triggered_at")),
                        rs.getString("state")
                ),
                correlationKey, run.get("workflow_version_id"));

        return new ItemTimelineView(
                String.valueOf(run.get("workflow_id")),
                ((Number) run.get("workflow_version_id")).longValue(),
                String.valueOf(run.get("workflow_key")),
                String.valueOf(run.get("workflow_name")),
                correlationKey,
                String.valueOf(run.get("status")),
                currentStage,
                getInstant(run.get("started_at")),
                getInstant(run.get("updated_at")),
                groupHash,
                groupLabel,
                group,
                events,
                expectations,
                alerts
        );
    }

    private Instant getInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date dt) {
            return dt.toInstant();
        }
        return null;
    }
}
