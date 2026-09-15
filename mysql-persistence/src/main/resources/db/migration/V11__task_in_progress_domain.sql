-- Optional domain column for per-domain concurrency limiting (TaskDef.concurrencyLimitScope=DOMAIN).
-- NULL is the legacy no-domain bucket; the default TASK_DEF scope ignores this column.
ALTER TABLE task_in_progress ADD COLUMN domain varchar(255);
