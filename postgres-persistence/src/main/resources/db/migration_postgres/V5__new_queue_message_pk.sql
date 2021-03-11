--
-- Copyright 2021 Netflix, Inc.
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
DROP INDEX IF EXISTS unique_queue_name_message_id;

-- remove id primary key
ALTER TABLE queue_message DROP CONSTRAINT IF EXISTS queue_message_pkey;

-- remove id column
ALTER TABLE queue_message DROP COLUMN IF EXISTS id;

-- set primary key to queue_name, message_id
ALTER TABLE queue_message ADD PRIMARY KEY (queue_name, message_id);