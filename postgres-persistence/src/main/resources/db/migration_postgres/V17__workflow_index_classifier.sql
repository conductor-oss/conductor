-- Classifier of the workflow definition an execution was started from (e.g. 'workflow' for a
-- plain workflow, 'agent' for AgentSpan agents). Derived from WorkflowDef.metadata at index time.
-- Rows indexed before this migration keep a NULL classifier and are treated as untagged
-- ('workflow') by the search query builder.
ALTER TABLE workflow_index ADD COLUMN classifier VARCHAR(255);
CREATE INDEX workflow_index_classifier_idx ON workflow_index (classifier, start_time);
