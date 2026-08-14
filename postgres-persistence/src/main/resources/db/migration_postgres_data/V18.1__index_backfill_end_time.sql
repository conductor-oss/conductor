-- Populate end_time for rows indexed before V18. Terminal executions are never re-indexed, so
-- without this every historical execution would sort as unfinished (the epoch default) forever.
--
-- Note the deliberate differences from V13.2, which back-fills update_time:
--
--   * A direct ::timestamptz cast rather than to_timestamp(..., 'YYYY-MM-DD"T"HH24:MI:SS.MS').
--     The fixed format requires a milliseconds field, and raises `invalid value "Z" for "MS"` on a
--     value without one. A raise here aborts the migration and the server does not start, and
--     conductor.postgres.applyDataMigrations defaults to true, so that would happen unattended.
--     The cast accepts any ISO-8601 form and reads the trailing Z as UTC, so no AT TIME ZONE
--     correction is needed either.
--
--   * A regex guard rather than IS NOT NULL. The cast still raises on a non-timestamp value such
--     as an empty string, and the guard is what makes a malformed row skip the update and keep
--     the epoch instead of failing the whole statement. Postgres only evaluates the SET expression
--     for rows matching WHERE, so no unguarded value is ever cast.
--
-- This is a single unbatched UPDATE over the whole index table. On a large deployment, time it
-- against a copy first, or set applyDataMigrations=false and back-fill in batches out of band.
UPDATE workflow_index
SET end_time = (json_data->>'endTime')::timestamptz
WHERE json_data->>'endTime' ~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}';

UPDATE task_index
SET end_time = (json_data->>'endTime')::timestamptz
WHERE json_data->>'endTime' ~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}';
