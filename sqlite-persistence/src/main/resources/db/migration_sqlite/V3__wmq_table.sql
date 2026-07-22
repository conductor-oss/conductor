CREATE TABLE workflow_message_queue (
    seq         INTEGER PRIMARY KEY AUTOINCREMENT,
    workflow_id TEXT NOT NULL,
    message_id  TEXT NOT NULL,
    payload     TEXT NOT NULL,
    received_at TEXT NOT NULL
);

CREATE INDEX wmq_workflow_id_idx ON workflow_message_queue(workflow_id);
