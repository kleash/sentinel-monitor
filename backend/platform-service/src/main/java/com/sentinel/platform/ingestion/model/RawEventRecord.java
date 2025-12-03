package com.sentinel.platform.ingestion.model;

import java.time.Instant;
import java.util.Map;

public class RawEventRecord {
    private final String eventId;
    private final String sourceSystem;
    private final String eventType;
    private final String workflowKey;
    private final String correlationKey;
    private final Map<String, Object> group;
    private final Map<String, Object> payload;
    private final Instant eventTimeUtc;
    private final Instant receivedAt;
    private final String ingestStatus;

    public RawEventRecord(String eventId,
                          String sourceSystem,
                          String eventType,
                          String workflowKey,
                          String correlationKey,
                          Map<String, Object> group,
                          Map<String, Object> payload,
                          Instant eventTimeUtc,
                          Instant receivedAt,
                          String ingestStatus) {
        this.eventId = eventId;
        this.sourceSystem = sourceSystem;
        this.eventType = eventType;
        this.workflowKey = workflowKey;
        this.correlationKey = correlationKey;
        this.group = group;
        this.payload = payload;
        this.eventTimeUtc = eventTimeUtc;
        this.receivedAt = receivedAt;
        this.ingestStatus = ingestStatus;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getEventType() {
        return eventType;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public Map<String, Object> getGroup() {
        return group;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getEventTimeUtc() {
        return eventTimeUtc;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getIngestStatus() {
        return ingestStatus;
    }
}
