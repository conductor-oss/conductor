-- AgentSpan skill storage (conductor.integrations.ai.enabled). Metadata + package bytes.
CREATE TABLE IF NOT EXISTS skill_metadata (
    owner_id    VARCHAR(255)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    version     VARCHAR(255)  NOT NULL,
    is_latest   BOOLEAN       NOT NULL DEFAULT FALSE,
    detail_json LONGTEXT      NOT NULL,
    created_at  BIGINT,
    updated_at  BIGINT,
    PRIMARY KEY (owner_id, name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_skill_metadata_owner_name ON skill_metadata (owner_id, name);

-- Package bytes are stored Base64-encoded so the value binds uniformly across backends.
CREATE TABLE IF NOT EXISTS skill_package (
    handle     VARCHAR(255) NOT NULL,
    data       LONGTEXT     NOT NULL,
    size_bytes BIGINT,
    created_at BIGINT,
    PRIMARY KEY (handle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
