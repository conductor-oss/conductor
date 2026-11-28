-- The task_id primary key already provides the index needed for task_id lookups.
-- DROP INDEX CONCURRENTLY avoids blocking writes while removing the redundant index.
DROP INDEX CONCURRENTLY IF EXISTS task_index_task_id_idx;
