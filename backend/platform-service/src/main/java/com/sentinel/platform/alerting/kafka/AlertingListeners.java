package com.sentinel.platform.alerting.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sentinel.platform.alerting.service.AlertingService;

@Component
public class AlertingListeners {
    private final AlertingService alertingService;

    public AlertingListeners(AlertingService alertingService) {
        this.alertingService = alertingService;
    }

    @KafkaListener(topics = "${ruleengine.alerts-triggered-topic}", groupId = "platform-alerting")
    public void onAlertTriggered(String payload) {
        alertingService.handleAlertTriggered(payload);
    }
}
