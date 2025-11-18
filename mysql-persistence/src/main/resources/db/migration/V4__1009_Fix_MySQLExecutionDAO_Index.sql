# Drop the 'unique_event_execution' index if it exists
SET @exist := (SELECT COUNT(INDEX_NAME)
               FROM information_schema.STATISTICS
               WHERE `TABLE_NAME` = 'event_execution'
                 AND `INDEX_NAME` = 'unique_event_execution'
                 AND TABLE_SCHEMA = database());
SET @sqlstmt := IF(@exist > 0, 'ALTER TABLE `event_execution` DROP INDEX `unique_event_execution`',
                   'SELECT ''INFO: Index already exists.''');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;

# Create the 'unique_event_execution' index with execution_id column instead of 'message_id' so events can be executed multiple times.
ALTER TABLE `event_execution`
    ADD CONSTRAINT `unique_event_execution` UNIQUE (event_handler_name, event_name, execution_id);
