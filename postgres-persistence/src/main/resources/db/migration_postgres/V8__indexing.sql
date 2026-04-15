CREATE TABLE workflow_index (
  workflow_id VARCHAR(255) NOT NULL,
  correlation_id VARCHAR(128) NULL,
  workflow_type VARCHAR(128) NOT NULL,
  start_time TIMESTAMP WITH TIME ZONE NOT NULL,
  status VARCHAR(32) NOT NULL,
  json_data JSONB NOT NULL,
  PRIMARY KEY (workflow_id)
);

CREATE INDEX workflow_index_correlation_id_idx ON workflow_index (correlation_id);
CREATE INDEX workflow_index_workflow_type_idx ON workflow_index (workflow_type);
CREATE INDEX workflow_index_start_time_idx ON workflow_index (start_time);
CREATE INDEX workflow_index_status_idx ON workflow_index (status);
CREATE INDEX workflow_index_json_data_json_idx ON workflow_index USING gin(jsonb_to_tsvector('english', json_data, '["all"]'));
CREATE INDEX workflow_index_json_data_text_idx ON workflow_index USING gin(to_tsvector('english', json_data::text));

CREATE TABLE task_index (
  task_id VARCHAR(255) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_def_name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  start_time TIMESTAMP WITH TIME ZONE NOT NULL,
  update_time TIMESTAMP WITH TIME ZONE NOT NULL,
  workflow_type VARCHAR(128) NOT NULL,
  json_data JSONB NOT NULL,
  PRIMARY KEY (task_id)
);

CREATE INDEX task_index_task_id_idx ON task_index (task_id);
CREATE INDEX task_index_task_type_idx ON task_index (task_type);
CREATE INDEX task_index_task_def_name_idx ON task_index (task_def_name);
CREATE INDEX task_index_status_idx ON task_index (status);
CREATE INDEX task_index_update_time_idx ON task_index (update_time);
CREATE INDEX task_index_workflow_type_idx ON task_index (workflow_type);
CREATE INDEX task_index_json_data_json_idx ON workflow_index USING gin(jsonb_to_tsvector('english', json_data, '["all"]'));
CREATE INDEX task_index_json_data_text_idx ON workflow_index USING gin(to_tsvector('english', json_data::text));

CREATE TABLE task_execution_logs (
    log_id SERIAL,
    task_id varchar(255) NOT NULL,
    log TEXT NOT NULL,
    created_time TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (log_id)
);

CREATE INDEX task_execution_logs_task_id_idx ON task_execution_logs (task_id);
