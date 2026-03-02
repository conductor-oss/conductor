-- Scheduler tables for Conductor OSS workflow scheduling feature.
--
-- org_id defaults to 'default' in OSS; the composite primary keys match the
-- Orkes Conductor schema so the two implementations can converge.

CREATE TABLE IF NOT EXISTS workflow_schedule (
    org_id         VARCHAR(255) NOT NULL DEFAULT 'default',
    schedule_name  VARCHAR(255) NOT NULL,
    workflow_name  VARCHAR(255) NOT NULL,
    json_data      TEXT        NOT NULL,
    next_run_time  BIGINT,
    PRIMARY KEY (org_id, schedule_name)
);

CREATE INDEX IF NOT EXISTS workflow_schedule_workflow_name_idx
    ON workflow_schedule (workflow_name);

CREATE INDEX IF NOT EXISTS workflow_schedule_next_run_time_idx
    ON workflow_schedule (next_run_time);

CREATE TABLE IF NOT EXISTS workflow_schedule_execution (
    org_id         VARCHAR(255) NOT NULL DEFAULT 'default',
    execution_id   VARCHAR(255) NOT NULL,
    schedule_name  VARCHAR(255) NOT NULL,
    workflow_id    VARCHAR(255),
    scheduled_time BIGINT      NOT NULL,
    execution_time BIGINT      NOT NULL,
    state          VARCHAR(50) NOT NULL,  -- POLLED, EXECUTED, FAILED
    reason         TEXT,
    zone_id        VARCHAR(100),
    PRIMARY KEY (org_id, execution_id)
);

CREATE INDEX IF NOT EXISTS workflow_schedule_execution_schedule_name_idx
    ON workflow_schedule_execution (schedule_name);

CREATE INDEX IF NOT EXISTS workflow_schedule_execution_time_idx
    ON workflow_schedule_execution (execution_time);

CREATE INDEX IF NOT EXISTS workflow_schedule_execution_state_idx
    ON workflow_schedule_execution (state);
