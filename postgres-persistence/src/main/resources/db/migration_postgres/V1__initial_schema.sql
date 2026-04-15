
-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR METADATA DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE meta_event_handler (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  name varchar(255) NOT NULL,
  event varchar(255) NOT NULL,
  active boolean NOT NULL,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX event_handler_name_index ON meta_event_handler (name);
CREATE INDEX event_handler_event_index ON meta_event_handler (event);

CREATE TABLE meta_task_def (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  name varchar(255) NOT NULL,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_task_def_name ON meta_task_def (name);

CREATE TABLE meta_workflow_def (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  name varchar(255) NOT NULL,
  version int NOT NULL,
  latest_version int NOT NULL DEFAULT 0,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_name_version ON meta_workflow_def (name,version);
CREATE INDEX workflow_def_name_index ON meta_workflow_def (name);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR EXECUTION DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE event_execution (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  event_handler_name varchar(255) NOT NULL,
  event_name varchar(255) NOT NULL,
  message_id varchar(255) NOT NULL,
  execution_id varchar(255) NOT NULL,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_event_execution ON event_execution (event_handler_name,event_name,message_id);

CREATE TABLE poll_data (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  queue_name varchar(255) NOT NULL,
  domain varchar(255) NOT NULL,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_poll_data ON poll_data (queue_name,domain);
CREATE INDEX ON poll_data (queue_name);

CREATE TABLE task_scheduled (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_id varchar(255) NOT NULL,
  task_key varchar(255) NOT NULL,
  task_id varchar(255) NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_workflow_id_task_key ON task_scheduled (workflow_id,task_key);

CREATE TABLE task_in_progress (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  task_def_name varchar(255) NOT NULL,
  task_id varchar(255) NOT NULL,
  workflow_id varchar(255) NOT NULL,
  in_progress_status boolean NOT NULL DEFAULT false,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_task_def_task_id1 ON task_in_progress (task_def_name,task_id);

CREATE TABLE task (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  task_id varchar(255) NOT NULL,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_task_id ON task (task_id);

CREATE TABLE workflow (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_id varchar(255) NOT NULL,
  correlation_id varchar(255),
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_workflow_id ON workflow (workflow_id);

CREATE TABLE workflow_def_to_workflow (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_def varchar(255) NOT NULL,
  date_str varchar(60),
  workflow_id varchar(255) NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_workflow_def_date_str ON workflow_def_to_workflow (workflow_def,date_str,workflow_id);

CREATE TABLE workflow_pending (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_type varchar(255) NOT NULL,
  workflow_id varchar(255) NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_workflow_type_workflow_id ON workflow_pending (workflow_type,workflow_id);
CREATE INDEX workflow_type_index ON workflow_pending (workflow_type);

CREATE TABLE workflow_to_task (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_id varchar(255) NOT NULL,
  task_id varchar(255) NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_workflow_to_task_id ON workflow_to_task (workflow_id,task_id);
CREATE INDEX workflow_id_index ON workflow_to_task (workflow_id);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR QUEUE DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE queue (
  id SERIAL,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  queue_name varchar(255) NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_queue_name ON queue (queue_name);

CREATE TABLE queue_message (
  id SERIAL,
  created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deliver_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  queue_name varchar(255) NOT NULL,
  message_id varchar(255) NOT NULL,
  priority integer DEFAULT 0,
  popped boolean DEFAULT false,
  offset_time_seconds BIGINT,
  payload TEXT,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_queue_name_message_id ON queue_message (queue_name,message_id);
CREATE INDEX combo_queue_message ON queue_message (queue_name,popped,deliver_on,created_on);
