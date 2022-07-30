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

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

public abstract class ManagedTask {

    public final static String MANAGED_TASKS = "managedTasks";
    private final WorkflowExecutor workflowExecutor;

    public ManagedTask(WorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    /**
     * An implementation of managed task.
     *
     * @return Task type
     */
    protected abstract String getTaskType();

    /**
     * Handle the task invocation by delegating execution to the managed runtime.
     *
     * @param workflow Workflow of which the task is a part of
     * @param task Task instance
     */
    protected abstract void invoke(WorkflowModel workflow, TaskModel task);

    /**
     * Ignore the execution result and mark execution as CANCELED.
     *
     * @param workflow Workflow of which the task is a part of
     * @param task Task instance
     */
    protected void cancel(WorkflowModel workflow, TaskModel task) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    /**
     * Fetch the execution results from the managed runtime and implements a callback path to the
     * invoking workflow and task.
     */
    protected abstract TaskResult callback();

    public final void end() {
        TaskResult taskResult = callback();
        workflowExecutor.updateTask(taskResult);
    }
}
