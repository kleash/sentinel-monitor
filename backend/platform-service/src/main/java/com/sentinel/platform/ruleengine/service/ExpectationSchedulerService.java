package com.sentinel.platform.ruleengine.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sentinel.platform.ruleengine.config.RuleEngineProperties;
import com.sentinel.platform.ruleengine.model.SyntheticMissedEvent;
import com.sentinel.platform.ruleengine.repository.ExpectationRepository;
import com.sentinel.platform.ruleengine.repository.ExpectationRepository.ExpectationRow;

@Service
public class ExpectationSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(ExpectationSchedulerService.class);

    private final ExpectationRepository expectationRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final RuleEngineProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExpectationSchedulerService(ExpectationRepository expectationRepository,
                                       KafkaTemplate<Object, Object> kafkaTemplate,
                                       RuleEngineProperties properties,
                                       ObjectMapper objectMapper,
                                       Clock clock) {
        this.expectationRepository = expectationRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ruleengine.scheduler-interval-seconds:15}000")
    public void scheduledPoll() {
        if (!properties.isSchedulerEnabled()) {
            return;
        }
        pollAndEmit(properties.getSchedulerPollLimit());
    }

    @Transactional
    public void pollAndEmit(int limit) {
        List<ExpectationRow> due = expectationRepository.claimDuePending(limit, "scheduler");
        if (due.isEmpty()) {
            log.debug("No due expectations to emit");
            return;
        }
        log.info("Claimed {} due expectations for synthetic emission", due.size());
        for (ExpectationRow row : due) {
            SyntheticMissedEvent event = new SyntheticMissedEvent();
            event.setExpectationId(row.id());
            event.setWorkflowRunId(row.workflowRunId());
            event.setFromNode(row.fromNodeKey());
            event.setToNode(row.toNodeKey());
            event.setDueAt(row.dueAt());
            event.setSeverity(row.severity());
            event.setDedupeKey("exp-" + row.id() + "-" + row.dueAt().toEpochMilli());
            byte[] key = row.toNodeKey() != null ? row.toNodeKey().getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
            kafkaTemplate.send(properties.getSyntheticTopic(), key, serialize(event));
            log.info("Emitted synthetic.missed for expectationId={} run={} toNode={}", row.id(), row.workflowRunId(), row.toNodeKey());
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize synthetic event", e);
        }
    }
}
