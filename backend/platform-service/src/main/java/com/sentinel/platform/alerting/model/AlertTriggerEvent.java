package com.sentinel.platform.alerting.model;

import java.time.Instant;
import java.util.Map;

public class AlertTriggerEvent {
    private String dedupeKey;
    private long workflowVersionId;
    private Long workflowRunId;
    private String node;
    private String correlationKey;
    private String severity;
    private String reason;
    private Instant triggeredAt;

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(Long workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
