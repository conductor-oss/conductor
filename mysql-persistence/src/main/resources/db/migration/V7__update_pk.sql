DELIMITER $$
DROP PROCEDURE IF EXISTS `DropIndexIfExists`$$
CREATE PROCEDURE `DropIndexIfExists`(IN tableName VARCHAR(128), IN indexName VARCHAR(128))
BEGIN

    DECLARE idx_exists INT DEFAULT 0;

    SET idx_exists = (SELECT COUNT(*)
                      FROM information_schema.STATISTICS
                      WHERE TABLE_NAME = tableName
                        AND INDEX_NAME = indexName
                        AND TABLE_SCHEMA = database());

    SET @idx_stmt = IF(idx_exists > 0,
                       CONCAT('ALTER TABLE ', tableName, ' DROP INDEX ', indexName),
                       'SELECT CONCAT(''INFO: Index "'', indexName, ''" does not exist'')');

    PREPARE stmt FROM @idx_stmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END$$

DROP PROCEDURE IF EXISTS `DropColumnIfExists`$$
CREATE PROCEDURE `DropColumnIfExists`(IN tableName VARCHAR(128), IN columnName VARCHAR(128))
BEGIN

    DECLARE col_exists INT DEFAULT 0;

    SET col_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_NAME = tableName
                        AND COLUMN_NAME = columnName
                        AND TABLE_SCHEMA = database());

    SET @col_stmt = IF(col_exists > 0,
                       CONCAT('ALTER TABLE ', tableName, ' DROP COLUMN ', columnName),
                       'SELECT CONCAT(''INFO: Column "'', columnName,''" does not exist on table "'', tableName, ''"'')');

    PREPARE stmt FROM @col_stmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END$$
DELIMITER ;

# 1) queue message
CALL DropIndexIfExists('queue_message', 'unique_queue_name_message_id');
CALL DropColumnIfExists('queue_message', 'id');
ALTER TABLE queue_message ADD PRIMARY KEY (queue_name, message_id);

# 2) queue
CALL DropIndexIfExists('queue', 'unique_queue_name');
CALL DropColumnIfExists('queue', 'id');
ALTER TABLE queue ADD PRIMARY KEY (queue_name);

# 3) workflow_to_task
CALL DropIndexIfExists('workflow_to_task', 'unique_workflow_to_task_id');
CALL DropColumnIfExists('workflow_to_task', 'id');
ALTER TABLE workflow_to_task ADD PRIMARY KEY (workflow_id, task_id);

# 4) workflow_pending
CALL DropIndexIfExists('workflow_pending', 'unique_workflow_type_workflow_id');
CALL DropColumnIfExists('workflow_pending', 'id');
ALTER TABLE workflow_pending ADD PRIMARY KEY (workflow_type, workflow_id);

# 5) workflow_def_to_workflow
CALL DropIndexIfExists('workflow_def_to_workflow', 'unique_workflow_def_date_str');
CALL DropColumnIfExists('workflow_def_to_workflow', 'id');
ALTER TABLE workflow_def_to_workflow ADD PRIMARY KEY (workflow_def, date_str, workflow_id);

# 6) workflow
CALL DropIndexIfExists('workflow', 'unique_workflow_id');
CALL DropColumnIfExists('workflow', 'id');
ALTER TABLE workflow ADD PRIMARY KEY (workflow_id);

# 7) task
CALL DropIndexIfExists('task', 'unique_task_id');
CALL DropColumnIfExists('task', 'id');
ALTER TABLE task ADD PRIMARY KEY (task_id);

# 8) task_in_progress
CALL DropIndexIfExists('task_in_progress', 'unique_task_def_task_id1');
CALL DropColumnIfExists('task_in_progress', 'id');
ALTER TABLE task_in_progress ADD PRIMARY KEY (task_def_name, task_id);

# 9) task_scheduled
CALL DropIndexIfExists('task_scheduled', 'unique_workflow_id_task_key');
CALL DropColumnIfExists('task_scheduled', 'id');
ALTER TABLE task_scheduled ADD PRIMARY KEY (workflow_id, task_key);

# 10) poll_data
CALL DropIndexIfExists('poll_data', 'unique_poll_data');
CALL DropColumnIfExists('poll_data', 'id');
ALTER TABLE poll_data ADD PRIMARY KEY (queue_name, domain);

# 11) event_execution
CALL DropIndexIfExists('event_execution', 'unique_event_execution');
CALL DropColumnIfExists('event_execution', 'id');
ALTER TABLE event_execution ADD PRIMARY KEY (event_handler_name, event_name, execution_id);

# 12) meta_workflow_def
CALL DropIndexIfExists('meta_workflow_def', 'unique_name_version');
CALL DropColumnIfExists('meta_workflow_def', 'id');
ALTER TABLE meta_workflow_def ADD PRIMARY KEY (name, version);

# 13) meta_task_def
CALL DropIndexIfExists('meta_task_def', 'unique_task_def_name');
CALL DropColumnIfExists('meta_task_def', 'id');
ALTER TABLE meta_task_def ADD PRIMARY KEY (name);
