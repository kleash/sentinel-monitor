package com.sentinel.platform.ruleengine.repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RuleEngineStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public RuleEngineStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long findRunId(Long workflowVersionId, String correlationKey) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id from workflow_run where workflow_version_id = ? and correlation_key = ?",
                    Long.class, workflowVersionId, correlationKey);
        } catch (Exception ex) {
            return null;
        }
    }

    public Long createRun(Long workflowVersionId, String correlationKey, String status, Instant startedAt, String groupJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into workflow_run (workflow_version_id, correlation_key, group_dims, status, started_at, updated_at) values (?,?,?,?,?,?)",
                    new String[]{"id"});
            ps.setLong(1, workflowVersionId);
            ps.setString(2, correlationKey);
            ps.setString(3, groupJson);
            ps.setString(4, status);
            ps.setTimestamp(5, Timestamp.from(startedAt.atZone(ZoneOffset.UTC).toInstant()));
            ps.setTimestamp(6, Timestamp.from(startedAt.atZone(ZoneOffset.UTC).toInstant()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateRun(Long runId, String status, Instant updatedAt, String lastNodeKey) {
        jdbcTemplate.update("update workflow_run set status = ?, updated_at = ?, last_node_key = ? where id = ?",
                status,
                Timestamp.from(updatedAt.atZone(ZoneOffset.UTC).toInstant()),
                lastNodeKey,
                runId);
    }

    public void saveOccurrence(Long runId,
                               String nodeKey,
                               String eventId,
                               Instant eventTimeUtc,
                               Instant receivedAt,
                               String payloadExcerpt,
                               boolean isLate,
                               boolean isDuplicate,
                               boolean orderViolation,
                               Long rawEventId) {
        jdbcTemplate.update(
                "insert into event_occurrence (workflow_run_id, node_key, event_id, event_time_utc, received_at, payload_excerpt, is_late, is_duplicate, order_violation, raw_event_id) values (?,?,?,?,?,?,?,?,?,?)",
                runId,
                nodeKey,
                eventId,
                Timestamp.from(eventTimeUtc.atZone(ZoneOffset.UTC).toInstant()),
                Timestamp.from(receivedAt.atZone(ZoneOffset.UTC).toInstant()),
                payloadExcerpt,
                isLate,
                isDuplicate,
                orderViolation,
                rawEventId
        );
    }

    public java.util.List<OutgoingEdge> fetchOutgoingEdges(Long workflowVersionId, String fromNodeKey) {
        return jdbcTemplate.query("""
                select wn_to.node_key as to_node_key, we.max_latency_sec, we.severity, we.absolute_deadline, we.optional, we.expected_count
                from workflow_node wn_from
                join workflow_edge we on we.from_node_id = wn_from.id
                join workflow_node wn_to on we.to_node_id = wn_to.id
                where wn_from.workflow_version_id = ? and wn_from.node_key = ?
                """, (rs, rowNum) -> new OutgoingEdge(
                rs.getString("to_node_key"),
                rs.getInt("max_latency_sec"),
                rs.getString("severity"),
                rs.getString("absolute_deadline"),
                rs.getBoolean("optional"),
                (Integer) rs.getObject("expected_count")
        ), workflowVersionId, fromNodeKey);
    }

    public List<ExpectationRecord> clearExpectations(Long runId, String toNodeKey, Instant receivedAtUtc) {
        var due = jdbcTemplate.query("""
                select id, due_at, severity, status
                from expectation
                where workflow_run_id = ? and to_node_key = ? and status in ('pending','fired')
                """, (rs, rowNum) -> new ExpectationRecord(
                rs.getLong("id"),
                rs.getTimestamp("due_at").toInstant(),
                rs.getString("severity"),
                rs.getString("status")
        ), runId, toNodeKey);
        jdbcTemplate.update("""
                update expectation
                set status = 'cleared', lock_owner = null
                where workflow_run_id = ? and to_node_key = ? and status in ('pending','fired')
                """, runId, toNodeKey);
        return due;
    }

    public void createExpectation(Long runId, String fromNodeKey, String toNodeKey, Instant dueAt, String severity) {
        jdbcTemplate.update("insert into expectation (workflow_run_id, from_node_key, to_node_key, due_at, status, severity, created_at) values (?,?,?,?,?,?,?)",
                runId,
                fromNodeKey,
                toNodeKey,
                Timestamp.from(dueAt.atZone(ZoneOffset.UTC).toInstant()),
                "pending",
                severity,
                Timestamp.from(Instant.now().atZone(ZoneOffset.UTC).toInstant()));
    }

    public Optional<NodeDescriptor> findNodeForEvent(Long workflowVersionId, String eventType) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select node_key, is_start, is_terminal
                    from workflow_node
                    where workflow_version_id = ? and event_type = ?
                    """, (rs, rowNum) -> new NodeDescriptor(
                    rs.getString("node_key"),
                    rs.getBoolean("is_start"),
                    rs.getBoolean("is_terminal")
            ), workflowVersionId, eventType));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean hasSeenEvent(Long runId, String eventId) {
        if (eventId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from event_occurrence where workflow_run_id = ? and event_id = ?",
                Integer.class, runId, eventId);
        return count != null && count > 0;
    }

    public RunContext loadRunContext(long runId) {
        return jdbcTemplate.queryForObject("""
                select workflow_version_id, correlation_key, group_dims
                from workflow_run
                where id = ?
                """, (rs, rowNum) -> new RunContext(
                rs.getLong("workflow_version_id"),
                rs.getString("correlation_key"),
                rs.getString("group_dims")
        ), runId);
    }

    public record OutgoingEdge(String toNodeKey, Integer maxLatencySec, String severity, String absoluteDeadline, boolean optional, Integer expectedCount) {}

    public record ExpectationRecord(long id, Instant dueAt, String severity, String status) {}

    public record NodeDescriptor(String nodeKey, boolean start, boolean terminal) {}

    public record RunContext(long workflowVersionId, String correlationKey, String groupJson) {}
}
