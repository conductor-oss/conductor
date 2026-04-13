-- flyway:nonTransactional
DROP INDEX IF EXISTS combo_queue_message;

CREATE INDEX combo_queue_message
    ON queue_message (queue_name, deliver_on ASC, priority DESC, created_on ASC)
    INCLUDE (message_id)
    WHERE popped = false;
