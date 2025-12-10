package com.sentinel.platform.aggregation.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.sentinel.platform.shared.group.GroupLabelService;
import com.sentinel.platform.shared.time.DateRange;

@Service
public class WallboardViewService {

    private final AggregationQueryService aggregationQueryService;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowRepository workflowRepository;
    private final JdbcTemplate jdbcTemplate;
    private final GroupLabelService groupLabelService;

    public WallboardViewService(AggregationQueryService aggregationQueryService,
                                WorkflowVersionRepository workflowVersionRepository,
                                WorkflowRepository workflowRepository,
                                JdbcTemplate jdbcTemplate,
                                GroupLabelService groupLabelService) {
        this.aggregationQueryService = aggregationQueryService;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowRepository = workflowRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.groupLabelService = groupLabelService;
    }

    @Transactional(readOnly = true)
    public WallboardView buildWallboard(int limit, DateRange dateRange) {
        List<StageAggregate> aggregates = aggregationQueryService.wallboard(limit, dateRange).stream()
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
        Map<Long, Map<String, String>> labelsByVersion = loadGroupLabels(versionIds, dateRange);

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

    private Map<Long, Map<String, String>> loadGroupLabels(Set<Long> workflowVersionIds, DateRange dateRange) {
        if (workflowVersionIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = workflowVersionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "select workflow_version_id, group_dims from workflow_run " +
                "where workflow_version_id in (" + placeholders + ") and group_dims is not null";
        List<Object> args = new ArrayList<>(workflowVersionIds);
        if (dateRange != null && !dateRange.isAllDays()) {
            sql += " and updated_at between ? and ?";
            args.add(java.sql.Timestamp.from(dateRange.start()));
            args.add(java.sql.Timestamp.from(dateRange.end()));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<Long, Map<String, String>> labels = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long versionId = ((Number) row.get("workflow_version_id")).longValue();
            Map<String, Object> group = groupLabelService.parseGroupJson((String) row.get("group_dims"));
            String hash = groupLabelService.hashGroup(group);
            if (hash == null) {
                continue;
            }
            labels.computeIfAbsent(versionId, id -> new LinkedHashMap<>())
                    .putIfAbsent(hash, groupLabelService.formatGroupLabel(group));
        }
        return labels;
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
