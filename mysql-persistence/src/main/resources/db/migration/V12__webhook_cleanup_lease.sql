-- See V17__webhook_cleanup_lease.sql in postgres-persistence for the design.

CREATE TABLE IF NOT EXISTS webhook_cleanup_lease (
    task         VARCHAR(64)  NOT NULL,
    holder       VARCHAR(255) NOT NULL,
    acquired_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP    NOT NULL DEFAULT '1970-01-02 00:00:00',
    PRIMARY KEY (task)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO webhook_cleanup_lease (task, holder, expires_at)
VALUES ('webhook-event-cleanup', 'unclaimed', '1970-01-02 00:00:00');
