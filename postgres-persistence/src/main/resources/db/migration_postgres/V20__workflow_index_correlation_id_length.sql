-- workflow.correlation_id is varchar(255), but workflow_index.correlation_id was
-- created as varchar(128) in V8. Indexing happens in the same request as the
-- start, so a correlation id of 129-255 characters failed the whole start.
ALTER TABLE workflow_index ALTER COLUMN correlation_id TYPE VARCHAR(255);
