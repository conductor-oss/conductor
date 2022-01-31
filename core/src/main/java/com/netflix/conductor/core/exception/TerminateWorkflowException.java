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
package com.netflix.conductor.core.exception;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.model.WorkflowModel.Status.FAILED;

public class TerminateWorkflowException extends RuntimeException {

    private final WorkflowModel.Status workflowStatus;
    private final TaskModel task;

    public TerminateWorkflowException(String reason) {
        this(reason, FAILED);
    }

    public TerminateWorkflowException(String reason, WorkflowModel.Status workflowStatus) {
        this(reason, workflowStatus, null);
    }

    public TerminateWorkflowException(
            String reason, WorkflowModel.Status workflowStatus, TaskModel task) {
        super(reason);
        this.workflowStatus = workflowStatus;
        this.task = task;
    }

    public WorkflowModel.Status getWorkflowStatus() {
        return workflowStatus;
    }

    public TaskModel getTask() {
        return task;
    }
}
