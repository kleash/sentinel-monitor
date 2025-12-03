package com.sentinel.platform.ruleengine.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.sentinel.platform.ingestion.model.NormalizedEvent;
import com.sentinel.platform.ruleengine.service.RuleEngineService;

@Component
public class RuleEngineListeners {
    private static final Logger log = LoggerFactory.getLogger(RuleEngineListeners.class);

    private final RuleEngineService ruleEngineService;
    private final ObjectMapper objectMapper;

    public RuleEngineListeners(RuleEngineService ruleEngineService, ObjectMapper objectMapper) {
        this.ruleEngineService = ruleEngineService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${ruleengine.normalized-topic}", groupId = "${RAW_CONSUMER_GROUP:platform-service}-engine")
    public void onNormalized(ConsumerRecord<String, String> record, @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long ts) {
        try {
            NormalizedEvent event = objectMapper.readValue(record.value(), NormalizedEvent.class);
            if (event.getReceivedAt() == null && ts != null) {
                event.setReceivedAt(java.time.Instant.ofEpochMilli(ts));
            }
            log.info("Rule engine received normalized event correlationKey={} eventType={}", event.getCorrelationKey(), event.getEventType());
            ruleEngineService.handleNormalizedEvent(event);
        } catch (Exception ex) {
            log.error("Failed to handle normalized event", ex);
        }
    }

    @KafkaListener(topics = "${ruleengine.synthetic-topic}", groupId = "${RAW_CONSUMER_GROUP:platform-service}-engine")
    public void onSyntheticMissed(String payload) {
        ruleEngineService.handleSyntheticMissed(payload);
    }
}
