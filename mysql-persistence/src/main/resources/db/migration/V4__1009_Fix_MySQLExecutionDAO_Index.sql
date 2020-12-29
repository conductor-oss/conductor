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
