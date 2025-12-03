package com.sentinel.platform.alerting.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, String entityId, String action, String actor, Map<String, Object> details) {
        jdbcTemplate.update("""
                insert into audit_log (entity_type, entity_id, action, actor, created_at, details)
                values (?,?,?,?,?,?)
                """,
                entityType,
                entityId,
                action,
                actor,
                Timestamp.from(Instant.now().atZone(ZoneOffset.UTC).toInstant()),
                toJson(details));
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
