/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.cassandra.util;

public interface Constants {

    String DAO_NAME = "cassandra";

    String TABLE_WORKFLOWS = "workflows";
    String TABLE_TASK_LOOKUP = "task_lookup";
    String TABLE_TASK_DEF_LIMIT = "task_def_limit";
    String TABLE_WORKFLOW_DEFS = "workflow_definitions";
    String TABLE_WORKFLOW_DEFS_INDEX = "workflow_defs_index";
    String TABLE_TASK_DEFS = "task_definitions";
    String TABLE_EVENT_HANDLERS = "event_handlers";
    String TABLE_EVENT_EXECUTIONS = "event_executions";

    String WORKFLOW_ID_KEY = "workflow_id";
    String SHARD_ID_KEY = "shard_id";
    String TASK_ID_KEY = "task_id";
    String ENTITY_KEY = "entity";
    String PAYLOAD_KEY = "payload";
    String TOTAL_TASKS_KEY = "total_tasks";
    String TOTAL_PARTITIONS_KEY = "total_partitions";
    String TASK_DEF_NAME_KEY = "task_def_name";
    String WORKFLOW_DEF_NAME_KEY = "workflow_def_name";
    String WORKFLOW_VERSION_KEY = "version";
    String WORKFLOW_DEFINITION_KEY = "workflow_definition";
    String WORKFLOW_DEF_INDEX_KEY = "workflow_def_version_index";
    String WORKFLOW_DEF_INDEX_VALUE = "workflow_def_index_value";
    String WORKFLOW_DEF_NAME_VERSION_KEY = "workflow_def_name_version";
    String TASK_DEFS_KEY = "task_defs";
    String TASK_DEFINITION_KEY = "task_definition";
    String HANDLERS_KEY = "handlers";
    String EVENT_HANDLER_NAME_KEY = "event_handler_name";
    String EVENT_HANDLER_KEY = "event_handler";
    String MESSAGE_ID_KEY = "message_id";
    String EVENT_EXECUTION_ID_KEY = "event_execution_id";

    String ENTITY_TYPE_WORKFLOW = "workflow";
    String ENTITY_TYPE_TASK = "task";

    int DEFAULT_SHARD_ID = 1;
    int DEFAULT_TOTAL_PARTITIONS = 1;
}
