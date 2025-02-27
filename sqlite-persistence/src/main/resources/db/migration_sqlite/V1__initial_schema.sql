CREATE TABLE meta_event_handler (
  id integer PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  event TEXT NOT NULL,
  active INTEGER NOT NULL,
  json_data TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE locks (
    lock_id TEXT NOT NULL,
    lease_expiration DATETIME NOT NULL,
    PRIMARY KEY (lock_id)
);

CREATE TABLE meta_workflow_def (
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    name TEXT NOT NULL,
    version INTEGER NOT NULL,
    latest_version INTEGER DEFAULT 0 NOT NULL,
    json_data TEXT NOT NULL,
    PRIMARY KEY (name, version)
);
CREATE INDEX workflow_def_name_index ON meta_workflow_def(name);

CREATE TABLE meta_task_def (
  name TEXT NOT NULL PRIMARY KEY,
  json_data TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Execution Tables
CREATE TABLE event_execution (
  event_handler_name TEXT NOT NULL,
  event_name TEXT NOT NULL,
  execution_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  json_data TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_handler_name, event_name, execution_id)
);

CREATE TABLE poll_data (
  queue_name TEXT NOT NULL,
  domain TEXT NOT NULL,
  json_data TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (queue_name, domain)
);
CREATE INDEX poll_data_queue_name_idx ON poll_data(queue_name);

CREATE TABLE task_scheduled (
  workflow_id TEXT NOT NULL,
  task_key TEXT NOT NULL,
  task_id TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (workflow_id, task_key)
);

CREATE TABLE task_in_progress (
  task_def_name TEXT NOT NULL,
  task_id TEXT NOT NULL,
  workflow_id TEXT NOT NULL,
  in_progress_status INTEGER NOT NULL DEFAULT 0,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (task_def_name, task_id)
);

CREATE TABLE task (
  task_id TEXT NOT NULL PRIMARY KEY,
  json_data TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow (
  workflow_id TEXT NOT NULL PRIMARY KEY,
  correlation_id TEXT,
  json_data TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_def_to_workflow (
  workflow_def TEXT NOT NULL,
  date_str TEXT,
  workflow_id TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (workflow_def, date_str, workflow_id)
);

CREATE TABLE workflow_pending (
  workflow_type TEXT NOT NULL,
  workflow_id TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (workflow_type, workflow_id)
);
CREATE INDEX workflow_type_index ON workflow_pending (workflow_type);

CREATE TABLE workflow_to_task (
  workflow_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (workflow_id, task_id)
);
CREATE INDEX workflow_id_index ON workflow_to_task(workflow_id);

-- Queue Tables
CREATE TABLE queue (
  queue_name TEXT NOT NULL PRIMARY KEY,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE queue_message (
  queue_name TEXT NOT NULL,
  message_id TEXT NOT NULL,
  deliver_on DATETIME DEFAULT CURRENT_TIMESTAMP,
  priority INTEGER DEFAULT 0,
  popped INTEGER DEFAULT 0,
  offset_time_seconds INTEGER,
  payload TEXT,
  created_on DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (queue_name, message_id)
);

CREATE TABLE task_execution_logs (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT NOT NULL,
    log TEXT NOT NULL,
    created_time DATETIME NOT NULL
);

CREATE INDEX task_execution_logs_task_id_idx ON task_execution_logs(task_id);

CREATE TABLE task_index (
    task_id TEXT NOT NULL,
    task_type TEXT NOT NULL,
    task_def_name TEXT NOT NULL,
    status TEXT NOT NULL,
    start_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    workflow_type TEXT NOT NULL,
    json_data TEXT NOT NULL,
    PRIMARY KEY (task_id)
);

CREATE TABLE workflow_index (
    workflow_id TEXT NOT NULL,
    correlation_id TEXT,
    workflow_type TEXT NOT NULL,
    start_time DATETIME NOT NULL,
    status TEXT NOT NULL,
    json_data TEXT NOT NULL,  -- SQLite doesn't have JSONB, storing as TEXT
    update_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    PRIMARY KEY (workflow_id)
);

-- Regular indexes
CREATE INDEX workflow_index_correlation_id_idx ON workflow_index(correlation_id);
CREATE INDEX workflow_index_start_time_idx ON workflow_index(start_time);
CREATE INDEX workflow_index_status_idx ON workflow_index(status);
CREATE INDEX workflow_index_workflow_type_idx ON workflow_index(workflow_type);

-- Regular indexes for columns
CREATE INDEX task_index_status_idx ON task_index(status);
CREATE INDEX task_index_task_def_name_idx ON task_index(task_def_name);
CREATE INDEX task_index_task_id_idx ON task_index(task_id);
CREATE INDEX task_index_task_type_idx ON task_index(task_type);
CREATE INDEX task_index_update_time_idx ON task_index(update_time);
CREATE INDEX task_index_workflow_type_idx ON task_index(workflow_type);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_event_handler_name ON meta_event_handler (name);
CREATE INDEX IF NOT EXISTS idx_event_handler_event ON meta_event_handler (event);
CREATE INDEX IF NOT EXISTS idx_workflow_def_name ON meta_workflow_def (name);
CREATE INDEX IF NOT EXISTS idx_workflow_correlation ON workflow (correlation_id);
CREATE INDEX IF NOT EXISTS idx_queue_message_combo ON queue_message (queue_name, priority DESC, popped, deliver_on, created_on);