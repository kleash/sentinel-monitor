package com.sentinel.platform.aggregation.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StageAggregateRepository {
    private final JdbcTemplate jdbcTemplate;

    public StageAggregateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(long workflowVersionId,
                       String groupHash,
                       String nodeKey,
                       Instant bucketStart,
                       int inFlightDelta,
                       int completedDelta,
                       int lateDelta,
                       int failedDelta) {
        jdbcTemplate.update("""
                INSERT INTO stage_aggregate (workflow_version_id, group_dim_hash, node_key, bucket_start, in_flight, completed, late, failed)
                VALUES (?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    in_flight = GREATEST(0, in_flight + VALUES(in_flight)),
                    completed = completed + VALUES(completed),
                    late = late + VALUES(late),
                    failed = failed + VALUES(failed)
                """,
                workflowVersionId,
                groupHash,
                nodeKey,
                Timestamp.from(bucketStart.atZone(ZoneOffset.UTC).toInstant()),
                inFlightDelta,
                completedDelta,
                lateDelta,
                failedDelta);
    }
}
