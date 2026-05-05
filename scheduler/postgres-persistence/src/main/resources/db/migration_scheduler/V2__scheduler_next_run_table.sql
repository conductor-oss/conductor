-- Dedicated key-value store for scheduler next-run times.
--
-- Rationale: the scheduler.next_run_time column only supports one entry per schedule name
-- (the table PK). Multi-cron schedules require a separate entry per cron expression, keyed
-- by a JSON payload string (e.g. {"name":"s","cron":"0 0 8 * * ? UTC","id":0}). A standalone
-- table supports both schedule-name keys (single-cron) and arbitrary payload keys (multi-cron)
-- without conflating timing state with the schedule definition row.

CREATE TABLE IF NOT EXISTS scheduler_next_run (
    key         VARCHAR(512) NOT NULL,
    epoch_millis BIGINT       NOT NULL,
    PRIMARY KEY (key)
);
