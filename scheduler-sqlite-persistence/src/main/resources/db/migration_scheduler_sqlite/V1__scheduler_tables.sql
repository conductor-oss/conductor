CREATE TABLE IF NOT EXISTS scheduler (
    scheduler_name TEXT PRIMARY KEY,
    workflow_name  TEXT,
    json_data      TEXT NOT NULL,
    next_run_time  INTEGER
);

CREATE TABLE IF NOT EXISTS scheduler_execution (
    execution_id   TEXT PRIMARY KEY,
    schedule_name  TEXT NOT NULL,
    state          TEXT,
    execution_time INTEGER,
    json_data      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS scheduler_execution_sched_time_idx
    ON scheduler_execution (schedule_name, execution_time DESC);
