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
package com.netflix.conductor.core.execution;

import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.model.SignalResponse;
import org.conductoross.conductor.model.TaskRun;
import org.conductoross.conductor.model.WorkflowRun;
import org.conductoross.conductor.model.WorkflowSignalReturnStrategy;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@Slf4j
@Builder
public class NotificationResult {
    private WorkflowModel targetWorkflow;
    private WorkflowModel blockingWorkflow;
    private List<TaskModel> blockingTasks;

    public SignalResponse toResponse(
            WorkflowSignalReturnStrategy returnStrategy, String requestId) {

        if (this.blockingTasks == null || this.blockingTasks.isEmpty()) {
            return switch (returnStrategy) {
                case TARGET_WORKFLOW, BLOCKING_WORKFLOW ->
                        getWorkflowRun(this.targetWorkflow, requestId, returnStrategy);
                default -> null;
            };
        }

        return switch (returnStrategy) {
            case TARGET_WORKFLOW -> getWorkflowRun(this.targetWorkflow, requestId, returnStrategy);
            case BLOCKING_WORKFLOW ->
                    getWorkflowRun(this.blockingWorkflow, requestId, returnStrategy);
            case BLOCKING_TASK, BLOCKING_TASK_INPUT ->
                    toTaskRun(this.blockingTasks.get(0), requestId, returnStrategy);
        };
    }

    private WorkflowRun getWorkflowRun(
            WorkflowModel workflow, String requestId, WorkflowSignalReturnStrategy returnStrategy) {
        WorkflowRun workflowRun = toWorkflowRun(workflow, requestId);
        workflowRun.setTargetWorkflowId(this.targetWorkflow.getWorkflowId());
        workflowRun.setTargetWorkflowStatus(this.targetWorkflow.getStatus().toString());
        workflowRun.setResponseType(returnStrategy);
        return workflowRun;
    }

    private static WorkflowRun toWorkflowRun(WorkflowModel workflow, String requestId) {
        WorkflowRun run = new WorkflowRun();
        run.setTasks(new ArrayList<>());

        run.setWorkflowId(workflow.getWorkflowId());
        run.setRequestId(requestId);
        run.setCorrelationId(workflow.getCorrelationId());
        run.setInput(workflow.getInput());
        run.setCreatedBy(workflow.getCreatedBy());
        run.setCreateTime(workflow.getCreateTime());
        run.setOutput(workflow.getOutput());
        run.setTasks(new ArrayList<>());
        workflow.getTasks().forEach(task -> run.getTasks().add(task.toTask()));
        run.setPriority(workflow.getPriority());
        run.setUpdateTime(workflow.getUpdatedTime() == null ? 0 : workflow.getUpdatedTime());
        run.setStatus(Workflow.WorkflowStatus.valueOf(workflow.getStatus().name()));
        run.setVariables(workflow.getVariables());

        return run;
    }

    private TaskRun toTaskRun(
            TaskModel task, String requestId, WorkflowSignalReturnStrategy responseType) {
        TaskRun run = new TaskRun();
        run.setTargetWorkflowId(targetWorkflow.getWorkflowId());
        run.setTargetWorkflowStatus(targetWorkflow.getStatus().toString());
        run.setResponseType(responseType);
        run.setRequestId(requestId);

        run.setTaskType(task.getTaskType());
        run.setTaskId(task.getTaskId());
        run.setReferenceTaskName(task.getReferenceTaskName());
        run.setRetryCount(task.getRetryCount());
        run.setTaskDefName(task.getTaskDefName());
        run.setRetriedTaskId(task.getRetriedTaskId());
        run.setWorkflowType(task.getWorkflowType());
        run.setReasonForIncompletion(task.getReasonForIncompletion());
        run.setPriority(task.getWorkflowPriority());

        run.setWorkflowId(task.getWorkflowInstanceId());
        run.setCorrelationId(task.getCorrelationId());
        run.setStatus(Task.Status.valueOf(task.getStatus().name()));
        run.setInput(task.getInputData());
        run.setOutput(task.getOutputData());

        run.setCreatedBy(task.getWorkerId());
        run.setCreateTime(task.getStartTime());
        run.setUpdateTime(task.getUpdateTime());

        return run;
    }
}
