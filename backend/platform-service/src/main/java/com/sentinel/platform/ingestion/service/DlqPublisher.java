package com.sentinel.platform.ingestion.service;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.sentinel.platform.ingestion.config.IngestionProperties;
import com.sentinel.platform.ingestion.model.DlqEvent;
import com.sentinel.platform.ingestion.model.DlqReason;

@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final IngestionProperties properties;
    private final ObjectMapper objectMapper;

    public DlqPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                        IngestionProperties properties,
                        ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publishInvalid(String reason, Map<String, Object> original) {
        publish(new DlqEvent(DlqReason.INVALID_PAYLOAD, Instant.now(), original));
    }

    public void publishProcessingError(String reason, Map<String, Object> original) {
        publish(new DlqEvent(DlqReason.PROCESSING_ERROR, Instant.now(), original));
    }

    private void publish(DlqEvent payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            kafkaTemplate.send(MessageBuilder.withPayload(body)
                    .setHeader(KafkaHeaders.TOPIC, properties.getDlqTopic())
                    .build());
            log.warn("DLQ event published topic={} reason={}", properties.getDlqTopic(), payload.getReason());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish DLQ event", ex);
        }
    }
}
