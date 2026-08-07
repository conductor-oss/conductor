-- Issue #1497: start_time/update_time were written with java.sql.Timestamp.toString(), which
-- renders in the JVM default zone, while searches rendered their bound in UTC. Rewrite existing
-- rows as UTC so both sides agree.
--
-- These columns are derived data: json_data already holds the authoritative instant as an
-- ISO-8601 string with a 'Z' suffix, which SQLite parses natively. So rebuild them from json_data
-- rather than converting the local-time text. No timezone is guessed at any point, which matters
-- more than it looks:
--
--   An earlier version of this migration used SQLite's 'utc' modifier to reinterpret the stored
--   local time. That reads the host tz database, while the bad values were written against the
--   *JVM's bundled* tz database -- and the two disagree whenever the JVM's tzdata is older than
--   the OS's. Observed on JDK 21 (tzdata 2023c) on macOS (tzdata 2026c): Paraguay dropped DST in
--   tzdata 2024b, so the JVM wrote America/Asuncion at -04:00 while SQLite read it back at
--   -03:00, leaving every row an hour off. Reconstructing from json_data has no such coupling.
--
-- This is also idempotent and self-correcting: it computes the same answer no matter what the
-- column currently holds, so a row left wrong by an earlier attempt is repaired.
--
-- Two guards, both load-bearing:
--   json_valid()  -- json_extract() raises "malformed JSON" and aborts the whole statement on a
--                    bad row, which would fail the migration and stop the server from booting.
--   COALESCE()    -- a row whose JSON lacks the field yields NULL, and both columns are NOT NULL.
-- Either way the affected row keeps its existing value instead of taking down startup.
UPDATE workflow_index SET
  start_time  = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.startTime')),  start_time),
  update_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.updateTime')), update_time)
WHERE json_valid(json_data);

UPDATE task_index SET
  start_time  = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.startTime')),  start_time),
  update_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.updateTime')), update_time)
WHERE json_valid(json_data);
