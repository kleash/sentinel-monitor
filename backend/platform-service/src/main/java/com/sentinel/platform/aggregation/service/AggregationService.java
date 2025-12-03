package com.sentinel.platform.aggregation.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sentinel.platform.aggregation.repository.StageAggregateRepository;
import com.sentinel.platform.ruleengine.model.RuleEvaluatedEvent;

@Service
public class AggregationService {
    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final StageAggregateRepository repository;
    private final ObjectMapper objectMapper;

    public AggregationService(StageAggregateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void handleRuleEvaluated(String payload) {
        try {
            RuleEvaluatedEvent event = objectMapper.readValue(payload, RuleEvaluatedEvent.class);
            Instant bucket = (event.getReceivedAt() != null ? event.getReceivedAt() : Instant.now()).truncatedTo(ChronoUnit.MINUTES);
            repository.upsert(event.getWorkflowVersionId(), event.getGroupHash(), event.getNode(), bucket,
                    0, event.getCompletedDelta(), event.getLateDelta(), event.getFailedDelta());
            Map<String, Integer> inflightDeltas = event.getInFlightDeltas() != null ? event.getInFlightDeltas() : Map.of();
            for (Map.Entry<String, Integer> inflight : inflightDeltas.entrySet()) {
                repository.upsert(event.getWorkflowVersionId(), event.getGroupHash(), inflight.getKey(), bucket,
                        inflight.getValue(), 0, 0, 0);
            }
        } catch (Exception ex) {
            log.warn("Failed to aggregate rule evaluated payload", ex);
        }
    }
}
