CREATE TABLE IF NOT EXISTS webhook (
    webhook_id  VARCHAR(255) NOT NULL,
    json_data   TEXT NOT NULL,
    modified_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (webhook_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS incoming_webhook_event (
    event_id   VARCHAR(255) NOT NULL,
    json_data  TEXT NOT NULL,
    created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_iwe_created_on ON incoming_webhook_event(created_on);

-- See PostgresWebhookDAO header for why we store target workflows (not pre-computed
-- matchers) and recompute on read.
CREATE TABLE IF NOT EXISTS webhook_target_workflows (
    webhook_id VARCHAR(255) NOT NULL,
    json_data  TEXT NOT NULL,
    PRIMARY KEY (webhook_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 'hash' holds a delimited deterministic key built by WebhookTaskHashing
-- (workflowName;version;taskRef;value1;value2;...) — NOT a fixed-width crypto
-- hash. Length is bounded only by the workflow def + match values.
--
-- KNOWN LATENT BUG: VARCHAR(255) (set here for InnoDB index-prefix headroom)
-- may silently truncate on long keys produced by large workflow defs or
-- multi-value matches. utf8mb4 + InnoDB's 3072-byte PK limit caps a usable
-- single-column varchar prefix at ~768 chars, and there's a second column
-- in this PK. Follow-up: hash the deterministic key to SHA-256 in both
-- write and read sites so every backend can share a fixed varchar(64).
CREATE TABLE IF NOT EXISTS webhook_hash_to_taskid (
    hash    VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (hash, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
