-- Optional back-fill script to populate updateTime historically.
UPDATE workflow_index
SET update_time = to_timestamp(json_data->>'updateTime', 'YYYY-MM-DD"T"HH24:MI:SS.MS')::timestamp AT TIME ZONE '00:00'
WHERE json_data->>'updateTime' IS NOT NULL;