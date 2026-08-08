-- Signature-dedup store for webhook replay protection. A signed event whose
-- (webhook_id, signature) pair lands in this table within the dedup window
-- is rejected as a replay.
--
-- The row is inserted post-verification; primary-key conflict means we've
-- already accepted this exact signed event recently. expires_at is set by
-- the application (verifier-specific TTL, default ~5 min). The cleanup job
-- prunes expired rows on the same cron as incoming_webhook_event.

CREATE TABLE webhook_signature_dedup (
    webhook_id  varchar(255) NOT NULL,
    signature   TEXT         NOT NULL,
    seen_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (webhook_id, signature)
);

CREATE INDEX idx_wsd_expires_at ON webhook_signature_dedup(expires_at);
