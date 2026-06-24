-- See V18__webhook_signature_dedup.sql in postgres-persistence for the design.

CREATE TABLE IF NOT EXISTS webhook_signature_dedup (
    webhook_id  VARCHAR(255) NOT NULL,
    signature   TEXT         NOT NULL,
    seen_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (webhook_id, signature)
);

CREATE INDEX IF NOT EXISTS idx_wsd_expires_at ON webhook_signature_dedup(expires_at);
