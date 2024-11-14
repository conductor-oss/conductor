--
-- Copyright 2020 Conductor Authors.
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

-- Drop the 'combo_queue_message' index if it exists
DECLARE
    sql_query  VARCHAR2 (150);
BEGIN
    SELECT CASE WHEN 
        (SELECT COUNT(INDEX_NAME) FROM all_indexes WHERE OWNER = USER AND TABLE_NAME = 'QUEUE_MESSAGE' AND INDEX_NAME = 'COMBO_QUEUE_MESSAGE') > 0 
        THEN
            'DROP INDEX COMBO_QUEUE_MESSAGE'
        ELSE
            'SELECT 1 FROM DUAL'
        END
    INTO sql_query from DUAL;
    EXECUTE IMMEDIATE sql_query;
END;
/

-- Re-create the 'combo_queue_message' index to add priority column because queries that order by priority are slow in large databases.
CREATE INDEX combo_queue_message ON queue_message (queue_name,priority,popped,deliver_on,created_on);