package com.sentinel.platform.ingestion.stream;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import com.sentinel.platform.ingestion.model.RawEventRequest;
import com.sentinel.platform.ingestion.service.IngestionService;

@Configuration
public class StreamHandlers {
    private static final Logger log = LoggerFactory.getLogger(StreamHandlers.class);

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public StreamHandlers(IngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<RawEventRequest>> rawEventsConsumer() {
        return message -> {
            RawEventRequest payload = message.getPayload();
            Instant receivedAt = extractReceivedAt(message);
            Map<String, Object> originalPayload = objectMapper.convertValue(payload, Map.class);
            ingestionService.ingestFromKafka(payload, receivedAt, originalPayload);
            log.debug("Handled Kafka raw event correlationKey={} eventType={}", payload.getCorrelationKey(), payload.getEventType());
        };
    }

    private Instant extractReceivedAt(Message<?> message) {
        Object timestampHeader = message.getHeaders().get(KafkaHeaders.RECEIVED_TIMESTAMP);
        if (timestampHeader instanceof Long ts) {
            return Instant.ofEpochMilli(ts);
        }
        return null;
    }
}
