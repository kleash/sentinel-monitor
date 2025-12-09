package com.sentinel.platform.ingestion.service;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.sentinel.platform.ingestion.config.IngestionProperties;
import com.sentinel.platform.ingestion.model.NormalizedEvent;

@Component
public class NormalizedEventPublisher {
    /**
     * Publishes normalized ingest events to the configured Spring Cloud Stream binding
     * so downstream rule engine consumers can react in near real time.
     */
    private static final Logger log = LoggerFactory.getLogger(NormalizedEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final IngestionProperties properties;
    private final ObjectMapper objectMapper;

    public NormalizedEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                    IngestionProperties properties,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publish(NormalizedEvent event) {
        Assert.notNull(event, "event must not be null");
        String correlationKey = StringUtils.hasText(event.getCorrelationKey()) ? event.getCorrelationKey() : event.getEventId();
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            Message<byte[]> message = MessageBuilder.withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, properties.getNormalizedTopic())
                    .setHeader(KafkaHeaders.KEY, correlationKey != null ? correlationKey.getBytes(StandardCharsets.UTF_8) : null)
                    .build();
            kafkaTemplate.send(message);
            log.info("Normalized event published topic={} correlationKey={} eventType={} eventId={}",
                    properties.getNormalizedTopic(), correlationKey, event.getEventType(), event.getEventId());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize normalized event", ex);
        }
    }
}
