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
package com.netflix.conductor.core.execution.managed;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

public interface ManagedTask {

    String MANAGED_TASKS = "managedTasks";

    String getTaskType();

    /**
     * Handles the task invocation by delegating execution to the managed runtime.
     *
     * @param workflow Workflow of which the task is a part of
     * @param task Task instance
     */
    void invoke(WorkflowModel workflow, TaskModel task);

    /**
     * Ignore the execution result and mark execution as CANCELED.
     *
     * @param workflow Workflow of which the task is a part of
     * @param task Task Instance
     */
    default void cancel(WorkflowModel workflow, TaskModel task) {
        task.setStatus(TaskModel.Status.CANCELED);
    }
}
