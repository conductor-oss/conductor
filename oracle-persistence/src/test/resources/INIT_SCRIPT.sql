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

-- 18+C XE
--CREATE USER junit_user;
--GRANT CONNECT, RESOURCE, CREATE SESSION, DBA, DV_REALM_RESOURCE TO junit_user CONTAINER=ALL;
--ALTER USER  junit_user IDENTIFIED BY "junit_user";
--ALTER USER junit_user QUOTA UNLIMITED ON USERS;

-- 19+C EE
-- ALTER SESSION SET CONTAINER=ORCLPDB1
--CREATE USER junit_user;
--GRANT CONNECT, RESOURCE, CREATE SESSION, DBA, DV_REALM_RESOURCE TO junit_user;
--ALTER USER  junit_user IDENTIFIED BY "junit_user";
--ALTER USER junit_user QUOTA UNLIMITED ON USERS;

-- 11G XE
ALTER SESSION SET CONTAINER=CONDUCTOR;
CREATE USER conductor IDENTIFIED BY "conductor";
GRANT CONNECT, RESOURCE, CREATE SESSION, DBA TO conductor;
CREATE TABLESPACE USERS DATAFILE '/opt/oracle/oradata/FREE/CONDUCTOR/users.dbf' SIZE 500M UNIFORM SIZE 128k;
ALTER USER conductor QUOTA UNLIMITED ON USERS;