package com.sentinel.platform.ingestion.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "event_raw", uniqueConstraints = {
        @UniqueConstraint(name = "uq_event_source", columnNames = {"source_system", "source_event_id"})
})
public class RawEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_event_id", nullable = false, length = 200)
    private String sourceEventId;

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "workflow_key", length = 100)
    private String workflowKey;

    @Column(name = "correlation_key", nullable = false, length = 200)
    private String correlationKey;

    @Column(name = "group_dims", columnDefinition = "json")
    private String groupDims;

    @Column(name = "event_time_utc", nullable = false)
    private Instant eventTimeUtc;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "payload", columnDefinition = "json")
    private String payload;

    @Column(name = "ingest_status", nullable = false, length = 20)
    private String ingestStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    @JsonIgnore
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public String getGroupDims() {
        return groupDims;
    }

    public void setGroupDims(String groupDims) {
        this.groupDims = groupDims;
    }

    public Instant getEventTimeUtc() {
        return eventTimeUtc;
    }

    public void setEventTimeUtc(Instant eventTimeUtc) {
        this.eventTimeUtc = eventTimeUtc;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getIngestStatus() {
        return ingestStatus;
    }

    public void setIngestStatus(String ingestStatus) {
        this.ingestStatus = ingestStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
