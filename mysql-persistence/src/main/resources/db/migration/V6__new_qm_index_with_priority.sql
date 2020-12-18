--
-- Copyright 2020 Netflix, Inc.
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
