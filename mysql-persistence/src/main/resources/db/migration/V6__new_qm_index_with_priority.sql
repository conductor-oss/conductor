# Drop the 'combo_queue_message' index if it exists
SET @exist := (SELECT COUNT(INDEX_NAME)
               FROM information_schema.STATISTICS
               WHERE `TABLE_NAME` = 'queue_message'
                 AND `INDEX_NAME` = 'combo_queue_message'
                 AND TABLE_SCHEMA = database());
SET @sqlstmt := IF(@exist > 0, 'ALTER TABLE `queue_message` DROP INDEX `combo_queue_message`',
                   'SELECT ''INFO: Index already exists.''');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;

# Re-create the 'combo_queue_message' index to add priority column because queries that order by priority are slow in large databases.
CREATE INDEX combo_queue_message ON queue_message (queue_name,priority,popped,deliver_on,created_on);
