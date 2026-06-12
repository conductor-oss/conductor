-- Drop the unused text index on the json_data column
DROP INDEX CONCURRENTLY IF EXISTS workflow_index_json_data_text_idx;
-- Create a new index to enable querying the json by attribute and value
CREATE INDEX CONCURRENTLY IF NOT EXISTS workflow_index_json_data_gin_idx ON workflow_index USING GIN (json_data jsonb_path_ops);

-- Drop the incorrectly created indices on the workflow_index that should be on the task_index table
DROP INDEX CONCURRENTLY IF EXISTS task_index_json_data_json_idx;
DROP INDEX CONCURRENTLY IF EXISTS task_index_json_data_text_idx;
-- Create the full text index on the json_data column of the task_index table
CREATE INDEX CONCURRENTLY IF NOT EXISTS task_index_json_data_fulltext_idx ON task_index USING GIN (jsonb_to_tsvector('english', json_data, '["all"]'));
-- Create a new index to enable querying the json by attribute and value
CREATE INDEX CONCURRENTLY IF NOT EXISTS task_index_json_data_gin_idx ON task_index USING GIN (json_data jsonb_path_ops);
