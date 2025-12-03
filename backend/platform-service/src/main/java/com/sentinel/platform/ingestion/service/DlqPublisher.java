package com.sentinel.platform.ingestion.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.sentinel.platform.ingestion.model.DlqEvent;
import com.sentinel.platform.ingestion.model.DlqReason;

@Component
public class DlqPublisher {

    private final StreamBridge streamBridge;

    public DlqPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publishInvalid(String reason, Map<String, Object> original) {
        publish(new DlqEvent(DlqReason.INVALID_PAYLOAD, Instant.now(), original));
    }

    public void publishProcessingError(String reason, Map<String, Object> original) {
        publish(new DlqEvent(DlqReason.PROCESSING_ERROR, Instant.now(), original));
    }

    private void publish(DlqEvent payload) {
        streamBridge.send("dlq-out-0", MessageBuilder.withPayload(payload).build());
    }
}
