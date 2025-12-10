package com.sentinel.platform.ruleengine.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sentinel.platform.ruleconfig.model.Workflow;
import com.sentinel.platform.ruleconfig.repository.WorkflowRepository;
import com.sentinel.platform.shared.group.GroupLabelService;
import com.sentinel.platform.shared.time.DateRange;
import com.sentinel.platform.ruleengine.web.dto.WorkflowInstancePage;
import com.sentinel.platform.ruleengine.web.dto.WorkflowInstanceView;

@Service
public class WorkflowInstanceQueryService {

    private final WorkflowRepository workflowRepository;
    private final JdbcTemplate jdbcTemplate;
    private final GroupLabelService groupLabelService;

    public WorkflowInstanceQueryService(WorkflowRepository workflowRepository,
                                        JdbcTemplate jdbcTemplate,
                                        GroupLabelService groupLabelService) {
        this.workflowRepository = workflowRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.groupLabelService = groupLabelService;
    }

    public WorkflowInstancePage findInstances(String workflowKey,
                                              String groupHash,
                                              String stage,
                                              DateRange dateRange,
                                              int page,
                                              int size) {
        if (size <= 0) {
            size = 20;
        }
        int offset = Math.max(page, 0) * size;

        Workflow workflow = workflowRepository.findByKey(workflowKey).orElse(null);
        if (workflow == null || workflow.getActiveVersionId() == null) {
            return WorkflowInstancePage.empty();
        }

        int fetchLimit = Math.max(size * 3, size + 1);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select wr.id as run_id, wr.workflow_version_id, wr.correlation_key, wr.status, wr.started_at, wr.updated_at, wr.last_node_key, wr.group_dims,
                       last_ev.node_key as last_event_node, last_ev.received_at as last_received_at, last_ev.event_time_utc as last_event_time,
                       last_ev.is_late as last_event_late, last_ev.order_violation as last_event_order_violation
                from workflow_run wr
                left join event_occurrence last_ev on last_ev.id = (
                    select eo.id from event_occurrence eo
                    where eo.workflow_run_id = wr.id
                    order by eo.received_at desc
                    limit 1
                )
                where wr.workflow_version_id = ?
                """);
        args.add(workflow.getActiveVersionId());

        boolean boundByDate = dateRange != null && !dateRange.isAllDays();
        if (boundByDate) {
            sql.append(" and wr.updated_at between ? and ?");
            args.add(Timestamp.from(dateRange.start()));
            args.add(Timestamp.from(dateRange.end()));
        }
        if (stage != null && !stage.isBlank()) {
            sql.append(" and exists (select 1 from event_occurrence eo where eo.workflow_run_id = wr.id and eo.node_key = ?");
            args.add(stage);
            if (boundByDate) {
                sql.append(" and eo.received_at between ? and ?");
                args.add(Timestamp.from(dateRange.start()));
                args.add(Timestamp.from(dateRange.end()));
            }
            sql.append(")");
        }
        sql.append(" order by wr.updated_at desc limit ? offset ?");
        args.add(fetchLimit);
        args.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<WorkflowInstanceView> mapped = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> group = groupLabelService.parseGroupJson((String) row.get("group_dims"));
            String hash = groupLabelService.hashGroup(group);
            if (groupHash != null && !groupHash.equals(hash)) {
                continue;
            }
            String label = groupLabelService.formatGroupLabel(group);
            mapped.add(toView(row, workflow, hash, label));
            if (mapped.size() > fetchLimit) {
                break;
            }
        }
        boolean hasMore = mapped.size() > size || rows.size() >= fetchLimit;
        if (mapped.size() > size) {
            mapped = mapped.subList(0, size);
        }
        return new WorkflowInstancePage(mapped, page, size, hasMore);
    }

    private WorkflowInstanceView toView(Map<String, Object> row, Workflow workflow, String groupHash, String groupLabel) {
        Instant startedAt = toInstant(row.get("started_at"));
        Instant updatedAt = toInstant(row.get("updated_at"));
        Instant lastEventAt = toInstant(row.get("last_received_at"));
        String currentStage = row.get("last_node_key") != null ? row.get("last_node_key").toString() : null;
        if (currentStage == null && row.get("last_event_node") != null) {
            currentStage = row.get("last_event_node").toString();
        }
        boolean late = asBoolean(row.get("last_event_late"));
        boolean orderViolation = asBoolean(row.get("last_event_order_violation"));
        return new WorkflowInstanceView(
                String.valueOf(row.get("correlation_key")),
                row.get("workflow_version_id") != null ? ((Number) row.get("workflow_version_id")).longValue() : null,
                workflow.getId() != null ? String.valueOf(workflow.getId()) : null,
                workflow.getKey(),
                workflow.getName(),
                String.valueOf(row.get("status")),
                currentStage,
                startedAt,
                updatedAt,
                lastEventAt,
                groupHash,
                groupLabel,
                late,
                orderViolation
        );
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number num) {
            return num.intValue() != 0;
        }
        return false;
    }
}
