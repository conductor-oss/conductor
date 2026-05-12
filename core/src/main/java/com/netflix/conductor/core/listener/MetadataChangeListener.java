/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.core.listener;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import com.fasterxml.jackson.annotation.JsonValue;

/** Listener for metadata changes: workflow definitions, task definitions, and event handlers. */
public interface MetadataChangeListener {

    enum MetadataChangeType {
        CREATED,
        UPDATED,
        DELETED;

        @JsonValue
        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    enum MetadataResourceType {
        WORKFLOW_DEF,
        TASK_DEF,
        EVENT_HANDLER;

        @JsonValue
        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    default void onWorkflowDefRegistered(WorkflowDef workflowDef) {}

    default void onWorkflowDefUpdated(WorkflowDef workflowDef) {}

    default void onWorkflowDefUnregistered(String name, int version) {}

    default void onTaskDefRegistered(TaskDef taskDef) {}

    default void onTaskDefUpdated(TaskDef taskDef) {}

    default void onTaskDefUnregistered(String taskType) {}

    default void onEventHandlerRegistered(EventHandler eventHandler) {}

    default void onEventHandlerUpdated(EventHandler eventHandler) {}

    default void onEventHandlerUnregistered(String name) {}
}
