-- Mutable secrets store: values are AES-256-GCM encrypted at rest.
-- The encryption key is supplied via CONDUCTOR_SECRETS_ENCRYPTION_KEY env var.
CREATE TABLE IF NOT EXISTS conductor_secrets (
    name       VARCHAR(255) PRIMARY KEY,
    value      TEXT         NOT NULL,
    created_at BIGINT       NOT NULL,
    updated_at BIGINT       NOT NULL
);
