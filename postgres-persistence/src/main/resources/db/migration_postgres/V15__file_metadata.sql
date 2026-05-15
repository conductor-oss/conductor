CREATE TABLE IF NOT EXISTS file_metadata (
    file_id                 VARCHAR(255)  NOT NULL PRIMARY KEY,
    file_name               VARCHAR(1024) NOT NULL,
    content_type            VARCHAR(255)  NOT NULL,
    storage_content_hash    VARCHAR(255),
    storage_content_size    BIGINT,
    storage_type            VARCHAR(50)   NOT NULL,
    storage_path            VARCHAR(2048) NOT NULL,
    upload_status           VARCHAR(50)   NOT NULL DEFAULT 'UPLOADING',
    workflow_id             VARCHAR(255)  NOT NULL,
    task_id                 VARCHAR(255),
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_metadata_workflow_id   ON file_metadata (workflow_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_task_id       ON file_metadata (task_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_upload_status ON file_metadata (upload_status);
