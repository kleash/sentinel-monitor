package com.sentinel.platform.ruleengine.model;

import java.time.Instant;

public class SyntheticMissedEvent {
    private long expectationId;
    private long workflowRunId;
    private String fromNode;
    private String toNode;
    private Instant dueAt;
    private String severity;
    private String dedupeKey;

    public long getExpectationId() {
        return expectationId;
    }

    public void setExpectationId(long expectationId) {
        this.expectationId = expectationId;
    }

    public long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(long workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public String getFromNode() {
        return fromNode;
    }

    public void setFromNode(String fromNode) {
        this.fromNode = fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public void setToNode(String toNode) {
        this.toNode = toNode;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }
}
