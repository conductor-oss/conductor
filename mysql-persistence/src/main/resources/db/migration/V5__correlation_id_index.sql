# Drop the 'workflow_corr_id_index' index if it exists
SET @exist := (SELECT COUNT(INDEX_NAME)
               FROM information_schema.STATISTICS
               WHERE `TABLE_NAME` = 'workflow'
                 AND `INDEX_NAME` = 'workflow_corr_id_index'
                 AND TABLE_SCHEMA = database());
SET @sqlstmt := IF(@exist > 0, 'ALTER TABLE `workflow` DROP INDEX `workflow_corr_id_index`',
                   'SELECT ''INFO: Index already exists.''');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;

# Create the 'workflow_corr_id_index' index with correlation_id column because correlation_id queries are slow in large databases.
CREATE INDEX workflow_corr_id_index ON workflow (correlation_id);
