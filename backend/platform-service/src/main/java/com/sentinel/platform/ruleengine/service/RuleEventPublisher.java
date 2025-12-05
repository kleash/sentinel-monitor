package com.sentinel.platform.ruleengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.sentinel.platform.alerting.model.AlertTriggerEvent;
import com.sentinel.platform.ruleengine.config.RuleEngineProperties;
import com.sentinel.platform.ruleengine.model.RuleEvaluatedEvent;

@Component
public class RuleEventPublisher {

    /**
     * Ships rule engine outcomes to Kafka for downstream aggregation and alerting.
     * Uses explicit keys so partitions stay aligned per correlation key.
     */
    private static final Logger log = LoggerFactory.getLogger(RuleEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final RuleEngineProperties properties;
    private final ObjectMapper objectMapper;

    public RuleEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                              RuleEngineProperties properties,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publishRuleEvaluated(RuleEvaluatedEvent event) {
        send(properties.getRuleEvaluatedTopic(), event.getCorrelationKey(), serialize(event));
        log.info("Published rule evaluated topic={} correlationKey={} workflowVersionId={} node={}",
                properties.getRuleEvaluatedTopic(), event.getCorrelationKey(), event.getWorkflowVersionId(), event.getNode());
    }

    public void publishAlertTriggered(AlertTriggerEvent alert) {
        String key = alert.getCorrelationKey() != null ? alert.getCorrelationKey() : alert.getDedupeKey();
        send(properties.getAlertsTriggeredTopic(), key, serialize(alert));
        log.info("Published alert triggered topic={} correlationKey={} dedupeKey={} workflowRunId={}",
                properties.getAlertsTriggeredTopic(), alert.getCorrelationKey(), alert.getDedupeKey(), alert.getWorkflowRunId());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize event payload", ex);
        }
    }

    private void send(String topic, String key, String payload) {
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, key != null ? key.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null)
                .build();
        kafkaTemplate.send(message);
    }
}
