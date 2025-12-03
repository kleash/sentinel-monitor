package com.sentinel.platform.ruleengine.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RuleEvaluatedEvent {
    private long workflowVersionId;
    private long workflowRunId;
    private String node;
    private String correlationKey;
    private String status;
    private boolean late;
    private boolean orderViolation;
    private int completedDelta = 1;
    private int lateDelta;
    private int failedDelta;
    private Map<String, Integer> inFlightDeltas = new HashMap<>();
    private String groupHash;
    private Map<String, Object> group;
    private Instant eventTime;
    private Instant receivedAt;

    public long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(long workflowRunId) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isLate() {
        return late;
    }

    public void setLate(boolean late) {
        this.late = late;
    }

    public boolean isOrderViolation() {
        return orderViolation;
    }

    public void setOrderViolation(boolean orderViolation) {
        this.orderViolation = orderViolation;
    }

    public int getCompletedDelta() {
        return completedDelta;
    }

    public void setCompletedDelta(int completedDelta) {
        this.completedDelta = completedDelta;
    }

    public int getLateDelta() {
        return lateDelta;
    }

    public void setLateDelta(int lateDelta) {
        this.lateDelta = lateDelta;
    }

    public int getFailedDelta() {
        return failedDelta;
    }

    public void setFailedDelta(int failedDelta) {
        this.failedDelta = failedDelta;
    }

    public Map<String, Integer> getInFlightDeltas() {
        return inFlightDeltas;
    }

    public void setInFlightDeltas(Map<String, Integer> inFlightDeltas) {
        this.inFlightDeltas = inFlightDeltas;
    }

    public String getGroupHash() {
        return groupHash;
    }

    public void setGroupHash(String groupHash) {
        this.groupHash = groupHash;
    }

    public Map<String, Object> getGroup() {
        return group;
    }

    public void setGroup(Map<String, Object> group) {
        this.group = group;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
