DELIMITER $$
DROP PROCEDURE IF EXISTS `DropIndexIfExists`$$
CREATE PROCEDURE `DropIndexIfExists`(IN tableName VARCHAR(128), IN indexName VARCHAR(128))
BEGIN

    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(1) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_NAME   = tableName
      AND INDEX_NAME   = indexName
      AND TABLE_SCHEMA = database();

    IF index_exists > 0 THEN

        SELECT CONCAT('INFO: Dropping Index ', indexName, ' on table ', tableName);
        SET @stmt = CONCAT('ALTER TABLE ', tableName, ' DROP INDEX ', indexName);
        PREPARE st FROM @stmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;

    ELSE
        SELECT CONCAT('INFO: Index ', indexName, ' does not exists on table ', tableName);
    END IF;

END$$

DROP PROCEDURE IF EXISTS `FixPkIfNeeded`$$
CREATE PROCEDURE `FixPkIfNeeded`(IN tableName VARCHAR(128), IN columns VARCHAR(128))
BEGIN

    DECLARE col_exists INT DEFAULT 0;

    SELECT COUNT(1) INTO col_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME   = tableName
      AND COLUMN_NAME  = 'id'
      AND TABLE_SCHEMA = database();

    IF col_exists > 0 THEN

        SELECT CONCAT('INFO: Updating PK on table ', tableName);

        SET @stmt = CONCAT('ALTER TABLE ', tableName, ' MODIFY id INT');
        PREPARE st FROM @stmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;

        SET @stmt = CONCAT('ALTER TABLE ', tableName, ' DROP PRIMARY KEY, ADD PRIMARY KEY (', columns, ')');
        PREPARE st FROM @stmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;

        SET @stmt = CONCAT('ALTER TABLE ', tableName, ' DROP COLUMN id');
        PREPARE st FROM @stmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;

    ELSE
        SELECT CONCAT('INFO: Column id does not exists on table ', tableName);
    END IF;

END$$
DELIMITER ;

CALL DropIndexIfExists('queue_message', 'unique_queue_name_message_id');
CALL FixPkIfNeeded('queue_message','queue_name, message_id');

CALL DropIndexIfExists('queue', 'unique_queue_name');
CALL FixPkIfNeeded('queue','queue_name');

CALL DropIndexIfExists('workflow_to_task', 'unique_workflow_to_task_id');
CALL FixPkIfNeeded('workflow_to_task', 'workflow_id, task_id');

CALL DropIndexIfExists('workflow_pending', 'unique_workflow_type_workflow_id');
CALL FixPkIfNeeded('workflow_pending', 'workflow_type, workflow_id');

CALL DropIndexIfExists('workflow_def_to_workflow', 'unique_workflow_def_date_str');
CALL FixPkIfNeeded('workflow_def_to_workflow', 'workflow_def, date_str, workflow_id');

CALL DropIndexIfExists('workflow', 'unique_workflow_id');
CALL FixPkIfNeeded('workflow', 'workflow_id');

CALL DropIndexIfExists('task', 'unique_task_id');
CALL FixPkIfNeeded('task', 'task_id');

CALL DropIndexIfExists('task_in_progress', 'unique_task_def_task_id1');
CALL FixPkIfNeeded('task_in_progress', 'task_def_name, task_id');

CALL DropIndexIfExists('task_scheduled', 'unique_workflow_id_task_key');
CALL FixPkIfNeeded('task_scheduled', 'workflow_id, task_key');

CALL DropIndexIfExists('poll_data', 'unique_poll_data');
CALL FixPkIfNeeded('poll_data','queue_name, domain');

CALL DropIndexIfExists('event_execution', 'unique_event_execution');
CALL FixPkIfNeeded('event_execution', 'event_handler_name, event_name, execution_id');

CALL DropIndexIfExists('meta_workflow_def', 'unique_name_version');
CALL FixPkIfNeeded('meta_workflow_def', 'name, version');

CALL DropIndexIfExists('meta_task_def', 'unique_task_def_name');
CALL FixPkIfNeeded('meta_task_def','name');