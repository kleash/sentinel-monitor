package com.sentinel.platform.alerting.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sentinel.platform.alerting.model.AlertTriggerEvent;
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
            AlertTriggerEvent alert = objectMapper.readValue(payload, AlertTriggerEvent.class);
            alertRepository.upsert(alert);
        } catch (Exception ex) {
            log.error("Failed to handle alerts.triggered payload", ex);
        }
    }

    public boolean ack(long id, String actor, String reason) {
        int updated = alertRepository.updateState(id, "ack", clock.instant(), actor, reason, null);
        if (updated > 0) {
            auditRepository.record("alert", String.valueOf(id), "ack", actor, Map.of("reason", reason));
        }
        return updated > 0;
    }

    public boolean suppress(long id, String actor, String reason, Instant until) {
        int updated = alertRepository.updateState(id, "suppressed", clock.instant(), actor, reason, until);
        if (updated > 0) {
            auditRepository.record("alert", String.valueOf(id), "suppress", actor, Map.of("reason", reason, "until", until));
        }
        return updated > 0;
    }

    public boolean resolve(long id, String actor, String reason) {
        int updated = alertRepository.updateState(id, "resolved", clock.instant(), actor, reason, null);
        if (updated > 0) {
            auditRepository.record("alert", String.valueOf(id), "resolve", actor, Map.of("reason", reason));
        }
        return updated > 0;
    }
}
