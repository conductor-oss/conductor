-- no longer need separate index if pk is queue_name, message_id
DROP INDEX IF EXISTS unique_queue_name_message_id;

-- remove id primary key
ALTER TABLE queue_message DROP CONSTRAINT IF EXISTS queue_message_pkey;

-- remove id column
ALTER TABLE queue_message DROP COLUMN IF EXISTS id;

-- set primary key to queue_name, message_id
ALTER TABLE queue_message ADD PRIMARY KEY (queue_name, message_id);
