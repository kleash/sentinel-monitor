ALTER TABLE alert
    DROP COLUMN runbook_url,
    DROP COLUMN context;

ALTER TABLE stage_aggregate
    DROP COLUMN avg_latency_ms,
    DROP COLUMN p95_latency_ms;

DROP TABLE IF EXISTS maintenance_window;
