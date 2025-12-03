package com.sentinel.platform.ruleengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ruleengine")
public class RuleEngineProperties {
    private String normalizedTopic;
    private String syntheticTopic;
    private String ruleEvaluatedTopic;
    private String alertsTriggeredTopic;
    private boolean schedulerEnabled = true;
    private int schedulerIntervalSeconds = 15;
    private int schedulerPollLimit = 100;

    public String getNormalizedTopic() {
        return normalizedTopic;
    }

    public void setNormalizedTopic(String normalizedTopic) {
        this.normalizedTopic = normalizedTopic;
    }

    public String getSyntheticTopic() {
        return syntheticTopic;
    }

    public void setSyntheticTopic(String syntheticTopic) {
        this.syntheticTopic = syntheticTopic;
    }

    public String getRuleEvaluatedTopic() {
        return ruleEvaluatedTopic;
    }

    public void setRuleEvaluatedTopic(String ruleEvaluatedTopic) {
        this.ruleEvaluatedTopic = ruleEvaluatedTopic;
    }

    public String getAlertsTriggeredTopic() {
        return alertsTriggeredTopic;
    }

    public void setAlertsTriggeredTopic(String alertsTriggeredTopic) {
        this.alertsTriggeredTopic = alertsTriggeredTopic;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public int getSchedulerIntervalSeconds() {
        return schedulerIntervalSeconds;
    }

    public void setSchedulerIntervalSeconds(int schedulerIntervalSeconds) {
        this.schedulerIntervalSeconds = schedulerIntervalSeconds;
    }

    public int getSchedulerPollLimit() {
        return schedulerPollLimit;
    }

    public void setSchedulerPollLimit(int schedulerPollLimit) {
        this.schedulerPollLimit = schedulerPollLimit;
    }
}
