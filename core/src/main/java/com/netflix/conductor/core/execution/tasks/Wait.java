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

import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;
import static com.netflix.conductor.model.TaskModel.Status.*;

@Component(TASK_TYPE_WAIT)
public class Wait extends WorkflowSystemTask {

    public static final String DURATION_INPUT = "duration";
    public static final String UNTIL_INPUT = "until";

    public Wait() {
        super(TASK_TYPE_WAIT);
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        long timeOut = task.getWaitTimeout();
        if (timeOut == 0) {
            return false;
        }
        if (System.currentTimeMillis() > timeOut) {
            task.setStatus(COMPLETED);
            return true;
        }

        return false;
    }

    public boolean isAsync() {
        return true;
    }
}
