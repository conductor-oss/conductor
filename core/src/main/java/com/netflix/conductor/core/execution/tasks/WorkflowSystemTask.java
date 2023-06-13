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
package com.netflix.conductor.core.execution.tasks;

import java.util.Optional;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

public abstract class WorkflowSystemTask {

    private final String taskType;

    public WorkflowSystemTask(String taskType) {
        this.taskType = taskType;
    }

    /**
     * Start the task execution.
     *
     * <p>Called only once, and first, when the task status is SCHEDULED.
     *
     * @param workflow Workflow for which the task is being started
     * @param task Instance of the Task
     * @param workflowExecutor Workflow Executor
     */
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        // Do nothing unless overridden by the task implementation
    }

    /**
     * "Execute" the task.
     *
     * <p>Called after {@link #start(WorkflowModel, TaskModel, WorkflowExecutor)}, if the task
     * status is not terminal. Can be called more than once.
     *
     * @param workflow Workflow for which the task is being started
     * @param task Instance of the Task
     * @param workflowExecutor Workflow Executor
     * @return true, if the execution has changed the task status. return false otherwise.
     */
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        return false;
    }

    /**
     * Cancel task execution
     *
     * @param workflow Workflow for which the task is being started
     * @param task Instance of the Task
     * @param workflowExecutor Workflow Executor
     */
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {}

    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long defaultOffset) {
        return Optional.empty();
    }

    /**
     * @return True if the task is supposed to be started asynchronously using internal queues.
     */
    public boolean isAsync() {
        return false;
    }

    /**
     * @return True to keep task in 'IN_PROGRESS' state, and 'COMPLETE' later by an external
     *     message.
     */
    public boolean isAsyncComplete(TaskModel task) {
        if (task.getInputData().containsKey("asyncComplete")) {
            return Optional.ofNullable(task.getInputData().get("asyncComplete"))
                    .map(result -> (Boolean) result)
                    .orElse(false);
        } else {
            return Optional.ofNullable(task.getWorkflowTask())
                    .map(WorkflowTask::isAsyncComplete)
                    .orElse(false);
        }
    }

    /**
     * @return name of the system task
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * Default to true for retrieving tasks when retrieving workflow data. Some cases (e.g.
     * subworkflows) might not need the tasks at all, and by setting this to false in that case, you
     * can get a solid performance gain.
     *
     * @return true for retrieving tasks when getting workflow
     */
    public boolean isTaskRetrievalRequired() {
        return true;
    }

    @Override
    public String toString() {
        return taskType;
    }
}
