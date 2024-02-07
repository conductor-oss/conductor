/*
 * Copyright 2022 Conductor Authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SET_CORRELATION_ID;
import static com.netflix.conductor.model.TaskModel.Status.*;

@Component(TASK_TYPE_SET_CORRELATION_ID)
public class SetCorrelationId extends WorkflowSystemTask {

    private static Logger LOGGER = LoggerFactory.getLogger(SetCorrelationId.class);

    public SetCorrelationId() {
        super(TASK_TYPE_SET_CORRELATION_ID);
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        LOGGER.info("Setting Correlation ID");
        Object correlationId = task.getInputData().get("correlationId");

        if (correlationId == null
                || correlationId.getClass() != String.class
                || ((String) correlationId).isEmpty()) {
            task.setReasonForIncompletion(
                    "A non-empty String value must be provided for 'correlationId'");
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            return false;
        }

        workflow.setCorrelationId((String) correlationId);
        task.addOutput("correlationId", correlationId);
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    public boolean isAsync() {
        return false;
    }
}
