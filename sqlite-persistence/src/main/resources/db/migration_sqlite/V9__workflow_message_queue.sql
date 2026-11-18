-- Durable workflow message queue: messages pushed to running workflows, including native
-- workflow signals, updates and queries, survive a server restart. A pull claims messages under
-- its reference name (claimed_by) instead of deleting them, so running the same pull again returns
-- the same messages. Claims are deleted when the workflow next pulls.
CREATE TABLE IF NOT EXISTS workflow_message_queue (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id VARCHAR(255) NOT NULL,
    workflow_id VARCHAR(255) NOT NULL,
    json_data TEXT NOT NULL,
    created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    claimed_by VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS workflow_message_queue_workflow_id ON workflow_message_queue (workflow_id, seq);

-- The highest loop iteration that has claimed messages for each workflow. A pull from an older
-- iteration (a stale run resumed after a pause) may read its own claims but not claim new ones.
CREATE TABLE IF NOT EXISTS workflow_message_claim (
    workflow_id VARCHAR(255) NOT NULL PRIMARY KEY,
    ordinal BIGINT NOT NULL
);
