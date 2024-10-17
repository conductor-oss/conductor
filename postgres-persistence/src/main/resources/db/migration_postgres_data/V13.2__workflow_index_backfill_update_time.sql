-- Optional back-fill script to populate updateTime historically.
UPDATE workflow_index
SET update_time = to_timestamp(json_data->>'updateTime', 'YYYY-MM-DDTHH24:MI:SS.MSZ')::timestamp WITH time zone
WHERE json_data->>'updateTime' IS NOT NULL;