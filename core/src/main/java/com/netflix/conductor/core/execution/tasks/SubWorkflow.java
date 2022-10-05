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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.operation.StartWorkflowOperation;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

@Component(TASK_TYPE_SUB_WORKFLOW)
public class SubWorkflow extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubWorkflow.class);
    private static final String SUB_WORKFLOW_ID = "subWorkflowId";

    private final ObjectMapper objectMapper;
    private final StartWorkflowOperation startWorkflowOperation;

    public SubWorkflow(ObjectMapper objectMapper, StartWorkflowOperation startWorkflowOperation) {
        super(TASK_TYPE_SUB_WORKFLOW);
        this.objectMapper = objectMapper;
        this.startWorkflowOperation = startWorkflowOperation;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input = task.getInputData();
        String name = input.get("subWorkflowName").toString();
        int version = (int) input.get("subWorkflowVersion");

        WorkflowDef workflowDefinition = null;
        if (input.get("subWorkflowDefinition") != null) {
            // convert the value back to workflow definition object
            workflowDefinition =
                    objectMapper.convertValue(
                            input.get("subWorkflowDefinition"), WorkflowDef.class);
            name = workflowDefinition.getName();
        }

        Map<String, String> taskToDomain = workflow.getTaskToDomain();
        if (input.get("subWorkflowTaskToDomain") instanceof Map) {
            taskToDomain = (Map<String, String>) input.get("subWorkflowTaskToDomain");
        }

        var wfInput = (Map<String, Object>) input.get("workflowInput");
        if (wfInput == null || wfInput.isEmpty()) {
            wfInput = input;
        }
        String correlationId = workflow.getCorrelationId();

        try {
            StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
            startWorkflowInput.setWorkflowDefinition(workflowDefinition);
            startWorkflowInput.setName(name);
            startWorkflowInput.setVersion(version);
            startWorkflowInput.setWorkflowInput(wfInput);
            startWorkflowInput.setCorrelationId(correlationId);
            startWorkflowInput.setParentWorkflowId(workflow.getWorkflowId());
            startWorkflowInput.setParentWorkflowTaskId(task.getTaskId());
            startWorkflowInput.setTaskToDomain(taskToDomain);

            String subWorkflowId = startWorkflowOperation.execute(startWorkflowInput);

            task.setSubWorkflowId(subWorkflowId);
            // For backwards compatibility
            task.addOutput(SUB_WORKFLOW_ID, subWorkflowId);

            // Set task status based on current sub-workflow status, as the status can change in
            // recursion by the time we update here.
            WorkflowModel subWorkflow = workflowExecutor.getWorkflow(subWorkflowId, false);
            updateTaskStatus(subWorkflow, task);
        } catch (TransientException te) {
            LOGGER.info(
                    "A transient backend error happened when task {} in {} tried to start sub workflow {}.",
                    task.getTaskId(),
                    workflow.toShortString(),
                    name);
        } catch (Exception ae) {

            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(ae.getMessage());
            LOGGER.error(
                    "Error starting sub workflow: {} from workflow: {}",
                    name,
                    workflow.toShortString(),
                    ae);
        }
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        String workflowId = task.getSubWorkflowId();
        if (StringUtils.isEmpty(workflowId)) {
            return false;
        }

        WorkflowModel subWorkflow = workflowExecutor.getWorkflow(workflowId, false);
        WorkflowModel.Status subWorkflowStatus = subWorkflow.getStatus();
        if (!subWorkflowStatus.isTerminal()) {
            return false;
        }

        updateTaskStatus(subWorkflow, task);
        return true;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        String workflowId = task.getSubWorkflowId();
        if (StringUtils.isEmpty(workflowId)) {
            return;
        }
        WorkflowModel subWorkflow = workflowExecutor.getWorkflow(workflowId, true);
        subWorkflow.setStatus(WorkflowModel.Status.TERMINATED);
        String reason =
                StringUtils.isEmpty(workflow.getReasonForIncompletion())
                        ? "Parent workflow has been terminated with status " + workflow.getStatus()
                        : "Parent workflow has been terminated with reason: "
                                + workflow.getReasonForIncompletion();
        workflowExecutor.terminateWorkflow(subWorkflow, reason, null);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    /**
     * Keep Subworkflow task asyncComplete. The Subworkflow task will be executed once
     * asynchronously to move to IN_PROGRESS state, and will move to termination by Subworkflow's
     * completeWorkflow logic, there by avoiding periodic polling.
     *
     * @param task
     * @return
     */
    @Override
    public boolean isAsyncComplete(TaskModel task) {
        return true;
    }

    private void updateTaskStatus(WorkflowModel subworkflow, TaskModel task) {
        WorkflowModel.Status status = subworkflow.getStatus();
        switch (status) {
            case RUNNING:
            case PAUSED:
                task.setStatus(TaskModel.Status.IN_PROGRESS);
                break;
            case COMPLETED:
                task.setStatus(TaskModel.Status.COMPLETED);
                break;
            case FAILED:
                task.setStatus(TaskModel.Status.FAILED);
                break;
            case TERMINATED:
                task.setStatus(TaskModel.Status.CANCELED);
                break;
            case TIMED_OUT:
                task.setStatus(TaskModel.Status.TIMED_OUT);
                break;
            default:
                throw new NonTransientException(
                        "Subworkflow status does not conform to relevant task status.");
        }

        if (status.isTerminal()) {
            if (subworkflow.getExternalOutputPayloadStoragePath() != null) {
                task.setExternalOutputPayloadStoragePath(
                        subworkflow.getExternalOutputPayloadStoragePath());
            } else {
                task.addOutput(subworkflow.getOutput());
            }
            if (!status.isSuccessful()) {
                task.setReasonForIncompletion(
                        String.format(
                                "Sub workflow %s failure reason: %s",
                                subworkflow.toShortString(),
                                subworkflow.getReasonForIncompletion()));
            }
        }
    }

    /**
     * We don't need the tasks when retrieving the workflow data.
     *
     * @return false
     */
    @Override
    public boolean isTaskRetrievalRequired() {
        return false;
    }
}
