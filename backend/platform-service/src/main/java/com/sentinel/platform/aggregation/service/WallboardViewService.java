package com.sentinel.platform.aggregation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sentinel.platform.aggregation.model.StageAggregate;
import com.sentinel.platform.aggregation.web.dto.WallboardGroupTile;
import com.sentinel.platform.aggregation.web.dto.WallboardView;
import com.sentinel.platform.aggregation.web.dto.WallboardWorkflowTile;
import com.sentinel.platform.ruleconfig.model.Workflow;
import com.sentinel.platform.ruleconfig.model.WorkflowVersion;
import com.sentinel.platform.ruleconfig.repository.WorkflowRepository;
import com.sentinel.platform.ruleconfig.repository.WorkflowVersionRepository;

@Service
public class WallboardViewService {

    private final AggregationQueryService aggregationQueryService;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowRepository workflowRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WallboardViewService(AggregationQueryService aggregationQueryService,
                                WorkflowVersionRepository workflowVersionRepository,
                                WorkflowRepository workflowRepository,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.aggregationQueryService = aggregationQueryService;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowRepository = workflowRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WallboardView buildWallboard(int limit) {
        List<StageAggregate> aggregates = aggregationQueryService.wallboard(limit).stream()
                .sorted(Comparator.comparing(StageAggregate::getBucketStart).reversed())
                .toList();
        if (aggregates.isEmpty()) {
            return WallboardView.empty();
        }

        Set<Long> versionIds = aggregates.stream()
                .map(StageAggregate::getWorkflowVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, WorkflowVersion> versions = workflowVersionRepository.findAllById(versionIds).stream()
                .collect(Collectors.toMap(WorkflowVersion::getId, v -> v));
        Set<Long> workflowIds = versions.values().stream()
                .map(v -> v.getWorkflow().getId())
                .collect(Collectors.toSet());
        Map<Long, Workflow> workflows = workflowRepository.findAllById(workflowIds).stream()
                .collect(Collectors.toMap(Workflow::getId, wf -> wf));
        Map<Long, Map<String, String>> labelsByVersion = loadGroupLabels(versionIds);

        Map<Long, Map<String, GroupAccumulator>> grouped = new LinkedHashMap<>();
        Instant latestBucket = aggregates.get(0).getBucketStart();
        for (StageAggregate agg : aggregates) {
            latestBucket = agg.getBucketStart().isAfter(latestBucket) ? agg.getBucketStart() : latestBucket;
            Map<String, GroupAccumulator> groups = grouped.computeIfAbsent(agg.getWorkflowVersionId(), id -> new LinkedHashMap<>());
            String hash = agg.getGroupDimHash() != null ? agg.getGroupDimHash() : "default";
            groups.computeIfAbsent(hash, GroupAccumulator::new).accept(agg);
        }

        List<WallboardWorkflowTile> workflowTiles = new ArrayList<>();
        for (Map.Entry<Long, Map<String, GroupAccumulator>> entry : grouped.entrySet()) {
            WorkflowVersion version = versions.get(entry.getKey());
            Workflow workflow = null;
            if (version != null && version.getWorkflow() != null) {
                Long workflowId = version.getWorkflow().getId();
                workflow = workflows.getOrDefault(workflowId, version.getWorkflow());
            }
            String workflowKey = workflow != null ? workflow.getKey() : "unknown";
            String workflowName = workflow != null ? workflow.getName() : workflowKey;

            Map<String, String> labels = labelsByVersion.getOrDefault(entry.getKey(), Map.of());
            List<WallboardGroupTile> groupTiles = entry.getValue().values().stream()
                    .map(acc -> acc.toTile(labels.getOrDefault(acc.groupHash, acc.groupHash)))
                    .toList();
            String workflowStatus = groupTiles.stream()
                    .map(WallboardGroupTile::status)
                    .max(Comparator.comparingInt(this::severityRank))
                    .orElse("green");

            workflowTiles.add(new WallboardWorkflowTile(
                    workflow != null ? String.valueOf(workflow.getId()) : String.valueOf(entry.getKey()),
                    workflowKey,
                    workflowName,
                    workflowStatus,
                    groupTiles
            ));
        }

        return new WallboardView(workflowTiles, latestBucket);
    }

    private Map<Long, Map<String, String>> loadGroupLabels(Set<Long> workflowVersionIds) {
        if (workflowVersionIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = workflowVersionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "select workflow_version_id, group_dims from workflow_run " +
                "where workflow_version_id in (" + placeholders + ") and group_dims is not null";
        Object[] args = workflowVersionIds.toArray();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        Map<Long, Map<String, String>> labels = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long versionId = ((Number) row.get("workflow_version_id")).longValue();
            Map<String, Object> group = parseGroup((String) row.get("group_dims"));
            String hash = hashGroup(group);
            if (hash == null) {
                continue;
            }
            labels.computeIfAbsent(versionId, id -> new LinkedHashMap<>())
                    .putIfAbsent(hash, formatGroupLabel(group));
        }
        return labels;
    }

    private Map<String, Object> parseGroup(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String hashGroup(Map<String, Object> group) {
        if (group == null || group.isEmpty()) {
            return "default";
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
            return "default";
        }
    }

    private String formatGroupLabel(Map<String, Object> group) {
        if (group == null || group.isEmpty()) {
            return "default";
        }
        return group.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .collect(Collectors.joining(" / "));
    }

    private int severityRank(String severity) {
        return switch (severity == null ? "" : severity.toLowerCase()) {
            case "red" -> 3;
            case "amber" -> 2;
            default -> 1;
        };
    }

    private static final class GroupAccumulator {
        private final String groupHash;
        private final Map<String, StageAggregate> latestByNode = new HashMap<>();

        private GroupAccumulator(String groupHash) {
            this.groupHash = groupHash;
        }

        void accept(StageAggregate aggregate) {
            latestByNode.putIfAbsent(aggregate.getNodeKey(), aggregate);
        }

        WallboardGroupTile toTile(String label) {
            int inFlight = latestByNode.values().stream().mapToInt(StageAggregate::getInFlight).sum();
            int late = latestByNode.values().stream().mapToInt(StageAggregate::getLate).sum();
            int failed = latestByNode.values().stream().mapToInt(StageAggregate::getFailed).sum();
            String status = failed > 0 ? "red" : (late > 0 ? "amber" : "green");
            return new WallboardGroupTile(label, groupHash, status, inFlight, late, failed, List.of());
        }
    }
}
