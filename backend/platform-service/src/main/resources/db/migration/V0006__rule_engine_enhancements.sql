ALTER TABLE event_occurrence
    ADD COLUMN event_id VARCHAR(200) NULL AFTER id;

CREATE UNIQUE INDEX uq_occurrence_run_event
    ON event_occurrence (workflow_run_id, event_id);

ALTER TABLE expectation
    ADD COLUMN fired_at DATETIME(3) NULL,
    ADD INDEX idx_expectation_status_due (status, due_at);
