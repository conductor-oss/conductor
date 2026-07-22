-- See V18__webhook_signature_dedup.sql in postgres-persistence for the design.
--
-- Signature length is bounded by the verifier's chosen header — typically
-- 64-128 chars (hex HMAC-SHA256 + optional version prefix). 512 leaves room.

CREATE TABLE IF NOT EXISTS webhook_signature_dedup (
    webhook_id  VARCHAR(255) NOT NULL,
    signature   VARCHAR(512) NOT NULL,
    seen_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP    NOT NULL DEFAULT '1970-01-02 00:00:00',
    PRIMARY KEY (webhook_id, signature)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_wsd_expires_at ON webhook_signature_dedup(expires_at);
