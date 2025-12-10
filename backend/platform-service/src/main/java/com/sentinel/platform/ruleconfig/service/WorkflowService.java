package com.sentinel.platform.ruleconfig.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.platform.ruleconfig.model.Workflow;
import com.sentinel.platform.ruleconfig.model.WorkflowEdge;
import com.sentinel.platform.ruleconfig.model.WorkflowNode;
import com.sentinel.platform.ruleconfig.model.WorkflowVersion;
import com.sentinel.platform.ruleconfig.repository.WorkflowEdgeRepository;
import com.sentinel.platform.ruleconfig.repository.WorkflowNodeRepository;
import com.sentinel.platform.ruleconfig.repository.WorkflowRepository;
import com.sentinel.platform.ruleconfig.repository.WorkflowVersionRepository;
import com.sentinel.platform.ruleconfig.web.dto.WorkflowRequest;
import com.sentinel.platform.ruleconfig.web.dto.WorkflowView;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowRepository workflowRepository,
                           WorkflowVersionRepository workflowVersionRepository,
                           WorkflowNodeRepository workflowNodeRepository,
                           WorkflowEdgeRepository workflowEdgeRepository,
                           ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowNodeRepository = workflowNodeRepository;
        this.workflowEdgeRepository = workflowEdgeRepository;
        this.objectMapper = objectMapper;
    }

    public List<Workflow> list() {
        return workflowRepository.findAll();
    }

    public List<WorkflowView> listViews() {
        return workflowRepository.findAll()
                .stream()
                .map(this::toView)
                .toList();
    }

    public Optional<Workflow> findByKey(String key) {
        return workflowRepository.findByKey(key);
    }

    public Optional<WorkflowView> findViewByKey(String key) {
        return workflowRepository.findByKey(key).map(this::toView);
    }

    @Transactional
    public Workflow createWorkflow(WorkflowRequest request) {
        if (workflowRepository.existsByKey(request.getKey())) {
            throw new IllegalArgumentException("workflow key already exists");
        }
        Workflow workflow = new Workflow();
        workflow.setName(request.getName());
        workflow.setKey(request.getKey());
        workflow.setOwner(request.getCreatedBy());
        Workflow saved = workflowRepository.save(workflow);

        WorkflowVersion version = new WorkflowVersion();
        version.setWorkflow(saved);
        version.setVersionNum(1);
        version.setDefinitionJson(toJson(request.getGraph()));
        version.setStatus("published");
        version.setCreatedBy(request.getCreatedBy());
        workflowVersionRepository.save(version);
        persistGraph(version, request.getGraph());
        saved.setActiveVersionId(version.getId());
        workflowRepository.save(saved);
        return saved;
    }

    public WorkflowView toView(Workflow workflow) {
        Map<String, Object> graph = Map.of();
        List<String> groupDimensions = List.of();
        String activeVersionLabel = null;
        if (workflow.getActiveVersionId() != null) {
            Optional<WorkflowVersion> activeVersion = workflowVersionRepository.findById(workflow.getActiveVersionId());
            if (activeVersion.isPresent()) {
                WorkflowVersion version = activeVersion.get();
                graph = parseGraph(version);
                groupDimensions = extractGroupDimensions(graph);
                activeVersionLabel = version.getVersionNum() != null ? "v" + version.getVersionNum() : String.valueOf(version.getId());
            }
        }
        return new WorkflowView(
                String.valueOf(workflow.getId()),
                workflow.getKey(),
                workflow.getName(),
                "green",
                activeVersionLabel,
                graph,
                groupDimensions
        );
    }

    private void persistGraph(WorkflowVersion version, Map<String, Object> graph) {
        try {
            @SuppressWarnings("unchecked")
            var nodes = (java.util.List<Map<String, Object>>) graph.getOrDefault("nodes", java.util.List.of());
            java.util.Map<String, WorkflowNode> nodeMap = new java.util.HashMap<>();
            for (Map<String, Object> n : nodes) {
                WorkflowNode node = new WorkflowNode();
                node.setWorkflowVersion(version);
                node.setNodeKey(String.valueOf(n.get("key")));
                node.setEventType(String.valueOf(n.get("eventType")));
                node.setStart(Boolean.TRUE.equals(n.get("start")));
                node.setTerminal(Boolean.TRUE.equals(n.get("terminal")));
                node.setOrderingPolicy(n.get("orderingPolicy") != null ? n.get("orderingPolicy").toString() : null);
                workflowNodeRepository.save(node);
                nodeMap.put(node.getNodeKey(), node);
            }
            @SuppressWarnings("unchecked")
            var edges = (java.util.List<Map<String, Object>>) graph.getOrDefault("edges", java.util.List.of());
            for (Map<String, Object> e : edges) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setFromNode(nodeMap.get(String.valueOf(e.get("from"))));
                edge.setToNode(nodeMap.get(String.valueOf(e.get("to"))));
                if (e.get("maxLatencySec") != null) {
                    edge.setMaxLatencySec(((Number) e.get("maxLatencySec")).intValue());
                }
                if (e.get("absoluteDeadline") != null) {
                    edge.setAbsoluteDeadline(e.get("absoluteDeadline").toString());
                }
                edge.setOptional(Boolean.TRUE.equals(e.get("optional")));
                if (e.get("expectedCount") != null) {
                    edge.setExpectedCount(((Number) e.get("expectedCount")).intValue());
                }
                edge.setSeverity(e.get("severity") != null ? e.get("severity").toString() : null);
                workflowEdgeRepository.save(edge);
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid graph payload", ex);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid graph payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGraph(WorkflowVersion version) {
        if (version == null || version.getDefinitionJson() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(version.getDefinitionJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractGroupDimensions(Map<String, Object> graph) {
        Object dims = graph.get("groupDimensions");
        if (dims instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
