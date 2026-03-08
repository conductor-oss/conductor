-- Scheduler tables for Conductor OSS (MySQL).

CREATE TABLE IF NOT EXISTS scheduler (
    scheduler_name VARCHAR(255) NOT NULL,
    workflow_name  VARCHAR(255) NOT NULL,
    json_data      MEDIUMTEXT   NOT NULL,
    next_run_time  BIGINT,
    PRIMARY KEY (scheduler_name)
);

CREATE INDEX scheduler_workflow_name_idx ON scheduler (workflow_name);
CREATE INDEX scheduler_next_run_time_idx ON scheduler (next_run_time);

CREATE TABLE IF NOT EXISTS scheduler_execution (
    execution_id  VARCHAR(255) NOT NULL,
    schedule_name VARCHAR(255) NOT NULL,
    state         VARCHAR(50)  NOT NULL,
    json_data     MEDIUMTEXT   NOT NULL,
    PRIMARY KEY (execution_id)
);

CREATE INDEX scheduler_execution_schedule_name_idx ON scheduler_execution (schedule_name);
CREATE INDEX scheduler_execution_state_idx ON scheduler_execution (state);

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
    PRIMARY KEY (execution_id)
);

CREATE INDEX workflow_scheduled_executions_schedule_name_idx ON workflow_scheduled_executions (schedule_name);
CREATE INDEX workflow_scheduled_executions_execution_time_idx ON workflow_scheduled_executions (execution_time);
