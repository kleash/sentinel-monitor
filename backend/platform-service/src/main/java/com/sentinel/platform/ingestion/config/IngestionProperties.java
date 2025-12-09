package com.sentinel.platform.ingestion.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestion")
public class IngestionProperties {

    @Min(1)
    private int maxConcurrentRequests = 32;

    @NotBlank
    private String rawTopic = "events.raw";

    @NotBlank
    private String rawConsumerGroup = "platform-service";

    @NotBlank
    private String normalizedTopic = "events.normalized";

    @NotBlank
    private String dlqTopic = "events.dlq";

    @NotBlank
    private String sourceSystemDefault = "rest";

    @Min(0)
    private int maxPayloadBytes = 0;

    @Min(0)
    private int maxGroupBytes = 0;

    @Min(0)
    private int producerLingerMs = 0;

    @Min(0)
    private int producerRetries = 3;

    @NotBlank
    private String producerCompression = "lz4";

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public String getNormalizedTopic() {
        return normalizedTopic;
    }

    public void setNormalizedTopic(String normalizedTopic) {
        this.normalizedTopic = normalizedTopic;
    }

    public String getRawTopic() {
        return rawTopic;
    }

    public void setRawTopic(String rawTopic) {
        this.rawTopic = rawTopic;
    }

    public String getRawConsumerGroup() {
        return rawConsumerGroup;
    }

    public void setRawConsumerGroup(String rawConsumerGroup) {
        this.rawConsumerGroup = rawConsumerGroup;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public String getSourceSystemDefault() {
        return sourceSystemDefault;
    }

    public void setSourceSystemDefault(String sourceSystemDefault) {
        this.sourceSystemDefault = sourceSystemDefault;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getMaxGroupBytes() {
        return maxGroupBytes;
    }

    public void setMaxGroupBytes(int maxGroupBytes) {
        this.maxGroupBytes = maxGroupBytes;
    }

    public int getProducerLingerMs() {
        return producerLingerMs;
    }

    public void setProducerLingerMs(int producerLingerMs) {
        this.producerLingerMs = producerLingerMs;
    }

    public int getProducerRetries() {
        return producerRetries;
    }

    public void setProducerRetries(int producerRetries) {
        this.producerRetries = producerRetries;
    }

    public String getProducerCompression() {
        return producerCompression;
    }

    public void setProducerCompression(String producerCompression) {
        this.producerCompression = producerCompression;
    }
}
