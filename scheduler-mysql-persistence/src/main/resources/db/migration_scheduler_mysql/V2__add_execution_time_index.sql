-- Add execution_time column to scheduler_execution for efficient ordering.
-- Previously getExecutionRecords ordered by json_extract(json_data, '$.executionTime')
-- which cannot use a standard index. The new column is indexed and populated on every
-- INSERT/UPDATE via the application layer.

ALTER TABLE scheduler_execution ADD COLUMN execution_time BIGINT;

-- Backfill existing rows from json_data.
UPDATE scheduler_execution
SET execution_time = json_extract(json_data, '$.executionTime')
WHERE execution_time IS NULL;

CREATE INDEX scheduler_execution_execution_time_idx
    ON scheduler_execution (execution_time);

-- Drop the unused archival table. It was defined in V1 to mirror the Orkes schema but
-- was never written to by the OSS DAO. All execution history lives in scheduler_execution.
DROP TABLE IF EXISTS workflow_scheduled_executions;
