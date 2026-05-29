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

-- expires_at is seeded as INTEGER 0 (not a text literal): the xerial sqlite-jdbc
-- driver stores java.sql.Timestamp as INTEGER (epoch millis), and SQLite's
-- type-comparison rule says INTEGER < TEXT. A text seed would always compare
-- greater than the cleanup job's now-Timestamp, so its "expires_at < ?" check
-- would never match — the lease could never be acquired and the cleanup job
-- would no-op forever.
INSERT OR IGNORE INTO webhook_cleanup_lease (task, holder, expires_at)
VALUES ('webhook-event-cleanup', 'unclaimed', 0);
