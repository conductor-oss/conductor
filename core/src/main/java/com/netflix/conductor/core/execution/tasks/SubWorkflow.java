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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    @SuppressWarnings("unchecked")
    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        // Guard against double-start: if the task has already moved past SCHEDULED state, a
        // sub-workflow was started on a prior call; skip re-creating it.
        // NOTE: we deliberately do NOT check task.getSubWorkflowId() here because
        // TaskModel.getSubWorkflowId() falls back to outputData, and retried tasks
        // inherit outputData from their failed predecessor — that would cause the guard
        // to trigger on a legitimately fresh retry attempt.
        if (task.getStatus() != TaskModel.Status.SCHEDULED) {
            LOGGER.warn(
                    "Sub-workflow task {} is already in state {}, skipping duplicate start.",
                    task.getTaskId(),
                    task.getStatus());
            return;
        }

        Map<String, Object> input = task.getInputData();

        // Null-safe version read: version may be absent when an inline workflowDefinition is
        // supplied (the mapper skips the MetadataDAO lookup in that case).
        // Use Number.intValue() — the same JSON round-trip issue that affects priority (Integer
        // stored, Double retrieved) applies here: parseInt("2.0") would throw and silently fall
        // back to null (= latest), causing the wrong sub-workflow version to be executed.
        Integer resolvedVersion = null;
        Object versionObj = input.get("subWorkflowVersion");
        if (versionObj instanceof Number) {
            int version = ((Number) versionObj).intValue();
            resolvedVersion = version == 0 ? null : version;
        }

        WorkflowDef workflowDefinition = null;
        String name;
        if (input.get("subWorkflowDefinition") != null) {
            // Convert the runtime Map to a WorkflowDef. This supports both the static
            // embedded-object form and the dynamic ${expr}-resolved form.
            workflowDefinition =
                    objectMapper.convertValue(
                            input.get("subWorkflowDefinition"), WorkflowDef.class);
            name = workflowDefinition.getName();
        } else {
            name =
                    input.get("subWorkflowName") != null
                            ? input.get("subWorkflowName").toString()
                            : null;
            if (name == null) {
                throw new NonTransientException(
                        "SubWorkflow name is null and no workflowDefinition supplied");
            }
        }

        Map<String, String> taskToDomain = workflow.getTaskToDomain();
        if (input.get("subWorkflowTaskToDomain") instanceof Map) {
            taskToDomain = (Map<String, String>) input.get("subWorkflowTaskToDomain");
        }

        var wfInput = (Map<String, Object>) input.get("workflowInput");
        if (wfInput == null || wfInput.isEmpty()) {
            wfInput = input;
        }

        // Mark dynamically-generated sub-workflows so they can be identified downstream.
        if (workflowDefinition != null) {
            wfInput = new HashMap<>(wfInput);
            Map<String, Object> systemMetadata =
                    wfInput.get("_systemMetadata") instanceof Map
                            ? new HashMap<>((Map<String, Object>) wfInput.get("_systemMetadata"))
                            : new HashMap<>();
            systemMetadata.put("dynamic", true);
            wfInput.put("_systemMetadata", systemMetadata);
        }

        // Priority: forward only when explicitly set in subWorkflowParams; otherwise leave null
        // so the sub-workflow inherits the server default (avoids breaking existing behaviour).
        // Use Number.intValue() to handle both Integer and Double (the latter appears after JSON
        // round-trips through the data store — e.g. "7" stored as 7.0 cannot be parseInt'd).
        Integer priority = null;
        Object priorityObj = input.get("priority");
        if (priorityObj instanceof Number) {
            priority = ((Number) priorityObj).intValue();
        }

        // Idempotency: read key and strategy forwarded by the mapper (fields present in
        // SubWorkflowParams and now propagated end-to-end; enforcement is implementation-specific).
        String idempotencyKey = null;
        if (input.get("idempotencyKey") != null) {
            idempotencyKey = String.valueOf(input.get("idempotencyKey"));
        }
        IdempotencyStrategy idempotencyStrategy = null;
        if (input.get("idempotencyStrategy") != null) {
            try {
                idempotencyStrategy =
                        IdempotencyStrategy.valueOf(
                                String.valueOf(input.get("idempotencyStrategy")));
            } catch (IllegalArgumentException ignored) {
                LOGGER.warn(
                        "Unknown idempotencyStrategy '{}' for task {} in {} — ignoring.",
                        input.get("idempotencyStrategy"),
                        task.getTaskId(),
                        workflow.toShortString());
            }
        }

        String correlationId = workflow.getCorrelationId();

        try {
            StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
            startWorkflowInput.setWorkflowDefinition(workflowDefinition);
            startWorkflowInput.setName(name);
            startWorkflowInput.setVersion(resolvedVersion);
            startWorkflowInput.setWorkflowInput(wfInput);
            startWorkflowInput.setCorrelationId(correlationId);
            startWorkflowInput.setParentWorkflowId(workflow.getWorkflowId());
            startWorkflowInput.setParentWorkflowTaskId(task.getTaskId());
            startWorkflowInput.setTaskToDomain(taskToDomain);
            startWorkflowInput.setPriority(priority);
            startWorkflowInput.setIdempotencyKey(idempotencyKey);
            startWorkflowInput.setIdempotencyStrategy(idempotencyStrategy);

            String subWorkflowId = workflowExecutor.startWorkflow(startWorkflowInput);

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
        if (subWorkflow == null) {
            // Sub-workflow may have already been deleted (e.g. data-store TTL expired).
            LOGGER.warn(
                    "Cannot execute sub-workflow {} — not found in store (already deleted?).",
                    workflowId);
            return false;
        }
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
        if (subWorkflow == null) {
            // Sub-workflow may have already been deleted (e.g. data-store TTL expired).
            LOGGER.warn(
                    "Cannot cancel sub-workflow {} — not found in store (already deleted?).",
                    workflowId);
            return;
        }
        subWorkflow.setStatus(WorkflowModel.Status.TERMINATED);
        String reason =
                StringUtils.isEmpty(workflow.getReasonForIncompletion())
                        ? "Parent workflow has been terminated with status " + workflow.getStatus()
                        : "Parent workflow has been terminated with reason: "
                                + workflow.getReasonForIncompletion();
        workflowExecutor.terminateWorkflow(subWorkflow, reason, null);
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
