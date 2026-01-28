/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.core.execution.mapper;

import java.util.Collection;
import java.util.LinkedList;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.execution.listeners.TaskUpdateListenerActionHandler.TYPE;

public interface ParentTaskMapper extends TaskMapper {
    String HAS_CHILDREN = "hasChildren";

    default TaskModel processChild(
            WorkflowModel workflow, TaskModel parentTask, TaskModel childTask) {
        return childTask;
    }

    default void addParentTaskListener(TaskModel parent, TaskModel task) {
        task.getUpdateListeners()
                .computeIfAbsent(TYPE, k -> new LinkedList<>())
                .add(parent.getTaskId());
    }

    boolean isChild(WorkflowModel workflow, TaskModel switchTask, WorkflowTask task);

    default boolean isChild(WorkflowTask task, Collection<WorkflowTask> workflowTasks) {
        return workflowTasks.stream()
                .anyMatch(
                        workflowTask ->
                                workflowTask
                                        .getTaskReferenceName()
                                        .equals(task.getTaskReferenceName()));
    }
}
