-- Webhook config storage. JSON document keyed by webhook id.
CREATE TABLE webhook (
    webhook_id varchar(255) PRIMARY KEY,
    json_data TEXT NOT NULL,
    modified_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Incoming webhook event payloads, awaiting worker dispatch.
CREATE TABLE incoming_webhook_event (
    event_id varchar(255) PRIMARY KEY,
    json_data TEXT NOT NULL,
    created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_iwe_created_on ON incoming_webhook_event(created_on);

-- Target workflow versions for a webhook. Stores the override snapshot taken
-- at config-create time (preserves expression evaluation). Match criteria are
-- NOT pre-computed; the DAO looks them up fresh from MetadataDAO on read so
-- WorkflowDef updates take effect without re-registering the webhook.
CREATE TABLE webhook_target_workflows (
    webhook_id varchar(255) PRIMARY KEY,
    json_data TEXT NOT NULL
);

-- Hash-indexed waiting tasks. Populated by Webhook.start() when a workflow
-- reaches a WAIT_FOR_WEBHOOK task. Worker looks up matching task ids by hash
-- and completes them when a matching event arrives.
CREATE TABLE webhook_hash_to_taskid (
    hash TEXT NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (hash, task_id)
);
