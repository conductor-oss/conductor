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

-- Drop the 'unique_event_execution' index if it exists
DECLARE
    sql_query  VARCHAR2 (150);
BEGIN
    SELECT CASE WHEN 
        (SELECT COUNT(*) FROM all_indexes WHERE OWNER = USER AND TABLE_NAME = 'EVENT_EXECUTION' AND INDEX_NAME = 'UNIQUE_EVENT_EXECUTION') > 0 
        THEN
            'ALTER TABLE EVENT_EXECUTION DROP CONSTRAINT UNIQUE_EVENT_EXECUTION DROP INDEX'
        ELSE
            'SELECT 1 FROM DUAL'
        END
    INTO sql_query from DUAL;
    EXECUTE IMMEDIATE sql_query;
END;
/

-- Create the 'unique_event_execution' index with execution_id column instead of 'message_id' so events can be executed multiple times.
ALTER TABLE event_execution
    ADD CONSTRAINT unique_event_execution UNIQUE (event_handler_name, event_name, execution_id);