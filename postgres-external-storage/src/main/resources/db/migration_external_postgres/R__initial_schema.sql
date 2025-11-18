--
-- Copyright 2022 Netflix, Inc.
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


-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR EXTERNAL PAYLOAD POSTGRES STORAGE
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ${tableName}
(
    id   TEXT,
    data bytea NOT NULL,
    created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

ALTER TABLE ${tableName} ALTER COLUMN data SET STORAGE EXTERNAL;

-- Delete trigger to delete the oldest external_payload rows,
-- when there are too many or there are too old.

DROP TRIGGER IF EXISTS tr_keep_row_number_steady ON ${tableName};

CREATE OR REPLACE FUNCTION keep_row_number_steady()
    RETURNS TRIGGER AS
$body$
DECLARE
    time_interval interval := concat(${maxDataYears},' years ',${maxDataMonths},' mons ',${maxDataDays},' days' );
BEGIN
    WHILE ((SELECT count(id) FROM ${tableName}) > ${maxDataRows}) OR
       ((SELECT min(created_on) FROM ${tableName}) < (CURRENT_TIMESTAMP - time_interval))
    LOOP
        DELETE FROM ${tableName}
        WHERE created_on = (SELECT min(created_on) FROM ${tableName});
    END LOOP;
    RETURN NULL;
END;
$body$
    LANGUAGE plpgsql;

CREATE TRIGGER tr_keep_row_number_steady
    AFTER INSERT ON ${tableName}
    FOR EACH ROW EXECUTE PROCEDURE keep_row_number_steady();