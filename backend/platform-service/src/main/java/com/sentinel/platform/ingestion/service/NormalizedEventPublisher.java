package com.sentinel.platform.ingestion.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.sentinel.platform.ingestion.model.NormalizedEvent;

@Component
public class NormalizedEventPublisher {
    /**
     * Publishes normalized ingest events to the configured Spring Cloud Stream binding
     * so downstream rule engine consumers can react in near real time.
     */
    private static final Logger log = LoggerFactory.getLogger(NormalizedEventPublisher.class);

    private final StreamBridge streamBridge;

    public NormalizedEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(NormalizedEvent event) {
        Assert.notNull(event, "event must not be null");
        String correlationKey = StringUtils.hasText(event.getCorrelationKey()) ? event.getCorrelationKey() : event.getEventId();
        Message<NormalizedEvent> message = MessageBuilder.withPayload(event)
                .setHeader(KafkaHeaders.KEY, correlationKey.getBytes(StandardCharsets.UTF_8))
                .build();
        boolean sent = streamBridge.send("normalizedEvents-out-0", message);
        if (!sent) {
            log.error("Failed to publish normalized event to stream binding normalizedEvents-out-0");
            throw new IllegalStateException("Failed to publish normalized event");
        }
        log.info("Normalized event published topic={} correlationKey={} eventType={} eventId={}", "normalizedEvents-out-0",
                correlationKey, event.getEventType(), event.getEventId());
    }
}
