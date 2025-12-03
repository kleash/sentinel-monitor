package com.sentinel.platform.ingestion.model;

import java.time.Instant;
import java.util.Map;

public class DlqEvent {
    private DlqReason reason;
    private Instant failedAt;
    private Map<String, Object> originalPayload;

    public DlqEvent() {
    }

    public DlqEvent(DlqReason reason, Instant failedAt, Map<String, Object> originalPayload) {
        this.reason = reason;
        this.failedAt = failedAt;
        this.originalPayload = originalPayload;
    }

    public DlqReason getReason() {
        return reason;
    }

    public void setReason(DlqReason reason) {
        this.reason = reason;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public Map<String, Object> getOriginalPayload() {
        return originalPayload;
    }

    public void setOriginalPayload(Map<String, Object> originalPayload) {
        this.originalPayload = originalPayload;
    }
}
