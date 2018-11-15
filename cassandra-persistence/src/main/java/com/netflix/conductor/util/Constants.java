/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.util;

public interface Constants {

    String TABLE_WORKFLOWS = "workflows";
    String TABLE_TASK_LOOKUP = "task_lookup";

    String WORKFLOW_ID_KEY = "workflow_id";
    String SHARD_ID_KEY = "shard_id";
    String TASK_ID_KEY = "task_id";
    String ENTITY_KEY = "entity";
    String PAYLOAD_KEY = "payload";
    String TOTAL_TASKS_KEY = "total_tasks";
    String TOTAL_PARTITIONS_KEY = "total_partitions";

    String ENTITY_TYPE_WORKFLOW = "workflow";
    String ENTITY_TYPE_TASK = "task";

    String CREATE_KEYSPACE_STATEMENT = "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = { 'class' : 'NetworkTopologyStrategy', 'us-east' : '3' };";
    String CREATE_WORKFLOWS_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS %s." + TABLE_WORKFLOWS + " (" + WORKFLOW_ID_KEY + " uuid, " + SHARD_ID_KEY + " int, " + TASK_ID_KEY + " text, " + ENTITY_KEY + " text, " + PAYLOAD_KEY + " text, " + TOTAL_TASKS_KEY + " int STATIC, " + TOTAL_PARTITIONS_KEY + " int STATIC, PRIMARY KEY ((" + WORKFLOW_ID_KEY + ", " + SHARD_ID_KEY + "), " + ENTITY_KEY + ", " + TASK_ID_KEY + "));";
    String CREATE_TASK_LOOKUP_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS %s." + TABLE_TASK_LOOKUP + "(" + TASK_ID_KEY + " uuid, " + WORKFLOW_ID_KEY + " uuid, PRIMARY KEY (" + TASK_ID_KEY + "));";
}