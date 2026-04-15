DROP INDEX IF EXISTS combo_queue_message;

CREATE INDEX combo_queue_message ON queue_message (queue_name,priority,popped,deliver_on,created_on);