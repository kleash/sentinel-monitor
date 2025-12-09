package com.sentinel.platform.ruleengine.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final RuleEngineProperties properties;
    private final Clock clock;
    private final RuleEngineService ruleEngineService;

    public ExpectationSchedulerService(ExpectationRepository expectationRepository,
                                       RuleEngineProperties properties,
                                       Clock clock,
                                       RuleEngineService ruleEngineService) {
        this.expectationRepository = expectationRepository;
        this.properties = properties;
        this.clock = clock;
        this.ruleEngineService = ruleEngineService;
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
            ruleEngineService.handleSyntheticMissed(event);
            log.info("Emitted synthetic.missed in-process for expectationId={} run={} toNode={}", row.id(), row.workflowRunId(), row.toNodeKey());
        }
    }
}
