UPDATE workflow_index
SET update_time = json_extract(json_data, '$.updateTime')
WHERE json_extract(json_data, '$.updateTime') IS NOT NULL;