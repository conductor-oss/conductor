-- Scheduler tables for Conductor OSS (MySQL).
-- Schema mirrors Orkes Conductor (table names, column names, json_data pattern).
-- Multi-tenancy (org_id) is an Orkes enterprise feature and is omitted here.
-- Must stay idempotent (IF NOT EXISTS, indexes inline as KEY): existing
-- deployments may already have these tables.

CREATE TABLE IF NOT EXISTS scheduler (
    scheduler_name VARCHAR(255) NOT NULL,
    workflow_name  VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    json_data      MEDIUMTEXT   NOT NULL,
    next_run_time  BIGINT,
    PRIMARY KEY (scheduler_name),
    KEY scheduler_workflow_name_idx (workflow_name),
    KEY scheduler_next_run_time_idx (next_run_time)
);

-- Live execution records. json_data holds the full WorkflowScheduleExecution JSON.
-- schedule_name and state are redundant columns kept for efficient SQL queries
-- (OSS has no queue infrastructure to offload pending-execution tracking).
CREATE TABLE IF NOT EXISTS scheduler_execution (
    execution_id  VARCHAR(255) NOT NULL,
    schedule_name VARCHAR(255) NOT NULL,
    state         VARCHAR(50)  NOT NULL,
    json_data     MEDIUMTEXT   NOT NULL,
    PRIMARY KEY (execution_id),
    KEY scheduler_execution_schedule_name_idx (schedule_name),
    KEY scheduler_execution_state_idx (state)
);

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
    start_workflow_request MEDIUMTEXT,
    PRIMARY KEY (execution_id),
    KEY workflow_scheduled_executions_schedule_name_idx (schedule_name),
    KEY workflow_scheduled_executions_execution_time_idx (execution_time)
);
