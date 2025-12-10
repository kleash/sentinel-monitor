package com.sentinel.platform.aggregation.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.sentinel.platform.aggregation.model.StageAggregate;

@Repository
public interface StageAggregateRepository extends JpaRepository<StageAggregate, Long> {
    /**
     * Maintains minute-level aggregates for workflow stages. Upsert semantics
     * are handled in SQL to keep operations idempotent under concurrent updates.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO stage_aggregate (workflow_version_id, group_dim_hash, node_key, bucket_start, in_flight, completed, late, failed)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
            ON DUPLICATE KEY UPDATE
                in_flight = GREATEST(0, in_flight + VALUES(in_flight)),
                completed = completed + VALUES(completed),
                late = late + VALUES(late),
                failed = failed + VALUES(failed)
            """, nativeQuery = true)
    void upsert(long workflowVersionId,
                String groupHash,
                String nodeKey,
                Instant bucketStart,
                int inFlightDelta,
                int completedDelta,
                int lateDelta,
                int failedDelta);

    List<StageAggregate> findByWorkflowVersionIdOrderByBucketStartDesc(Long workflowVersionId, Pageable pageable);

    List<StageAggregate> findByWorkflowVersionIdAndGroupDimHashOrderByBucketStartDesc(Long workflowVersionId, String groupDimHash, Pageable pageable);

    List<StageAggregate> findAllByOrderByBucketStartDesc(Pageable pageable);

    List<StageAggregate> findByWorkflowVersionIdAndBucketStartBetweenOrderByBucketStartDesc(Long workflowVersionId,
                                                                                            Instant start,
                                                                                            Instant end,
                                                                                            Pageable pageable);

    List<StageAggregate> findByWorkflowVersionIdAndGroupDimHashAndBucketStartBetweenOrderByBucketStartDesc(Long workflowVersionId,
                                                                                                            String groupDimHash,
                                                                                                            Instant start,
                                                                                                            Instant end,
                                                                                                            Pageable pageable);

    List<StageAggregate> findAllByBucketStartBetweenOrderByBucketStartDesc(Instant start, Instant end, Pageable pageable);
}
