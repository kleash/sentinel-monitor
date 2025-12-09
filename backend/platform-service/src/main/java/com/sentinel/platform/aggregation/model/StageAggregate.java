package com.sentinel.platform.aggregation.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "stage_aggregate", uniqueConstraints = {
        @UniqueConstraint(name = "uq_stage", columnNames = {"workflow_version_id", "group_dim_hash", "node_key", "bucket_start"})
})
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StageAggregate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_version_id", nullable = false)
    private Long workflowVersionId;

    @Column(name = "group_dim_hash")
    private String groupDimHash;

    @Column(name = "node_key", nullable = false)
    private String nodeKey;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "in_flight", nullable = false)
    private int inFlight;

    @Column(nullable = false)
    private int completed;

    @Column(nullable = false)
    private int late;

    @Column(nullable = false)
    private int failed;

    public Long getId() {
        return id;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public String getGroupDimHash() {
        return groupDimHash;
    }

    public void setGroupDimHash(String groupDimHash) {
        this.groupDimHash = groupDimHash;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public Instant getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(Instant bucketStart) {
        this.bucketStart = bucketStart;
    }

    public int getInFlight() {
        return inFlight;
    }

    public void setInFlight(int inFlight) {
        this.inFlight = inFlight;
    }

    public int getCompleted() {
        return completed;
    }

    public void setCompleted(int completed) {
        this.completed = completed;
    }

    public int getLate() {
        return late;
    }

    public void setLate(int late) {
        this.late = late;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }
}
