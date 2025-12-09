package com.sentinel.platform.ruleengine.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sentinel.platform.alerting.model.AlertTriggerEvent;
import com.sentinel.platform.ingestion.model.NormalizedEvent;
import com.sentinel.platform.ruleconfig.model.Workflow;
import com.sentinel.platform.ruleconfig.model.WorkflowVersion;
import com.sentinel.platform.ruleconfig.repository.WorkflowNodeRepository;
import com.sentinel.platform.ruleconfig.repository.WorkflowRepository;
import com.sentinel.platform.ruleconfig.repository.WorkflowVersionRepository;
import com.sentinel.platform.ruleengine.model.RuleEvaluatedEvent;
import com.sentinel.platform.ruleengine.model.SyntheticMissedEvent;
import com.sentinel.platform.ruleengine.repository.RuleEngineStateRepository;
import com.sentinel.platform.ruleengine.repository.RuleEngineStateRepository.ExpectationRecord;
import com.sentinel.platform.ruleengine.repository.RuleEngineStateRepository.NodeDescriptor;
import com.sentinel.platform.ruleengine.repository.RuleEngineStateRepository.OutgoingEdge;
import com.sentinel.platform.ruleengine.repository.RuleEngineStateRepository.RunContext;

@Service
public class RuleEngineService {
    /**
     * Applies workflow rules to normalized events, managing runtime state and emitting
     * both rule evaluation and alert trigger events for downstream aggregation/alerting.
     */
    private static final Logger log = LoggerFactory.getLogger(RuleEngineService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final RuleEngineStateRepository stateRepository;
    private final RuleEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RuleEngineService(WorkflowRepository workflowRepository,
                             WorkflowVersionRepository workflowVersionRepository,
                             WorkflowNodeRepository workflowNodeRepository,
                             RuleEngineStateRepository stateRepository,
                             RuleEventPublisher eventPublisher,
                             ObjectMapper objectMapper,
                             Clock clock) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowNodeRepository = workflowNodeRepository;
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Entry point for normalized ingest events. Resolves applicable workflow versions
     * and processes the event against each version's runtime state.
     */
    public void handleNormalizedEvent(NormalizedEvent event) {
        List<WorkflowVersion> targets = resolveTargetVersions(event);
        if (targets.isEmpty()) {
            log.debug("No workflow versions resolved for eventType={} workflowKey={} correlationKey={}",
                    event.getEventType(), event.getWorkflowKey(), event.getCorrelationKey());
            return;
        }
        log.info("Processing normalized event correlationKey={} eventType={} targets={}", event.getCorrelationKey(), event.getEventType(), targets.size());
        for (WorkflowVersion version : targets) {
            processEventForVersion(event, version);
        }
    }

    /**
     * Handles scheduler-produced synthetic misses to close out expectations and emit alerts.
     */
    public void handleSyntheticMissed(String payload) {
        try {
            SyntheticMissedEvent missed = objectMapper.readValue(payload, SyntheticMissedEvent.class);
            handleSyntheticMissed(missed);
        } catch (Exception ex) {
            log.error("Failed to process synthetic missed payload={}", payload, ex);
        }
    }

    public void handleSyntheticMissed(SyntheticMissedEvent missed) {
        try {
            log.info("Handling synthetic missed workflowRunId={} toNode={} severity={}", missed.getWorkflowRunId(), missed.getToNode(), missed.getSeverity());
            RunContext runContext = stateRepository.loadRunContext(missed.getWorkflowRunId());
            Map<String, Object> group = parseGroup(runContext.groupJson());

            RuleEvaluatedEvent evaluated = new RuleEvaluatedEvent();
            evaluated.setWorkflowVersionId(runContext.workflowVersionId());
            evaluated.setWorkflowRunId(missed.getWorkflowRunId());
            evaluated.setNode(missed.getToNode());
            evaluated.setCorrelationKey(runContext.correlationKey());
            evaluated.setStatus(normalizeSeverity(missed.getSeverity()));
            evaluated.setLate(true);
            evaluated.setOrderViolation(false);
            evaluated.setLateDelta(1);
            evaluated.setCompletedDelta(0);
            evaluated.setInFlightDeltas(Map.of());
            evaluated.setGroup(group);
            evaluated.setGroupHash(hashGroup(group));
            evaluated.setEventTime(missed.getDueAt());
            evaluated.setReceivedAt(clock.instant());
            eventPublisher.publishRuleEvaluated(evaluated);
            stateRepository.updateRun(missed.getWorkflowRunId(), normalizeSeverity(missed.getSeverity()), clock.instant(), missed.getToNode());

            AlertTriggerEvent alert = new AlertTriggerEvent();
            alert.setWorkflowVersionId(runContext.workflowVersionId());
            alert.setWorkflowRunId(missed.getWorkflowRunId());
            alert.setNode(missed.getToNode());
            alert.setCorrelationKey(runContext.correlationKey());
            alert.setSeverity(normalizeSeverity(missed.getSeverity()));
            alert.setReason("EXPECTED_MISSED");
            alert.setDedupeKey(missed.getDedupeKey());
            alert.setTriggeredAt(clock.instant());
            eventPublisher.publishAlertTriggered(alert);
        } catch (Exception ex) {
            log.error("Failed to handle synthetic missed event", ex);
        }
    }

    /**
     * Applies a normalized event to a specific workflow version, managing run creation,
     * expectation clearing/creation, and downstream emissions.
     */
    private void processEventForVersion(NormalizedEvent event, WorkflowVersion version) {
        Optional<NodeDescriptor> nodeOpt = stateRepository.findNodeForEvent(version.getId(), event.getEventType());
        if (nodeOpt.isEmpty()) {
            log.warn("No node found for eventType={} workflowVersion={} correlationKey={}",
                    event.getEventType(), version.getId(), event.getCorrelationKey());
            return;
        }
        NodeDescriptor node = nodeOpt.get();
        Long runId = stateRepository.findRunId(version.getId(), event.getCorrelationKey());
        if (runId == null) {
            runId = stateRepository.createRun(version.getId(), event.getCorrelationKey(), "green", event.getEventTime(), toJson(event.getGroup()));
            log.info("Created new workflow run version={} runId={} correlationKey={} startNode={}",
                    version.getId(), runId, event.getCorrelationKey(), node.nodeKey());
        }
        boolean duplicate = StringUtils.hasText(event.getEventId()) && stateRepository.hasSeenEvent(runId, event.getEventId());
        if (duplicate) {
            log.info("Duplicate event ignored correlationKey={} eventId={} version={}", event.getCorrelationKey(), event.getEventId(), version.getId());
            return;
        }

        List<ExpectationRecord> cleared = stateRepository.clearExpectations(runId, node.nodeKey(), event.getReceivedAt());
        boolean late = cleared.stream().anyMatch(rec -> event.getReceivedAt().isAfter(rec.dueAt()));
        boolean optionalInbound = stateRepository.hasOptionalInbound(version.getId(), node.nodeKey());
        boolean orderViolation = cleared.isEmpty() && !node.start() && !optionalInbound;

        Map<String, Integer> inFlightDeltas = new HashMap<>();
        if (!cleared.isEmpty()) {
            inFlightDeltas.merge(node.nodeKey(), -cleared.size(), Integer::sum);
            log.info("Cleared expectations runId={} node={} clearedCount={} late={}", runId, node.nodeKey(), cleared.size(), late);
        }

        for (OutgoingEdge edge : stateRepository.fetchOutgoingEdges(version.getId(), node.nodeKey())) {
            if (edge.optional()) {
                continue;
            }
            Instant dueAt = computeDueAt(event.getEventTime(), edge);
            int expectedCount = edge.expectedCount() != null && edge.expectedCount() > 0 ? edge.expectedCount() : 1;
            for (int i = 0; i < expectedCount; i++) {
                stateRepository.createExpectation(runId, node.nodeKey(), edge.toNodeKey(), dueAt, edge.severity());
                inFlightDeltas.merge(edge.toNodeKey(), 1, Integer::sum);
            }
            log.debug("Created expectation runId={} fromNode={} toNode={} dueAt={} severity={}", runId, node.nodeKey(), edge.toNodeKey(), dueAt, edge.severity());
        }

        stateRepository.saveOccurrence(runId, node.nodeKey(), event.getEventId(), event.getEventTime(), event.getReceivedAt(),
                payloadExcerpt(event.getPayload()), late, duplicate, orderViolation, null);

        String status = deriveStatus(late, orderViolation, cleared);
        stateRepository.updateRun(runId, status, clock.instant(), node.nodeKey());
        log.info("Rule evaluated runId={} version={} node={} status={} late={} orderViolation={} inFlightDeltas={}",
                runId, version.getId(), node.nodeKey(), status, late, orderViolation, inFlightDeltas);

        RuleEvaluatedEvent evaluated = new RuleEvaluatedEvent();
        evaluated.setWorkflowVersionId(version.getId());
        evaluated.setWorkflowRunId(runId);
        evaluated.setNode(node.nodeKey());
        evaluated.setCorrelationKey(event.getCorrelationKey());
        evaluated.setStatus(status);
        evaluated.setLate(late);
        evaluated.setOrderViolation(orderViolation);
        evaluated.setCompletedDelta(1);
        evaluated.setLateDelta(late ? 1 : 0);
        evaluated.setFailedDelta(orderViolation ? 1 : 0);
        evaluated.setInFlightDeltas(inFlightDeltas);
        evaluated.setGroup(event.getGroup());
        evaluated.setGroupHash(hashGroup(event.getGroup()));
        evaluated.setEventTime(event.getEventTime());
        evaluated.setReceivedAt(event.getReceivedAt());
        eventPublisher.publishRuleEvaluated(evaluated);

        if (late || orderViolation) {
            AlertTriggerEvent alert = new AlertTriggerEvent();
            alert.setWorkflowVersionId(version.getId());
            alert.setWorkflowRunId(runId);
            alert.setNode(node.nodeKey());
            alert.setCorrelationKey(event.getCorrelationKey());
            alert.setSeverity(severityFromExpectations(cleared, orderViolation));
            alert.setReason(late ? "SLA_MISSED" : "ORDER_VIOLATION");
            alert.setDedupeKey(version.getId() + ":" + node.nodeKey() + ":" + event.getCorrelationKey());
            alert.setTriggeredAt(event.getReceivedAt());
            eventPublisher.publishAlertTriggered(alert);
        }
    }

    private List<WorkflowVersion> resolveTargetVersions(NormalizedEvent event) {
        Set<Long> versionIds = new HashSet<>();
        List<WorkflowVersion> targets = new java.util.ArrayList<>();

        if (event.getWorkflowKeys() != null && !event.getWorkflowKeys().isEmpty()) {
            for (String key : event.getWorkflowKeys()) {
                workflowRepository.findByKey(key).flatMap(this::resolveActiveVersion).ifPresent(v -> addIfNew(versionIds, targets, v));
            }
        } else if (StringUtils.hasText(event.getWorkflowKey())) {
            workflowRepository.findByKey(event.getWorkflowKey()).flatMap(this::resolveActiveVersion).ifPresent(v -> addIfNew(versionIds, targets, v));
        } else {
            workflowNodeRepository.findActiveByEventType(event.getEventType()).forEach(node -> {
                WorkflowVersion v = node.getWorkflowVersion();
                addIfNew(versionIds, targets, v);
            });
        }
        return targets;
    }

    private void addIfNew(Set<Long> seen, List<WorkflowVersion> targets, WorkflowVersion version) {
        if (seen.add(version.getId())) {
            targets.add(version);
        }
    }

    private Optional<WorkflowVersion> resolveActiveVersion(Workflow workflow) {
        if (workflow.getActiveVersionId() != null) {
            return workflowVersionRepository.findById(workflow.getActiveVersionId());
        }
        return workflowVersionRepository.findFirstByWorkflowIdOrderByVersionNumDesc(workflow.getId());
    }

    private Instant computeDueAt(Instant eventTime, OutgoingEdge edge) {
        Instant dueAt = eventTime;
        if (edge.absoluteDeadline() != null) {
            try {
                String deadline = edge.absoluteDeadline();
                if (deadline.contains("+") || deadline.contains("Z") || deadline.contains("-")) {
                    OffsetTime ot = OffsetTime.parse(deadline);
                    dueAt = LocalDate.ofInstant(eventTime, ZoneOffset.UTC).atTime(ot.toLocalTime()).toInstant(ZoneOffset.UTC);
                } else {
                    LocalTime lt = LocalTime.parse(deadline, DateTimeFormatter.ofPattern("HH:mm"));
                    dueAt = LocalDate.ofInstant(eventTime, ZoneOffset.UTC).atTime(lt).toInstant(ZoneOffset.UTC);
                }
                if (dueAt.isBefore(eventTime)) {
                    dueAt = dueAt.plusSeconds(86400);
                }
            } catch (Exception ex) {
                log.warn("Failed to parse absolute deadline {} for edge to {}. Falling back to eventTime", edge.absoluteDeadline(), edge.toNodeKey());
                dueAt = eventTime;
            }
        } else if (edge.maxLatencySec() != null && edge.maxLatencySec() > 0) {
            dueAt = dueAt.plusSeconds(edge.maxLatencySec());
        }
        return dueAt;
    }

    private String deriveStatus(boolean late, boolean orderViolation, List<ExpectationRecord> cleared) {
        if (orderViolation) {
            return "red";
        }
        if (late) {
            return normalizeSeverity(severityFromExpectations(cleared, false));
        }
        return "green";
    }

    private String severityFromExpectations(List<ExpectationRecord> cleared, boolean orderViolation) {
        if (orderViolation) {
            return "red";
        }
        return cleared.stream()
                .map(ExpectationRecord::severity)
                .map(this::normalizeSeverity)
                .max((a, b) -> Integer.compare(severityRank(a), severityRank(b)))
                .orElse("amber");
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return "amber";
        }
        String s = severity.toLowerCase();
        if (s.equals("red") || s.equals("amber") || s.equals("green")) {
            return s;
        }
        return "amber";
    }

    private int severityRank(String severity) {
        return switch (normalizeSeverity(severity)) {
            case "red" -> 3;
            case "amber" -> 2;
            default -> 1;
        };
    }

    private String hashGroup(Map<String, Object> group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> sorted = new TreeMap<>(group);
            String serialized = objectMapper.writeValueAsString(sorted);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to hash group dims", e);
            return null;
        }
    }

    private String payloadExcerpt(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() > 500 ? json.substring(0, 500) : json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> parseGroup(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
