package com.sentinel.platform.alerting.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_key", nullable = false, length = 200)
    private String correlationKey;

    @Column(name = "workflow_version_id", nullable = false)
    private Long workflowVersionId;

    @Column(name = "node_key", nullable = false, length = 100)
    private String nodeKey;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "dedupe_key", nullable = false, length = 300)
    private String dedupeKey;

    @Column(name = "first_triggered_at", nullable = false)
    private Instant firstTriggeredAt;

    @Column(name = "last_triggered_at", nullable = false)
    private Instant lastTriggeredAt;

    @Column(name = "acked_by", length = 200)
    private String ackedBy;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "suppressed_until")
    private Instant suppressedUntil;

    public Long getId() {
        return id;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public Instant getFirstTriggeredAt() {
        return firstTriggeredAt;
    }

    public void setFirstTriggeredAt(Instant firstTriggeredAt) {
        this.firstTriggeredAt = firstTriggeredAt;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public String getAckedBy() {
        return ackedBy;
    }

    public void setAckedBy(String ackedBy) {
        this.ackedBy = ackedBy;
    }

    public Instant getAckedAt() {
        return ackedAt;
    }

    public void setAckedAt(Instant ackedAt) {
        this.ackedAt = ackedAt;
    }

    public Instant getSuppressedUntil() {
        return suppressedUntil;
    }

    public void setSuppressedUntil(Instant suppressedUntil) {
        this.suppressedUntil = suppressedUntil;
    }
}
