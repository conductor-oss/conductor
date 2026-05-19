-- Webhook config storage. JSON document keyed by webhook id.
CREATE TABLE IF NOT EXISTS webhook (
    webhook_id  VARCHAR(255) NOT NULL PRIMARY KEY,
    json_data   TEXT NOT NULL,
    modified_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Incoming webhook event payloads, awaiting worker dispatch.
CREATE TABLE IF NOT EXISTS incoming_webhook_event (
    event_id   VARCHAR(255) NOT NULL PRIMARY KEY,
    json_data  TEXT NOT NULL,
    created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_iwe_created_on ON incoming_webhook_event(created_on);

-- Target workflow versions for a webhook. See PostgresWebhookDAO header for
-- why we store targets (not pre-computed matchers) and recompute on read.
CREATE TABLE IF NOT EXISTS webhook_target_workflows (
    webhook_id VARCHAR(255) NOT NULL PRIMARY KEY,
    json_data  TEXT NOT NULL
);

-- Hash-indexed waiting tasks.
CREATE TABLE IF NOT EXISTS webhook_hash_to_taskid (
    hash    TEXT NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (hash, task_id)
);
