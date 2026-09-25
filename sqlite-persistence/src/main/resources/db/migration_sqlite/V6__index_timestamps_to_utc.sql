-- Issue #1497: start_time/update_time were stored in the JVM's local zone while searches used
-- UTC, so time-range filters missed recent rows. Rewrite both columns in UTC.
--
-- json_data already holds the instant as an ISO-8601 string, so read it from there rather than
-- converting the stored local text. No timezone is involved.
--
-- json_valid() skips rows with unreadable JSON, which would otherwise abort the statement.
-- COALESCE keeps the current value when the field is missing, since the columns are NOT NULL.
UPDATE workflow_index SET
  start_time  = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.startTime')),  start_time),
  update_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.updateTime')), update_time)
WHERE json_valid(json_data);

UPDATE task_index SET
  start_time  = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.startTime')),  start_time),
  update_time = COALESCE(strftime('%Y-%m-%d %H:%M:%f', json_extract(json_data, '$.updateTime')), update_time)
WHERE json_valid(json_data);
