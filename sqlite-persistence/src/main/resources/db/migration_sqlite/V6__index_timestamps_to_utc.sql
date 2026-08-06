-- Issue #1497: start_time/update_time were written with java.sql.Timestamp.toString(), which
-- renders in the JVM default zone, while searches rendered their bound in UTC. Rewrite existing
-- rows as UTC so both sides agree. SQLite's 'utc' modifier reads the value as local time and
-- shifts it using the host tz database, so each row gets the DST offset in force at its own
-- timestamp. No-op on UTC hosts and on empty tables.
--
-- COALESCE is load-bearing: strftime returns NULL for an unparseable value and both columns are
-- NOT NULL, so without it one malformed row would abort the migration and the server would not
-- boot. Such rows are left untouched instead.
--
-- Known limitation: a DB written under zone A and migrated under zone B is off by (B - A). Not
-- fixable in SQL.
UPDATE workflow_index SET
  start_time  = COALESCE(strftime('%Y-%m-%d %H:%M:%f', start_time,  'utc'), start_time),
  update_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', update_time, 'utc'), update_time);

UPDATE task_index SET
  start_time  = COALESCE(strftime('%Y-%m-%d %H:%M:%f', start_time,  'utc'), start_time),
  update_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', update_time, 'utc'), update_time);
