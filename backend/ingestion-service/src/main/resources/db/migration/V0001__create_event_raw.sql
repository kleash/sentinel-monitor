-- Baseline for ingestion raw events aligned to architecture.md
CREATE TABLE event_raw (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_event_id VARCHAR(200) NOT NULL,
    source_system VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    workflow_key VARCHAR(100),
    correlation_key VARCHAR(200) NOT NULL,
    group_dims JSON,
    event_time_utc DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    payload JSON,
    ingest_status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_event_source (source_system, source_event_id),
    KEY idx_event_time (event_type, event_time_utc),
    KEY idx_corr (workflow_key, correlation_key),
    KEY idx_received (received_at)
) ENGINE=InnoDB
  DEFAULT CHARSET = utf8mb4
  PARTITION BY RANGE (TO_DAYS(received_at)) (
      PARTITION p_max VALUES LESS THAN (MAXVALUE)
  );

-- Note: partition rollover/archival to be managed via scheduled job; hot retention target is 30 days.
