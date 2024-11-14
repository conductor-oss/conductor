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

-- Drop the 'workflow_corr_id_index' index if it exists
DECLARE
    sql_query  VARCHAR2 (150);
BEGIN
    SELECT CASE WHEN 
        (SELECT COUNT(INDEX_NAME) FROM all_indexes WHERE OWNER = USER AND TABLE_NAME = 'WORKFLOW' AND INDEX_NAME = 'WORKFLOW_CORR_ID_INDEX') > 0 
        THEN
            'ALTER TABLE WORKFLOW DROP CONSTRAINT WORKFLOW_CORR_ID_INDEX DROP INDEX'
        ELSE
            'SELECT 1 FROM DUAL'
        END
    INTO sql_query from DUAL;
    EXECUTE IMMEDIATE sql_query;
END;
/

-- Create the 'workflow_corr_id_index' index with correlation_id column because correlation_id queries are slow in large databases.
CREATE INDEX workflow_corr_id_index ON workflow (correlation_id);