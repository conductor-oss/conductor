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
package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;

public class WorkflowSystemTaskStub extends WorkflowSystemTask {

    private boolean started = false;

    public WorkflowSystemTaskStub(String taskType) {
        super(taskType);
    }

    @Override
    public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
        started = true;
        task.setStatus(Status.COMPLETED);
        super.start(workflow, task, executor);
    }

    public boolean isStarted() {
        return started;
    }
}
