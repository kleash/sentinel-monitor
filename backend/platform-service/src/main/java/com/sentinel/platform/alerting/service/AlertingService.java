package com.sentinel.platform.alerting.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sentinel.platform.alerting.model.Alert;
import com.sentinel.platform.alerting.model.AlertTriggerEvent;
import com.sentinel.platform.alerting.model.AuditLogEntry;
import com.sentinel.platform.alerting.repository.AlertRepository;
import com.sentinel.platform.alerting.repository.AuditRepository;

@Service
public class AlertingService {
    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final AlertRepository alertRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AlertingService(AlertRepository alertRepository, AuditRepository auditRepository, ObjectMapper objectMapper, Clock clock) {
        this.alertRepository = alertRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void handleAlertTriggered(String payload) {
        try {
            AlertTriggerEvent trigger = objectMapper.readValue(payload, AlertTriggerEvent.class);
            String dedupeKey = Optional.ofNullable(trigger.getDedupeKey())
                    .orElse(trigger.getWorkflowVersionId() + ":" + trigger.getNode() + ":" + trigger.getCorrelationKey());
            Alert alert = alertRepository.findFirstByDedupeKey(dedupeKey).orElseGet(Alert::new);
            alert.setCorrelationKey(trigger.getCorrelationKey());
            alert.setWorkflowVersionId(trigger.getWorkflowVersionId());
            alert.setNodeKey(Optional.ofNullable(trigger.getNode()).orElse("unknown"));
            alert.setSeverity(Optional.ofNullable(trigger.getSeverity()).orElse("amber"));
            alert.setDedupeKey(dedupeKey);
            Instant triggeredAt = Optional.ofNullable(trigger.getTriggeredAt()).orElse(clock.instant());
            alert.setFirstTriggeredAt(alert.getFirstTriggeredAt() == null ? triggeredAt : alert.getFirstTriggeredAt());
            alert.setLastTriggeredAt(triggeredAt);
            String existingState = Optional.ofNullable(alert.getState()).orElse("open");
            alert.setState("resolved".equalsIgnoreCase(existingState) ? "open" : existingState);
            alertRepository.save(alert);
        } catch (Exception ex) {
            log.error("Failed to handle alerts.triggered payload", ex);
        }
    }

    public List<Alert> list(String state, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        if (state == null) {
            return alertRepository.findAllByOrderByLastTriggeredAtDesc(page);
        }
        return alertRepository.findByStateOrderByLastTriggeredAtDesc(state, page);
    }

    public boolean ack(long id, String actor, String reason) {
        return updateState(id, "ack", actor, reason, null);
    }

    public boolean suppress(long id, String actor, String reason, Instant until) {
        return updateState(id, "suppressed", actor, reason, until);
    }

    public boolean resolve(long id, String actor, String reason) {
        return updateState(id, "resolved", actor, reason, null);
    }

    private boolean updateState(long id, String state, String actor, String reason, Instant suppressedUntil) {
        return alertRepository.findById(id).map(alert -> {
            Instant now = clock.instant();
            alert.setState(state);
            alert.setAckedBy(actor);
            alert.setAckedAt(now);
            alert.setSuppressedUntil(suppressedUntil);
            if (alert.getLastTriggeredAt() == null) {
                alert.setLastTriggeredAt(now);
            }
            alertRepository.save(alert);
            recordAudit(id, state, actor, reason, suppressedUntil);
            return true;
        }).orElse(false);
    }

    private void recordAudit(long alertId, String action, String actor, String reason, Instant until) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setEntityType("alert");
        entry.setEntityId(String.valueOf(alertId));
        entry.setAction(action);
        entry.setActor(actor);
        entry.setDetails(serializeDetails(reason, until));
        auditRepository.save(entry);
    }

    private String serializeDetails(String reason, Instant until) {
        try {
            return objectMapper.writeValueAsString(Map.of("reason", reason, "until", until));
        } catch (Exception e) {
            log.debug("Failed to serialize audit details", e);
            return null;
        }
    }
}
