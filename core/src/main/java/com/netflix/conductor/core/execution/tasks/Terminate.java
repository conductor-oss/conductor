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

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_TERMINATE;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.COMPLETED;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.FAILED;

/**
 * Task that can terminate a workflow with a given status and modify the workflow's output with a
 * given parameter, it can act as a "return" statement for conditions where you simply want to
 * terminate your workflow. For example, if you have a decision where the first condition is met,
 * you want to execute some tasks, otherwise you want to finish your workflow.
 *
 * <pre>
 * ...
 * {
 *  "tasks": [
 *      {
 *          "name": "terminate",
 *          "taskReferenceName": "terminate0",
 *          "inputParameters": {
 *              "terminationStatus": "COMPLETED",
 *              "workflowOutput": "${task0.output}"
 *          },
 *          "type": "TERMINATE",
 *          "startDelay": 0,
 *          "optional": false
 *      }
 *   ]
 * }
 * ...
 * </pre>
 *
 * This task has some validations on creation and execution, they are: - the "terminationStatus"
 * parameter is mandatory and it can only receive the values "COMPLETED" or "FAILED" - the terminate
 * task cannot be optional
 */
@Component(TASK_TYPE_TERMINATE)
public class Terminate extends WorkflowSystemTask {

    private static final String TERMINATION_STATUS_PARAMETER = "terminationStatus";
    private static final String TERMINATION_REASON_PARAMETER = "terminationReason";
    private static final String TERMINATION_WORKFLOW_OUTPUT = "workflowOutput";

    public Terminate() {
        super(TASK_TYPE_TERMINATE);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        String returnStatus = (String) task.getInputData().get(TERMINATION_STATUS_PARAMETER);

        if (validateInputStatus(returnStatus)) {
            task.setOutputData(getInputFromParam(task.getInputData()));
            task.setStatus(TaskModel.Status.COMPLETED);
            return true;
        }
        task.setReasonForIncompletion("given termination status is not valid");
        task.setStatus(TaskModel.Status.FAILED);
        return false;
    }

    public static String getTerminationStatusParameter() {
        return TERMINATION_STATUS_PARAMETER;
    }

    public static String getTerminationReasonParameter() {
        return TERMINATION_REASON_PARAMETER;
    }

    public static String getTerminationWorkflowOutputParameter() {
        return TERMINATION_WORKFLOW_OUTPUT;
    }

    public static Boolean validateInputStatus(String status) {
        return COMPLETED.name().equals(status) || FAILED.name().equals(status);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getInputFromParam(Map<String, Object> taskInput) {
        HashMap<String, Object> output = new HashMap<>();
        if (taskInput.get(TERMINATION_WORKFLOW_OUTPUT) == null) {
            return output;
        }
        if (taskInput.get(TERMINATION_WORKFLOW_OUTPUT) instanceof HashMap) {
            output.putAll((HashMap<String, Object>) taskInput.get(TERMINATION_WORKFLOW_OUTPUT));
            return output;
        }
        output.put("output", taskInput.get(TERMINATION_WORKFLOW_OUTPUT));
        return output;
    }
}
