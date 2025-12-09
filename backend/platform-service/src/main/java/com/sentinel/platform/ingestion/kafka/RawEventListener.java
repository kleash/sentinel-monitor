package com.sentinel.platform.ingestion.kafka;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.sentinel.platform.ingestion.model.RawEventRequest;
import com.sentinel.platform.ingestion.service.DlqPublisher;
import com.sentinel.platform.ingestion.service.IngestionService;

@Component
public class RawEventListener {
    /**
     * Kafka adapter for raw ingest events. Converts the wire payload to a {@link RawEventRequest},
     * forwards to the ingestion service, and routes malformed messages to the DLQ.
     */
    private static final Logger log = LoggerFactory.getLogger(RawEventListener.class);

    private final IngestionService ingestionService;
    private final DlqPublisher dlqPublisher;
    private final ObjectMapper objectMapper;

    public RawEventListener(IngestionService ingestionService,
                            DlqPublisher dlqPublisher,
                            ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.dlqPublisher = dlqPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${ingestion.raw-topic}", groupId = "${ingestion.raw-consumer-group}")
    public void onRawEvent(@Payload String payload,
                           @Header(name = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long receivedTimestamp) {
        try {
            RawEventRequest request = objectMapper.readValue(payload, RawEventRequest.class);
            Instant receivedAt = receivedTimestamp != null ? Instant.ofEpochMilli(receivedTimestamp) : null;
            Map<String, Object> originalPayload = objectMapper.readValue(payload, Map.class);
            ingestionService.ingestFromKafka(request, receivedAt, originalPayload);
        } catch (Exception ex) {
            log.warn("Failed to process raw Kafka event, sending to DLQ", ex);
            dlqPublisher.publishInvalid("deserialization failure", Map.of("rawPayload", payload));
        }
    }
}
