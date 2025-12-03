CREATE TABLE workflow (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    `key` VARCHAR(200) NOT NULL UNIQUE,
    owner VARCHAR(200),
    active_version_id BIGINT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE workflow_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    version_num INT NOT NULL,
    definition_json JSON NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_by VARCHAR(200),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3),
    UNIQUE KEY uq_workflow_version (workflow_id, version_num),
    CONSTRAINT fk_workflow_version_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id)
);

CREATE TABLE workflow_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_version_id BIGINT NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    is_start BOOLEAN NOT NULL DEFAULT FALSE,
    is_terminal BOOLEAN NOT NULL DEFAULT FALSE,
    ordering_policy VARCHAR(50),
    UNIQUE KEY uq_node_per_version (workflow_version_id, node_key),
    CONSTRAINT fk_node_version FOREIGN KEY (workflow_version_id) REFERENCES workflow_version (id)
);

CREATE TABLE workflow_edge (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    from_node_id BIGINT NOT NULL,
    to_node_id BIGINT NOT NULL,
    max_latency_sec INT,
    absolute_deadline VARCHAR(50),
    optional BOOLEAN NOT NULL DEFAULT FALSE,
    severity VARCHAR(20),
    expected_count INT,
    CONSTRAINT fk_edge_from FOREIGN KEY (from_node_id) REFERENCES workflow_node (id),
    CONSTRAINT fk_edge_to FOREIGN KEY (to_node_id) REFERENCES workflow_node (id)
);
