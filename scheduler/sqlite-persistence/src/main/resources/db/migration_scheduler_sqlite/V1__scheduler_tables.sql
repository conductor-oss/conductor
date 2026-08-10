-- Scheduler tables for Conductor OSS (SQLite).
-- Schema mirrors Orkes Conductor (table names, column names, json_data pattern).
-- Multi-tenancy (org_id) is an Orkes enterprise feature and is omitted here.

CREATE TABLE IF NOT EXISTS scheduler (
    scheduler_name VARCHAR(255) NOT NULL,
    workflow_name  VARCHAR(255) NOT NULL,
    json_data      TEXT         NOT NULL,
    next_run_time  BIGINT,
    PRIMARY KEY (scheduler_name)
);

CREATE INDEX IF NOT EXISTS scheduler_workflow_name_idx
    ON scheduler (workflow_name);

CREATE INDEX IF NOT EXISTS scheduler_next_run_time_idx
    ON scheduler (next_run_time);

-- Live execution records. json_data holds the full WorkflowScheduleExecution JSON.
-- schedule_name and state are redundant columns kept for efficient SQL queries
-- (OSS has no queue infrastructure to offload pending-execution tracking).
CREATE TABLE IF NOT EXISTS scheduler_execution (
    execution_id  VARCHAR(255) NOT NULL,
    schedule_name VARCHAR(255) NOT NULL,
    state         VARCHAR(50)  NOT NULL,
    json_data     TEXT         NOT NULL,
    PRIMARY KEY (execution_id)
);

CREATE INDEX IF NOT EXISTS scheduler_execution_schedule_name_idx
    ON scheduler_execution (schedule_name);

CREATE INDEX IF NOT EXISTS scheduler_execution_state_idx
    ON scheduler_execution (state);

-- Archival table for completed execution history (mirrors Orkes workflow_scheduled_executions).
-- Rows are moved here from scheduler_execution after completion and pruned to archivalMaxRecords.
CREATE TABLE IF NOT EXISTS workflow_scheduled_executions (
    execution_id           VARCHAR(255) NOT NULL,
    schedule_name          VARCHAR(255) NOT NULL,
    workflow_name          VARCHAR(255),
    workflow_id            VARCHAR(255),
    reason                 VARCHAR(1024),
    stack_trace            TEXT,
    state                  VARCHAR(50)  NOT NULL,
    scheduled_time         BIGINT       NOT NULL,
    execution_time         BIGINT,
    start_workflow_request TEXT,
    PRIMARY KEY (execution_id)
);

CREATE INDEX IF NOT EXISTS workflow_scheduled_executions_schedule_name_idx
    ON workflow_scheduled_executions (schedule_name);

CREATE INDEX IF NOT EXISTS workflow_scheduled_executions_execution_time_idx
    ON workflow_scheduled_executions (execution_time);
