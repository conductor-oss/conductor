-- AgentSpan skill storage (conductor.integrations.ai.enabled). Metadata + package bytes.
CREATE TABLE IF NOT EXISTS skill_metadata (
    name        VARCHAR(255)  NOT NULL,
    version     VARCHAR(255)  NOT NULL,
    is_latest   BOOLEAN       NOT NULL DEFAULT 0,
    detail_json TEXT          NOT NULL,
    created_at  BIGINT,
    updated_at  BIGINT,
    PRIMARY KEY (name, version)
);

-- Package bytes are stored Base64-encoded so the value binds uniformly across backends.
CREATE TABLE IF NOT EXISTS skill_package (
    handle     VARCHAR(255) NOT NULL PRIMARY KEY,
    data       TEXT         NOT NULL,
    size_bytes BIGINT,
    created_at BIGINT
);
