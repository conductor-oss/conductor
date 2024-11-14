--
-- Copyright 2021 Conductor Authors.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE meta_event_handler (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  name varchar2(255) NOT NULL,
  event varchar2(255) NOT NULL,
  active char(1) NOT NULL,
  json_data clob NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE meta_event_handler_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER meta_event_handler_seq_tr
 BEFORE INSERT ON meta_event_handler FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT meta_event_handler_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE INDEX event_handler_name_index ON meta_event_handler (name);
CREATE INDEX event_handler_event_index ON meta_event_handler (event);

CREATE TABLE meta_task_def (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  name varchar2(255) NOT NULL,
  json_data clob NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_task_def_name UNIQUE (name)
);

CREATE SEQUENCE meta_task_def_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER meta_task_def_seq_tr
 BEFORE INSERT ON meta_task_def FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT meta_task_def_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE meta_workflow_def (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  name varchar2(255) NOT NULL,
  version number(10) NOT NULL,
  latest_version number(10) DEFAULT 0 NOT NULL,
  json_data clob NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_name_version UNIQUE (name,version)
);

CREATE SEQUENCE meta_workflow_def_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER meta_workflow_def_seq_tr
 BEFORE INSERT ON meta_workflow_def FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT meta_workflow_def_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE INDEX workflow_def_name_index ON meta_workflow_def (name);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR EXECUTION DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE event_execution (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  event_handler_name varchar2(255) NOT NULL,
  event_name varchar2(255) NOT NULL,
  message_id varchar2(255) NOT NULL,
  execution_id varchar2(255) NOT NULL,
  json_data clob NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_event_execution UNIQUE (event_handler_name,event_name,message_id)
);

CREATE SEQUENCE event_execution_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER event_execution_seq_tr
 BEFORE INSERT ON event_execution FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT event_execution_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE poll_data (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  queue_name varchar2(255) NOT NULL,
  domain varchar2(255) NOT NULL,
  json_data clob NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_poll_data UNIQUE (queue_name,domain)
);

CREATE SEQUENCE poll_data_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER poll_data_seq_tr
 BEFORE INSERT ON poll_data FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT poll_data_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE INDEX poll_data_queue_name_index ON poll_data (queue_name);

CREATE TABLE task_scheduled (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  workflow_id varchar2(255) NOT NULL,
  task_key varchar2(255) NOT NULL,
  task_id varchar2(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_workflow_id_task_key UNIQUE (workflow_id,task_key)
);

CREATE SEQUENCE task_scheduled_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER task_scheduled_seq_tr
 BEFORE INSERT ON task_scheduled FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT task_scheduled_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE task_in_progress (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  task_def_name varchar2(255) NOT NULL,
  task_id varchar2(255) NOT NULL,
  workflow_id varchar2(255) NOT NULL,
  in_progress_status char(1) DEFAULT 'N' NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_task_def_task_id1 UNIQUE (task_def_name,task_id)
);

CREATE SEQUENCE task_in_progress_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER task_in_progress_seq_tr
 BEFORE INSERT ON task_in_progress FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT task_in_progress_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE task (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  task_id varchar2(255) NOT NULL,
  json_data clob NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_task_id UNIQUE (task_id)
);

CREATE SEQUENCE task_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER task_seq_tr
 BEFORE INSERT ON task FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT task_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE workflow (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  workflow_id varchar2(255) NOT NULL,
  correlation_id varchar2(255),
  json_data clob NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_workflow_id UNIQUE (workflow_id)
);

CREATE SEQUENCE workflow_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER workflow_seq_tr
 BEFORE INSERT ON workflow FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT workflow_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE workflow_def_to_workflow (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  workflow_def varchar2(255) NOT NULL,
  date_str number(10) NOT NULL,
  workflow_id varchar2(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_workflow_def_date_str UNIQUE (workflow_def,date_str,workflow_id)
);

CREATE SEQUENCE workflow_def_to_workflow_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER workflowdef_to_workflow_seq_tr
 BEFORE INSERT ON workflow_def_to_workflow FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT workflow_def_to_workflow_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE workflow_pending (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  workflow_type varchar2(255) NOT NULL,
  workflow_id varchar2(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_workflowtype_workflowid UNIQUE (workflow_type,workflow_id)
);

CREATE SEQUENCE workflow_pending_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER workflow_pending_seq_tr
 BEFORE INSERT ON workflow_pending FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT workflow_pending_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE INDEX workflow_type_index ON workflow_pending (workflow_type);


CREATE TABLE workflow_to_task (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  modified_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  workflow_id varchar2(255) NOT NULL,
  task_id varchar2(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_workflow_to_task_id UNIQUE (workflow_id,task_id)
);


CREATE SEQUENCE workflow_to_task_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER workflow_to_task_seq_tr
 BEFORE INSERT ON workflow_to_task FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT workflow_to_task_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE INDEX workflow_id_index ON workflow_to_task (workflow_id);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR QUEUE DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE queue (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  queue_name varchar2(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_queue_name UNIQUE (queue_name)
);

CREATE SEQUENCE queue_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER queue_seq_tr
 BEFORE INSERT ON queue FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT queue_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE TABLE queue_message (
  id number(11) check (id > 0) NOT NULL,
  created_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP NOT NULL,
  deliver_on TIMESTAMP(0) DEFAULT SYSTIMESTAMP,
  queue_name varchar2(255) NOT NULL,
  message_id varchar2(255) NOT NULL,
  popped char(1) DEFAULT 'N',
  offset_time_seconds number(10),
  payload clob DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT unique_queue_name_message_id UNIQUE (queue_name,message_id)
);

CREATE SEQUENCE queue_message_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE TRIGGER queue_message_seq_tr
 BEFORE INSERT ON queue_message FOR EACH ROW
 WHEN (NEW.id IS NULL)
BEGIN
 SELECT queue_message_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE INDEX combo_queue_message ON queue_message (queue_name,popped,deliver_on,created_on);