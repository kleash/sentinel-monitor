package com.sentinel.platform.ruleengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    }

    public void publishAlertTriggered(AlertTriggerEvent alert) {
        String key = alert.getCorrelationKey() != null ? alert.getCorrelationKey() : alert.getDedupeKey();
        send(properties.getAlertsTriggeredTopic(), key, serialize(alert));
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
