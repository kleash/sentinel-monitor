package com.sentinel.platform.ingestion.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sentinel.platform.ingestion.config.IngestionProperties;
import com.sentinel.platform.ingestion.model.InvalidEventException;
import com.sentinel.platform.ingestion.model.NormalizedEvent;
import com.sentinel.platform.ingestion.model.RawEventRecord;
import com.sentinel.platform.ingestion.model.RawEventRequest;
import com.sentinel.platform.ingestion.model.SaveResult;
import com.sentinel.platform.ingestion.repository.EventRawRepository;

@Service
public class IngestionService {
    /**
     * Central orchestrator for ingesting raw events (REST or Kafka), normalizing
     * them, persisting for idempotency and fanning out to downstream topics.
     */
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final String INGEST_STATUS_STORED = "STORED";

    private final EventRawRepository repository;
    private final NormalizedEventPublisher normalizedEventPublisher;
    private final DlqPublisher dlqPublisher;
    private final MeterRegistry meterRegistry;
    private final IngestionProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final RawEventValidator rawEventValidator;

    public IngestionService(EventRawRepository repository,
                            NormalizedEventPublisher normalizedEventPublisher,
                            DlqPublisher dlqPublisher,
                            MeterRegistry meterRegistry,
                            IngestionProperties properties,
                            Clock clock,
                            ObjectMapper objectMapper,
                            RawEventValidator rawEventValidator) {
        this.repository = repository;
        this.normalizedEventPublisher = normalizedEventPublisher;
        this.dlqPublisher = dlqPublisher;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.rawEventValidator = rawEventValidator;
    }

    /**
     * REST entrypoint that validates, normalizes, and publishes a single raw event.
     */
    public NormalizedEvent ingestFromRest(RawEventRequest request) {
        log.info("REST ingest requested eventType={} correlationKey={} eventId={}", request != null ? request.getEventType() : null,
                request != null ? request.getCorrelationKey() : null, request != null ? request.getEventId() : null);
        NormalizedEvent normalized = normalize(request);
        persistAndPublish(normalized);
        return normalized;
    }

    /**
     * Kafka entrypoint. Normalizes incoming events and applies DLQ routing on validation/processing failures.
     */
    public void ingestFromKafka(RawEventRequest request, Instant receivedAtOverride, Map<String, Object> originalPayloadForDlq) {
        log.info("Kafka ingest received eventType={} correlationKey={} eventId={} receivedAtOverride={}",
                request != null ? request.getEventType() : null,
                request != null ? request.getCorrelationKey() : null,
                request != null ? request.getEventId() : null,
                receivedAtOverride);
        if (receivedAtOverride != null) {
            request.setReceivedAt(receivedAtOverride);
        }
        try {
            NormalizedEvent normalized = normalize(request);
            persistAndPublish(normalized);
        } catch (InvalidEventException ex) {
            meterRegistry.counter("ingest.events.invalid").increment();
            dlqPublisher.publishInvalid(ex.getMessage(), originalPayloadForDlq);
            log.warn("Invalid event sent to DLQ reason={} eventType={} correlationKey={}",
                    ex.getMessage(), request.getEventType(), request.getCorrelationKey());
        } catch (Exception ex) {
            meterRegistry.counter("ingest.events.failed").increment();
            dlqPublisher.publishProcessingError(ex.getMessage(), originalPayloadForDlq);
            log.error("Failed to process event from Kafka, routed to DLQ", ex);
        }
    }

