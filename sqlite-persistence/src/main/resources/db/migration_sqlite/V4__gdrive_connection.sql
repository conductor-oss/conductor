CREATE TABLE IF NOT EXISTS gdrive_connection (
    connection_id TEXT PRIMARY KEY,
    account_name TEXT NOT NULL,
    oauth_token_json TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
