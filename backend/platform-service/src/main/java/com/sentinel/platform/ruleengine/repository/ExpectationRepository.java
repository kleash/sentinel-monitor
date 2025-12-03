package com.sentinel.platform.ruleengine.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExpectationRepository {
    private final JdbcTemplate jdbcTemplate;

    public ExpectationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExpectationRow> claimDuePending(int limit, String owner) {
        Instant now = Instant.now();
        List<ExpectationRow> rows = jdbcTemplate.query("""
                        select id, workflow_run_id, from_node_key, to_node_key, due_at, severity
                        from expectation
                        where status = 'pending' and due_at <= ?
                        order by due_at
                        limit ?
                        """,
                (rs, rowNum) -> new ExpectationRow(
                        rs.getLong("id"),
                        rs.getLong("workflow_run_id"),
                        rs.getString("from_node_key"),
                        rs.getString("to_node_key"),
                        rs.getTimestamp("due_at").toInstant(),
                        rs.getString("severity")
                ),
                Timestamp.from(now.atZone(ZoneOffset.UTC).toInstant()),
                limit);
        markFired(rows.stream().map(ExpectationRow::id).toList(), owner, now);
        return rows;
    }

    private void markFired(List<Long> ids, String lockOwner, Instant firedAt) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Object> params = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        jdbcTemplate.update(
                "update expectation set status = 'fired', lock_owner = ?, fired_at = ? where id in (" + placeholders + ")",
                concat(lockOwner, Timestamp.from(firedAt.atZone(ZoneOffset.UTC).toInstant()), params).toArray());
    }

    private List<Object> concat(Object first, Object second, List<?> rest) {
        List<Object> params = new java.util.ArrayList<>();
        params.add(first);
        params.add(second);
        params.addAll(rest);
        return params;
    }

    public record ExpectationRow(long id, long workflowRunId, String fromNodeKey, String toNodeKey, Instant dueAt, String severity) {}
}
