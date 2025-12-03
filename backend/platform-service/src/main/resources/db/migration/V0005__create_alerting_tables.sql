CREATE TABLE alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    correlation_key VARCHAR(200) NOT NULL,
    workflow_version_id BIGINT NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    runbook_url VARCHAR(500),
    dedupe_key VARCHAR(300) NOT NULL,
    first_triggered_at DATETIME(3) NOT NULL,
    last_triggered_at DATETIME(3) NOT NULL,
    acked_by VARCHAR(200),
    acked_at DATETIME(3),
    suppressed_until DATETIME(3),
    context JSON,
    KEY idx_alert_state (state, severity),
    KEY idx_alert_dedupe (dedupe_key),
    KEY idx_alert_workflow (workflow_version_id, node_key, correlation_key)
);

CREATE TABLE maintenance_window (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NOT NULL,
    scope JSON,
    created_by VARCHAR(200),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(200),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    details JSON,
    KEY idx_audit (entity_type, entity_id, created_at)
);
