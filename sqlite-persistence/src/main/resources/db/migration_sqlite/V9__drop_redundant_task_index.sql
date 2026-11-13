-- The task_id primary key already provides the index needed for task_id lookups.
DROP INDEX IF EXISTS task_index_task_id_idx;
