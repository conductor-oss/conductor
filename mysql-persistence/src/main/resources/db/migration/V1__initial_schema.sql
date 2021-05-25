
-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR METADATA DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE meta_event_handler (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  name varchar(255) NOT NULL,
  event varchar(255) NOT NULL,
  active boolean NOT NULL,
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  KEY event_handler_name_index (name),
  KEY event_handler_event_index (event)
);

CREATE TABLE meta_task_def (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  name varchar(255) NOT NULL,
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_task_def_name (name)
);

CREATE TABLE meta_workflow_def (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  name varchar(255) NOT NULL,
  version int(11) NOT NULL,
  latest_version int(11) NOT NULL DEFAULT 0,
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_name_version (name,version),
  KEY workflow_def_name_index (name)
);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR EXECUTION DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE event_execution (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  event_handler_name varchar(255) NOT NULL,
  event_name varchar(255) NOT NULL,
  message_id varchar(255) NOT NULL,
  execution_id varchar(255) NOT NULL,
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_event_execution (event_handler_name,event_name,message_id)
);

CREATE TABLE poll_data (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  queue_name varchar(255) NOT NULL,
  domain varchar(255) NOT NULL,
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_poll_data (queue_name,domain),
  KEY (queue_name)
);

CREATE TABLE task_scheduled (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_id varchar(255) NOT NULL,
  task_key varchar(255) NOT NULL,
  task_id varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_workflow_id_task_key (workflow_id,task_key)
);

CREATE TABLE task_in_progress (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  task_def_name varchar(255) NOT NULL,
  task_id varchar(255) NOT NULL,
  workflow_id varchar(255) NOT NULL,
  in_progress_status boolean NOT NULL DEFAULT false,
  PRIMARY KEY (id),
  UNIQUE KEY unique_task_def_task_id1 (task_def_name,task_id)
);

CREATE TABLE task (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  task_id varchar(255) NOT NULL,
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_task_id (task_id)
);

CREATE TABLE workflow (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_id varchar(255) NOT NULL,
  correlation_id varchar(255),
  json_data mediumtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_workflow_id (workflow_id)
);

CREATE TABLE workflow_def_to_workflow (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_def varchar(255) NOT NULL,
  date_str integer NOT NULL,
  workflow_id varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_workflow_def_date_str (workflow_def,date_str,workflow_id)
);

CREATE TABLE workflow_pending (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_type varchar(255) NOT NULL,
  workflow_id varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_workflow_type_workflow_id (workflow_type,workflow_id),
  KEY workflow_type_index (workflow_type)
);

CREATE TABLE workflow_to_task (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  workflow_id varchar(255) NOT NULL,
  task_id varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_workflow_to_task_id (workflow_id,task_id),
  KEY workflow_id_index (workflow_id)
);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR QUEUE DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE queue (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  queue_name varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_queue_name (queue_name)
);

CREATE TABLE queue_message (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deliver_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  queue_name varchar(255) NOT NULL,
  message_id varchar(255) NOT NULL,
  popped boolean DEFAULT false,
  offset_time_seconds long,
  payload mediumtext,
  PRIMARY KEY (id),
  UNIQUE KEY unique_queue_name_message_id (queue_name,message_id),
  KEY combo_queue_message (queue_name,popped,deliver_on,created_on)
);
