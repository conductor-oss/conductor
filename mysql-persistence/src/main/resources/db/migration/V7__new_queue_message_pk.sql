# no longer need separate index if pk is queue_name, message_id
SET @idx_exists := (SELECT COUNT(INDEX_NAME)
                    FROM information_schema.STATISTICS
                    WHERE `TABLE_NAME` = 'queue_message'
                      AND `INDEX_NAME` = 'unique_queue_name_message_id'
                      AND TABLE_SCHEMA = database());
SET @idxstmt := IF(@idx_exists > 0, 'ALTER TABLE `queue_message` DROP INDEX `unique_queue_name_message_id`',
                                    'SELECT ''INFO: Index unique_queue_name_message_id does not exist.''');
PREPARE stmt1 FROM @idxstmt;
EXECUTE stmt1;

# remove id column
set @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE `TABLE_NAME`  = 'queue_message'
                      AND `COLUMN_NAME` = 'id'
                      AND TABLE_SCHEMA  = database());
SET @colstmt := IF(@col_exists > 0, 'ALTER TABLE `queue_message` DROP COLUMN `id`',
                                    'SELECT ''INFO: Column id does not exist.''') ;
PREPARE stmt2 from @colstmt;
EXECUTE stmt2;

# set primary key to queue_name, message_id
ALTER TABLE queue_message ADD PRIMARY KEY (queue_name, message_id);
