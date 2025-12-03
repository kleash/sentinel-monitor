package com.sentinel.platform.aggregation.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sentinel.platform.aggregation.service.AggregationService;

@Component
public class AggregationListeners {
    private final AggregationService aggregationService;

    public AggregationListeners(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @KafkaListener(topics = "${ruleengine.rule-evaluated-topic}", groupId = "platform-aggregation")
    public void onRuleEvaluated(String payload) {
        aggregationService.handleRuleEvaluated(payload);
    }
}