    /**
     * Persists the normalized event for idempotency, publishes to downstream stream,
     * and records counters/logs around dedupe and successful sends.
     */
    private void persistAndPublish(NormalizedEvent normalized) {
        MDC.put("correlationKey", normalized.getCorrelationKey());
        try {
            RawEventRecord record = toRecord(normalized);
            SaveResult result = repository.save(record);
            if (result == SaveResult.DUPLICATE) {
                meterRegistry.counter("ingest.events.duplicate").increment();
                log.info("Duplicate ingest ignored eventId={} source={} workflowKey={}", normalized.getEventId(), normalized.getSourceSystem(), normalized.getWorkflowKey());
                return;
            }
            meterRegistry.counter("ingest.events.stored").increment();
            normalizedEventPublisher.publish(normalized);
            meterRegistry.counter("ingest.events.normalized.sent").increment();
            log.info("Ingest stored and published correlationKey={} eventType={} eventId={} receivedAt={}",
                    normalized.getCorrelationKey(), normalized.getEventType(), normalized.getEventId(), normalized.getReceivedAt());
        } finally {
            MDC.remove("correlationKey");
        }
    }

    /**
     * Validates required fields, applies defaults, enforces limits, and emits a normalized event envelope.
     */
    private NormalizedEvent normalize(RawEventRequest request) {
        // Validate the envelope before attempting to normalize individual fields.
        if (request == null) {
            throw new InvalidEventException("Payload missing");
        }
        rawEventValidator.validate(request);
        String eventType = trimOrNull(request.getEventType());
        if (!StringUtils.hasText(eventType)) {
            throw new InvalidEventException("eventType is required");
        }
        String correlationKey = trimOrNull(request.getCorrelationKey());
        if (!StringUtils.hasText(correlationKey)) {
            throw new InvalidEventException("correlationKey is required");
        }
        Instant eventTime = Optional.ofNullable(request.getEventTime()).orElseThrow(() ->
                new InvalidEventException("eventTime is required"));
        String sourceSystem = StringUtils.hasText(request.getSourceSystem())
                ? request.getSourceSystem()
                : properties.getSourceSystemDefault();
        if (!StringUtils.hasText(sourceSystem)) {
            throw new InvalidEventException("sourceSystem is required");
        }

        NormalizedEvent normalized = new NormalizedEvent();
        normalized.setEventId(StringUtils.hasText(request.getEventId()) ? request.getEventId() : UUID.randomUUID().toString());
        normalized.setSourceSystem(sourceSystem);
        normalized.setEventType(eventType);
        normalized.setEventTime(eventTime);
        normalized.setReceivedAt(Optional.ofNullable(request.getReceivedAt()).orElseGet(clock::instant));
        normalized.setWorkflowKey(trimOrNull(request.getWorkflowKey()));
        normalized.setWorkflowKeys(request.getWorkflowKeys());
        normalized.setCorrelationKey(correlationKey);
        normalized.setGroup(request.getGroup());
        normalized.setPayload(request.getPayload());
        log.debug("Normalized event envelope correlationKey={} eventType={} workflowKey={} workflowKeysCount={}",
                normalized.getCorrelationKey(), normalized.getEventType(), normalized.getWorkflowKey(),
                normalized.getWorkflowKeys() != null ? normalized.getWorkflowKeys().size() : 0);
        enforceSizeLimits(normalized);
        return normalized;
    }

    private RawEventRecord toRecord(NormalizedEvent normalized) {
        return new RawEventRecord(
                normalized.getEventId(),
                normalized.getSourceSystem(),
                normalized.getEventType(),
                normalized.getWorkflowKey(),
                normalized.getCorrelationKey(),
                normalized.getGroup(),
                normalized.getPayload(),
                normalized.getEventTime(),
                normalized.getReceivedAt(),
                INGEST_STATUS_STORED
        );
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void enforceSizeLimits(NormalizedEvent normalized) {
        int payloadBytes = measureBytes(normalized.getPayload());
        int groupBytes = measureBytes(normalized.getGroup());
        if (payloadBytes > properties.getMaxPayloadBytes()) {
            throw new InvalidEventException("payload too large");
        }
        if (groupBytes > properties.getMaxGroupBytes()) {
            throw new InvalidEventException("group too large");
        }
    }

    private int measureBytes(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (Exception e) {
            throw new InvalidEventException("unable to serialize JSON for size check");
        }
    }
}
