-- The End Time column on the executions and task search pages was sortable, but end_time was
-- never an indexed column, so the query builder dropped the sort and returned an unordered page.
--
-- Defaulting to the epoch rather than allowing NULL keeps ordering identical across engines:
-- SQLite sorts NULL lowest and Postgres sorts it highest, so a nullable column would place
-- still-running executions at opposite ends of an endTime:DESC page depending on the backend.
-- Unfinished rows carry the epoch and land last on DESC, first on ASC, on both. This mirrors the
-- update_time sentinel in V1.
ALTER TABLE workflow_index ADD COLUMN end_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00.000';
ALTER TABLE task_index ADD COLUMN end_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00.000';

CREATE INDEX workflow_index_end_time_idx ON workflow_index(end_time);
CREATE INDEX task_index_end_time_idx ON task_index(end_time);

-- Back-fill from json_data, which already holds the instant as an ISO-8601 string, using the
-- same idiom as V6. json_valid() skips rows with unreadable JSON, which would otherwise abort
-- the statement, and COALESCE keeps the epoch default where the field is absent (a workflow
-- that has not finished) since the column is NOT NULL.
UPDATE workflow_index
SET end_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.endTime')), end_time)
WHERE json_valid(json_data);

UPDATE task_index
SET end_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.endTime')), end_time)
WHERE json_valid(json_data);
