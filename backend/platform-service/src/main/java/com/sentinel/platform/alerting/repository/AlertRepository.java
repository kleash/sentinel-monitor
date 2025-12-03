package com.sentinel.platform.alerting.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sentinel.platform.alerting.model.AlertTriggerEvent;

@Repository
public class AlertRepository {
    private final JdbcTemplate jdbcTemplate;

    public AlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(AlertTriggerEvent alert) {
        String dedupeKey = alert.getDedupeKey();
        String correlationKey = alert.getCorrelationKey();
        long workflowVersionId = alert.getWorkflowVersionId();
        String node = Optional.ofNullable(alert.getNode()).orElse("unknown");
        String severity = Optional.ofNullable(alert.getSeverity()).orElse("amber");
        String state = "open";
        Instant now = Optional.ofNullable(alert.getTriggeredAt()).orElse(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO alert (correlation_key, workflow_version_id, node_key, severity, state, runbook_url, dedupe_key, first_triggered_at, last_triggered_at, context)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE last_triggered_at = VALUES(last_triggered_at), severity = VALUES(severity), state = CASE WHEN state = 'resolved' THEN 'open' ELSE state END, context = VALUES(context)
                """,
                correlationKey,
                workflowVersionId,
                node,
                severity,
                state,
                alert.getRunbookUrl(),
                dedupeKey,
                Timestamp.from(now.atZone(ZoneOffset.UTC).toInstant()),
                Timestamp.from(now.atZone(ZoneOffset.UTC).toInstant()),
                alert.getContext() != null ? alert.getContext().toString() : null
        );
    }

    public Map<String, Object> findById(long id) {
        return jdbcTemplate.queryForMap("select * from alert where id = ?", id);
    }

    public int updateState(long id, String state, Instant changedAt, String actor, String reason, Instant suppressedUntil) {
        return jdbcTemplate.update("""
                update alert
                set state = ?, acked_by = ?, acked_at = ?, suppressed_until = ?, last_triggered_at = COALESCE(last_triggered_at, ?)
                where id = ?
                """,
                state,
                actor,
                Timestamp.from(changedAt.atZone(ZoneOffset.UTC).toInstant()),
                suppressedUntil != null ? Timestamp.from(suppressedUntil.atZone(ZoneOffset.UTC).toInstant()) : null,
                Timestamp.from(changedAt.atZone(ZoneOffset.UTC).toInstant()),
                id);
    }
}
