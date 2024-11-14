--
-- Copyright 2021 Conductor Authors.
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

-- no longer need separate index if pk is queue_name, message_id
DECLARE
    sql_query  VARCHAR2 (150);
BEGIN
    SELECT CASE WHEN 
        (SELECT COUNT(INDEX_NAME) FROM all_indexes WHERE OWNER = USER AND TABLE_NAME = 'QUEUE_MESSAGE' AND INDEX_NAME = 'UNIQUE_QUEUE_NAME_MESSAGE_ID') > 0 
        THEN
            'ALTER TABLE QUEUE_MESSAGE DROP CONSTRAINT UNIQUE_QUEUE_NAME_MESSAGE_ID DROP INDEX'
        ELSE
            'SELECT 1 FROM DUAL'
        END
    INTO sql_query from DUAL;
    EXECUTE IMMEDIATE sql_query;
END;
/
ALTER TABLE queue_message DROP PRIMARY KEY;
ALTER TABLE queue_message ADD CONSTRAINT UNIQUE_QUEUE_NAME_MESSAGE_ID PRIMARY KEY (queue_name, message_id);