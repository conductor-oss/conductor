ALTER TABLE workflow_index
ADD update_time TIMESTAMP WITH TIME ZONE NULL;

UPDATE workflow_index
SET update_time = to_timestamp(json_data->>'updateTime', 'YYYY-MM-DDTHH24:MI:SSZ')::timestamp WITH time zone;

ALTER TABLE workflow_index
ALTER COLUMN update_time SET NOT NULL;