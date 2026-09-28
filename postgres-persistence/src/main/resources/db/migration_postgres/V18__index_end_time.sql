-- The End Time column on the executions and task search pages was sortable, but end_time was
-- never an indexed column, so the query builder dropped the sort and returned an unordered page.
--
-- Defaulting to the epoch rather than allowing NULL keeps ordering identical across engines:
-- SQLite sorts NULL lowest and Postgres sorts it highest, so a nullable column would place
-- still-running executions at opposite ends of an endTime:DESC page depending on the backend.
-- Unfinished rows carry the epoch and land last on DESC, first on ASC, on both. This mirrors the
-- update_time sentinel in V13.1.
--
-- Rows indexed before this migration take the epoch default; V18.1 back-fills them from json_data.
ALTER TABLE workflow_index
    ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT TIMESTAMP WITH TIME ZONE 'epoch';

ALTER TABLE task_index
    ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT TIMESTAMP WITH TIME ZONE 'epoch';

CREATE INDEX IF NOT EXISTS workflow_index_end_time_idx ON workflow_index (end_time);
CREATE INDEX IF NOT EXISTS task_index_end_time_idx ON task_index (end_time);
