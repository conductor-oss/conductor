-- Durable workflow message queue: messages pushed to running workflows survive a server
-- restart. A pull claims messages under its name and loop iteration (claimed_by, claim_ordinal)
-- instead of deleting them, so running the same pull again returns the same messages. Claims are
-- deleted when the same pull claims in a later iteration, or when the workflow ends.
CREATE TABLE IF NOT EXISTS workflow_message_queue (
    seq BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    workflow_id VARCHAR(255) NOT NULL,
    json_data TEXT NOT NULL,
    created_on TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    claimed_by VARCHAR(255),
    claim_ordinal BIGINT
);
CREATE INDEX IF NOT EXISTS workflow_message_queue_workflow_id ON workflow_message_queue (workflow_id, seq);

-- The highest loop iteration that has claimed messages, for each pull of a workflow. A run of
-- an older iteration (a stale run resumed after a pause) may read its own claims but not claim
-- new ones.
CREATE TABLE IF NOT EXISTS workflow_message_claim (
    workflow_id VARCHAR(255) NOT NULL,
    claimant VARCHAR(255) NOT NULL,
    ordinal BIGINT NOT NULL,
    PRIMARY KEY (workflow_id, claimant)
);
