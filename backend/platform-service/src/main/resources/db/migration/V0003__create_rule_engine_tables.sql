CREATE TABLE workflow_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_version_id BIGINT NOT NULL,
    correlation_key VARCHAR(200) NOT NULL,
    group_dims JSON,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    last_node_key VARCHAR(100),
    CONSTRAINT fk_run_workflow_version FOREIGN KEY (workflow_version_id) REFERENCES workflow_version (id),
    KEY idx_run_corr (workflow_version_id, correlation_key),
    KEY idx_status_updated (status, updated_at)
);

CREATE TABLE event_occurrence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    event_time_utc DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    payload_excerpt VARCHAR(1000),
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    order_violation BOOLEAN NOT NULL DEFAULT FALSE,
    raw_event_id BIGINT,
    CONSTRAINT fk_occurrence_run FOREIGN KEY (workflow_run_id) REFERENCES workflow_run (id),
    KEY idx_run_node (workflow_run_id, node_key),
    KEY idx_occ_received (received_at)
);

CREATE TABLE expectation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT NOT NULL,
    from_node_key VARCHAR(100) NOT NULL,
    to_node_key VARCHAR(100) NOT NULL,
    due_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    lock_owner VARCHAR(100),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_expectation_run FOREIGN KEY (workflow_run_id) REFERENCES workflow_run (id),
    KEY idx_due_status (status, due_at),
    KEY idx_run_to (workflow_run_id, to_node_key)
);
