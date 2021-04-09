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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

@Component(TASK_TYPE_SUB_WORKFLOW)
public class SubWorkflow extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubWorkflow.class);
    private static final String SUB_WORKFLOW_ID = "subWorkflowId";

    private final ObjectMapper objectMapper;

    public SubWorkflow(ObjectMapper objectMapper) {
        super(TASK_TYPE_SUB_WORKFLOW);
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void start(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {

        Map<String, Object> input = task.getInputData();
        String name = input.get("subWorkflowName").toString();
        int version = (int) input.get("subWorkflowVersion");

        WorkflowDef workflowDefinition = null;
        if (input.get("subWorkflowDefinition") != null) {
            // convert the value back to workflow definition object
            workflowDefinition = objectMapper.convertValue(input.get("subWorkflowDefinition"), WorkflowDef.class);
        }

        Map taskToDomain = workflow.getTaskToDomain();
        if (input.get("subWorkflowTaskToDomain") instanceof Map) {
            taskToDomain = (Map) input.get("subWorkflowTaskToDomain");
        }
        Map<String, Object> wfInput = (Map<String, Object>) input.get("workflowInput");
        if (wfInput == null || wfInput.isEmpty()) {
            wfInput = input;
        }
        String correlationId = workflow.getCorrelationId();

        try {
            String subWorkflowId;
            if (workflowDefinition != null) {
                subWorkflowId = workflowExecutor.startWorkflow(
                    workflowDefinition,
                    wfInput,
                    null,
                    correlationId,
                    0,
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    null,
                    taskToDomain);
            } else {
                subWorkflowId = workflowExecutor.startWorkflow(
                    name,
                    version,
                    wfInput,
                    null,
                    correlationId,
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    null,
                    taskToDomain);
            }

            task.setSubWorkflowId(subWorkflowId);
            // For backwards compatibility
            task.getOutputData().put(SUB_WORKFLOW_ID, subWorkflowId);

            // Set task status based on current sub-workflow status, as the status can change in recursion by the time we update here.
            Workflow subWorkflow = workflowExecutor.getWorkflow(subWorkflowId, false);
            updateTaskStatus(subWorkflow, task);
        } catch (Exception e) {
            task.setStatus(Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            LOGGER.error("Error starting sub workflow: {} from workflow: {}", name, workflow.getWorkflowId(), e);
        }
    }

    @Override
    public boolean execute(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {
        String workflowId = task.getSubWorkflowId();
        if (StringUtils.isEmpty(workflowId)) {
            return false;
        }

        Workflow subWorkflow = workflowExecutor.getWorkflow(workflowId, false);
        WorkflowStatus subWorkflowStatus = subWorkflow.getStatus();
        if (!subWorkflowStatus.isTerminal()) {
            return false;
        }

        updateTaskStatus(subWorkflow, task);
        return true;
    }

    @Override
    public void cancel(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {
        String workflowId = task.getSubWorkflowId();
        if (StringUtils.isEmpty(workflowId)) {
            return;
        }
        Workflow subWorkflow = workflowExecutor.getWorkflow(workflowId, true);
        subWorkflow.setStatus(WorkflowStatus.TERMINATED);
        String reason = StringUtils.isEmpty(workflow.getReasonForIncompletion())
            ? "Parent workflow has been terminated with status " + workflow.getStatus()
            : "Parent workflow has been terminated with reason: " + workflow.getReasonForIncompletion();
        workflowExecutor.terminateWorkflow(subWorkflow, reason, null);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    /**
     * Keep Subworkflow task asyncComplete. The Subworkflow task will be executed once asynchronously to move to
     * IN_PROGRESS state, and will move to termination by Subworkflow's completeWorkflow logic, there by avoiding
     * periodic polling.
     *
     * @param task
     * @return
     */
    @Override
    public boolean isAsyncComplete(Task task) {
        return true;
    }

    private void updateTaskStatus(Workflow subworkflow, Task task) {
        WorkflowStatus status = subworkflow.getStatus();
        switch (status) {
            case RUNNING:
            case PAUSED:
                task.setStatus(Status.IN_PROGRESS);
                break;
            case COMPLETED:
                task.setStatus(Status.COMPLETED);
                break;
            case FAILED:
                task.setStatus(Status.FAILED);
                break;
            case TERMINATED:
                task.setStatus(Status.CANCELED);
                break;
            case TIMED_OUT:
                task.setStatus(Status.TIMED_OUT);
                break;
            default:
                throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR,
                    "Subworkflow status does not conform to relevant task status.");
        }

        if (status.isTerminal()) {
            if (subworkflow.getExternalOutputPayloadStoragePath() != null) {
                task.setExternalOutputPayloadStoragePath(subworkflow.getExternalOutputPayloadStoragePath());
            } else {
                task.getOutputData().putAll(subworkflow.getOutput());
            }
            if(!status.isSuccessful()) {
                task.setReasonForIncompletion(String.format("Sub workflow %s failure reason: %s", subworkflow.toShortString(), subworkflow.getReasonForIncompletion()));
            }
        }
    }
}
