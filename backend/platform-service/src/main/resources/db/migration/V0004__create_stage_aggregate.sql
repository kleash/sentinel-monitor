CREATE TABLE stage_aggregate (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_version_id BIGINT NOT NULL,
    group_dim_hash VARCHAR(200),
    node_key VARCHAR(100) NOT NULL,
    bucket_start DATETIME(0) NOT NULL,
    in_flight INT NOT NULL DEFAULT 0,
    completed INT NOT NULL DEFAULT 0,
    late INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    avg_latency_ms BIGINT,
    p95_latency_ms BIGINT,
    UNIQUE KEY uq_stage (workflow_version_id, group_dim_hash, node_key, bucket_start),
    KEY idx_stage_agg (workflow_version_id, group_dim_hash, bucket_start),
    CONSTRAINT fk_stageagg_workflow_version FOREIGN KEY (workflow_version_id) REFERENCES workflow_version (id)
);
