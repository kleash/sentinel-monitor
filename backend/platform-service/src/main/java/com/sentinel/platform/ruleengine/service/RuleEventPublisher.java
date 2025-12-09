package com.sentinel.platform.ruleengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sentinel.platform.alerting.model.AlertTriggerEvent;
import com.sentinel.platform.alerting.service.AlertingService;
import com.sentinel.platform.aggregation.service.AggregationService;
import com.sentinel.platform.ruleengine.model.RuleEvaluatedEvent;

@Component
public class RuleEventPublisher {

    /**
     * Emits rule outcomes internally without Kafka hops: directly invokes aggregation and alerting services
     * to keep the workflow in-process while still serializing payloads consistently.
     */
    private static final Logger log = LoggerFactory.getLogger(RuleEventPublisher.class);

    private final AggregationService aggregationService;
    private final AlertingService alertingService;
    private final ObjectMapper objectMapper;

    public RuleEventPublisher(AggregationService aggregationService,
                              AlertingService alertingService,
                              ObjectMapper objectMapper) {
        this.aggregationService = aggregationService;
        this.alertingService = alertingService;
        this.objectMapper = objectMapper;
    }

    public void publishRuleEvaluated(RuleEvaluatedEvent event) {
        aggregationService.handleRuleEvaluated(serialize(event));
        log.info("Published rule evaluated in-process correlationKey={} workflowVersionId={} node={}",
                event.getCorrelationKey(), event.getWorkflowVersionId(), event.getNode());
    }

    public void publishAlertTriggered(AlertTriggerEvent alert) {
        alertingService.handleAlertTriggered(serialize(alert));
        log.info("Published alert triggered in-process correlationKey={} dedupeKey={} workflowRunId={}",
                alert.getCorrelationKey(), alert.getDedupeKey(), alert.getWorkflowRunId());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize event payload", ex);
        }
    }
}
