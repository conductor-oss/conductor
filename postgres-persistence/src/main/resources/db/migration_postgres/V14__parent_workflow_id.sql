ALTER TABLE workflow_index ADD COLUMN parent_workflow_id VARCHAR(255) NOT NULL DEFAULT '';
CREATE INDEX workflow_index_parent_workflow_id_idx ON workflow_index (parent_workflow_id);
