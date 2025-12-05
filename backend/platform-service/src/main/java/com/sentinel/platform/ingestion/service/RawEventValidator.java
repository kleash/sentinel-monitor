package com.sentinel.platform.ingestion.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sentinel.platform.ingestion.model.InvalidEventException;
import com.sentinel.platform.ingestion.model.RawEventRequest;

@Component
public class RawEventValidator {
    /**
     * Lightweight guardrail that validates minimal ingest envelope fields before
     * normalization, catching malformed requests early.
     */

    public void validate(RawEventRequest request) {
        Map<String, Object> payload = new java.util.HashMap<>();
        if (request != null) {
            payload.put("eventType", request.getEventType());
            payload.put("eventTime", request.getEventTime());
            payload.put("correlationKey", request.getCorrelationKey());
            payload.put("sourceSystem", request.getSourceSystem());
        }
        validateMap(payload);
    }

    public void validateMap(Map<String, Object> payload) {
        requireText(payload.get("eventType"), "eventType");
        requireText(payload.get("correlationKey"), "correlationKey");
        Object eventTime = payload.get("eventTime");
        if (!(eventTime instanceof Instant) && !(eventTime instanceof String)) {
            throw new InvalidEventException("eventTime must be ISO date-time");
        }
        if (eventTime instanceof String) {
            try {
                Instant.parse((String) eventTime);
            } catch (Exception ex) {
                throw new InvalidEventException("eventTime must be ISO date-time");
            }
        }
    }

    private void requireText(Object value, String field) {
        if (!(value instanceof String s) || !StringUtils.hasText(s)) {
            throw new InvalidEventException(field + " is required");
        }
    }
}
