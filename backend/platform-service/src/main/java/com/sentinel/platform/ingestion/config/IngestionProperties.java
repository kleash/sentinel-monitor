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
    private String normalizedTopic = "events.normalized";

    @NotBlank
    private String dlqTopic = "events.dlq";

    @NotBlank
    private String sourceSystemDefault = "rest";

    @Min(1)
    private int maxPayloadBytes = 262144;

    @Min(1)
    private int maxGroupBytes = 65536;

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
