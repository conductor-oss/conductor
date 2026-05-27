-- See V17__webhook_cleanup_lease.sql in postgres-persistence for the design.
-- Sqlite-typical deployments are single-instance and don't strictly need
-- this, but the cleanup-job code uses the lease unconditionally for symmetry
-- across backends.

CREATE TABLE IF NOT EXISTS webhook_cleanup_lease (
    task         VARCHAR(64)  NOT NULL PRIMARY KEY,
    holder       VARCHAR(255) NOT NULL,
    acquired_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP    NOT NULL
);

INSERT OR IGNORE INTO webhook_cleanup_lease (task, holder, expires_at)
VALUES ('webhook-event-cleanup', 'unclaimed', '1970-01-01T00:00:00');
