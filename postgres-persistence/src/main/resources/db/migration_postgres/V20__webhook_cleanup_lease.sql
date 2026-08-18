-- Shared lease for the WebhookCleanupJob so only one conductor instance per
-- cluster runs the deletion cron per tick. Without this, every horizontally-
-- scaled replica hits the incoming_webhook_event table simultaneously on the
-- cron — thundering herd against an OLTP table.
--
-- Acquisition is a single conditional UPDATE on a pre-seeded row. The TTL
-- bounds how long a crashed lease holder can block other instances.

CREATE TABLE webhook_cleanup_lease (
    task         VARCHAR(64)  NOT NULL PRIMARY KEY,
    holder       VARCHAR(255) NOT NULL,
    acquired_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP    NOT NULL
);

INSERT INTO webhook_cleanup_lease (task, holder, expires_at)
VALUES ('webhook-event-cleanup', 'unclaimed', TIMESTAMP 'epoch')
ON CONFLICT (task) DO NOTHING;
