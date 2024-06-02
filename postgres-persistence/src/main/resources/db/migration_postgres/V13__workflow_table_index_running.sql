CREATE INDEX CONCURRENTLY IF NOT EXISTS workflow_status_running_idx
ON workflow (json_data) WHERE (json_data::jsonb ->> 'status') = 'RUNNING';