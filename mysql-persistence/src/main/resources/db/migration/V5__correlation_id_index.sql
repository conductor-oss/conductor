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
