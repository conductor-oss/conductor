-- use descending priority in the index to conform to queries
DROP INDEX IF EXISTS combo_queue_message;

CREATE INDEX combo_queue_message ON queue_message USING btree (queue_name , priority desc, popped, deliver_on, created_on)