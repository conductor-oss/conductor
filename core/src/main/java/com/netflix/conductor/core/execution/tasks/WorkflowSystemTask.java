/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;

import java.util.Optional;

public class WorkflowSystemTask {

    private final String name;

    public WorkflowSystemTask(String name) {
        this.name = name;
    }

    /**
     * Start the task execution
     *
     * @param workflow         Workflow for which the task is being started
     * @param task             Instance of the Task
     * @param workflowExecutor Workflow Executor
     */
    public void start(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {
        //Do nothing unless overridden by the task implementation
    }

    /**
     * @param workflow         Workflow for which the task is being started
     * @param task             Instance of the Task
     * @param workflowExecutor Workflow Executor
     * @return true, if the execution has changed the task status.  return false otherwise.
     */
    public boolean execute(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {
        return false;
    }

    /**
     * Cancel task execution
     *
     * @param workflow         Workflow for which the task is being started
     * @param task             Instance of the Task
     * @param workflowExecutor Workflow Executor
     */
    public void cancel(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {
    }

    /**
     * @return True if the task is supposed to be started asynchronously using internal queues.
     */
    public boolean isAsync() {
        return false;
    }

    /**
     * @return True to keep task in 'IN_PROGRESS' state, and 'COMPLETE' later by an external message.
     */
    public boolean isAsyncComplete(Task task) {
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
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
