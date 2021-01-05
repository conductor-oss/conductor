-- 1) queue_message
DROP INDEX IF EXISTS unique_queue_name_message_id;
ALTER TABLE queue_message DROP CONSTRAINT IF EXISTS queue_message_pkey;
ALTER TABLE queue_message DROP COLUMN IF EXISTS id;
ALTER TABLE queue_message ADD PRIMARY KEY (queue_name, message_id);

-- 2) queue
DROP INDEX IF EXISTS unique_queue_name;
ALTER TABLE queue DROP CONSTRAINT IF EXISTS queue_pkey;
ALTER TABLE queue DROP COLUMN IF EXISTS id;
ALTER TABLE queue ADD PRIMARY KEY (queue_name);

-- 3) workflow_to_task
DROP INDEX IF EXISTS unique_workflow_to_task_id;
ALTER TABLE workflow_to_task DROP CONSTRAINT IF EXISTS workflow_to_task_pkey;
ALTER TABLE workflow_to_task DROP COLUMN IF EXISTS id;
ALTER TABLE workflow_to_task ADD PRIMARY KEY (workflow_id, task_id);

-- 4) workflow_pending
DROP INDEX IF EXISTS unique_workflow_type_workflow_id;
ALTER TABLE workflow_pending DROP CONSTRAINT IF EXISTS workflow_pending_pkey;
ALTER TABLE workflow_pending DROP COLUMN IF EXISTS id;
ALTER TABLE workflow_pending ADD PRIMARY KEY (workflow_type, workflow_id);

-- 5) workflow_def_to_workflow
DROP INDEX IF EXISTS unique_workflow_def_date_str;
ALTER TABLE workflow_def_to_workflow DROP CONSTRAINT IF EXISTS workflow_def_to_workflow_pkey;
ALTER TABLE workflow_def_to_workflow DROP COLUMN IF EXISTS id;
ALTER TABLE workflow_def_to_workflow ADD PRIMARY KEY (workflow_def, date_str, workflow_id);

-- 6) workflow
DROP INDEX IF EXISTS unique_workflow_id;
ALTER TABLE workflow DROP CONSTRAINT IF EXISTS workflow_pkey;
ALTER TABLE workflow DROP COLUMN IF EXISTS id;
ALTER TABLE workflow ADD PRIMARY KEY (workflow_id);

-- 7) task
DROP INDEX IF EXISTS unique_task_id;
ALTER TABLE task DROP CONSTRAINT IF EXISTS task_pkey;
ALTER TABLE task DROP COLUMN IF EXISTS id;
ALTER TABLE task ADD PRIMARY KEY (task_id);

-- 8) task_in_progress
DROP INDEX IF EXISTS unique_task_def_task_id1;
ALTER TABLE task_in_progress DROP CONSTRAINT IF EXISTS task_in_progress_pkey;
ALTER TABLE task_in_progress DROP COLUMN IF EXISTS id;
ALTER TABLE task_in_progress ADD PRIMARY KEY (task_def_name, task_id);

-- 9) task_scheduled
DROP INDEX IF EXISTS unique_workflow_id_task_key;
ALTER TABLE task_scheduled DROP CONSTRAINT IF EXISTS task_scheduled_pkey;
ALTER TABLE task_scheduled DROP COLUMN IF EXISTS id;
ALTER TABLE task_scheduled ADD PRIMARY KEY (workflow_id, task_key);

-- 10) poll_data
DROP INDEX IF EXISTS unique_poll_data;
ALTER TABLE poll_data DROP CONSTRAINT IF EXISTS poll_data_pkey;
ALTER TABLE poll_data DROP COLUMN IF EXISTS id;
ALTER TABLE poll_data ADD PRIMARY KEY (queue_name, domain);

-- 11) event_execution
DROP INDEX IF EXISTS unique_event_execution;
ALTER TABLE event_execution DROP CONSTRAINT IF EXISTS event_execution_pkey;
ALTER TABLE event_execution DROP COLUMN IF EXISTS id;
ALTER TABLE event_execution ADD PRIMARY KEY (event_handler_name, event_name, execution_id);

-- 12) meta_workflow_def
DROP INDEX IF EXISTS unique_name_version;
ALTER TABLE meta_workflow_def DROP CONSTRAINT IF EXISTS meta_workflow_def_pkey;
ALTER TABLE meta_workflow_def DROP COLUMN IF EXISTS id;
ALTER TABLE meta_workflow_def ADD PRIMARY KEY (name, version);

-- 13) meta_task_def
DROP INDEX IF EXISTS unique_task_def_name;
ALTER TABLE meta_task_def DROP CONSTRAINT IF EXISTS meta_task_def_pkey;
ALTER TABLE meta_task_def DROP COLUMN IF EXISTS id;
ALTER TABLE meta_task_def ADD PRIMARY KEY (name);
