package com.sentinel.platform.ingestion.repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneOffset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.sentinel.platform.ingestion.model.RawEventRecord;
import com.sentinel.platform.ingestion.model.SaveResult;

@Repository
public class EventRawRepository {
    private static final Logger log = LoggerFactory.getLogger(EventRawRepository.class);

    private static final String INSERT_SQL = """
            INSERT INTO event_raw (source_event_id, source_system, event_type, workflow_key, correlation_key, group_dims,
                                   event_time_utc, received_at, payload, ingest_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EventRawRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public SaveResult save(RawEventRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"id"});
                ps.setString(1, record.getEventId());
                ps.setString(2, record.getSourceSystem());
                ps.setString(3, record.getEventType());
                ps.setString(4, record.getWorkflowKey());
                ps.setString(5, record.getCorrelationKey());
                ps.setString(6, toJson(record.getGroup()));
                ps.setTimestamp(7, Timestamp.from(record.getEventTimeUtc().atZone(ZoneOffset.UTC).toInstant()));
                ps.setTimestamp(8, Timestamp.from(record.getReceivedAt().atZone(ZoneOffset.UTC).toInstant()));
                ps.setString(9, toJson(record.getPayload()));
                ps.setString(10, record.getIngestStatus());
                return ps;
            }, keyHolder);
            return SaveResult.INSERTED;
        } catch (DuplicateKeyException ex) {
            log.debug("Duplicate raw event ignored sourceSystem={} eventId={}", record.getSourceSystem(), record.getEventId());
            return SaveResult.DUPLICATE;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON field", e);
        }
    }
}
