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
package com.netflix.conductor.core.operation;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.event.WorkflowCreationEvent;
import com.netflix.conductor.core.event.WorkflowEvaluationEvent;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

@Component
public class StartWorkflowOperation implements WorkflowOperation<StartWorkflowInput, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartWorkflowOperation.class);

    private final MetadataMapperService metadataMapperService;
    private final IDGenerator idGenerator;
    private final ParametersUtils parametersUtils;
    private final ExecutionDAOFacade executionDAOFacade;
    private final ExecutionLockService executionLockService;
    private final ApplicationEventPublisher eventPublisher;

    public StartWorkflowOperation(
            MetadataMapperService metadataMapperService,
            IDGenerator idGenerator,
            ParametersUtils parametersUtils,
            ExecutionDAOFacade executionDAOFacade,
            ExecutionLockService executionLockService,
            ApplicationEventPublisher eventPublisher) {
        this.metadataMapperService = metadataMapperService;
        this.idGenerator = idGenerator;
        this.parametersUtils = parametersUtils;
        this.executionDAOFacade = executionDAOFacade;
        this.executionLockService = executionLockService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String execute(StartWorkflowInput input) {
        return startWorkflow(input);
    }

    @EventListener(WorkflowCreationEvent.class)
    public void handleWorkflowCreationEvent(WorkflowCreationEvent workflowCreationEvent) {
        startWorkflow(workflowCreationEvent.getStartWorkflowInput());
    }

    private String startWorkflow(StartWorkflowInput input) {
        WorkflowDef workflowDefinition;

        if (input.getWorkflowDefinition() == null) {
            workflowDefinition =
                    metadataMapperService.lookupForWorkflowDefinition(
                            input.getName(), input.getVersion());
        } else {
            workflowDefinition = input.getWorkflowDefinition();
        }

        workflowDefinition = metadataMapperService.populateTaskDefinitions(workflowDefinition);

        // perform validations
        Map<String, Object> workflowInput = input.getWorkflowInput();
        String externalInputPayloadStoragePath = input.getExternalInputPayloadStoragePath();
        validateWorkflow(workflowDefinition, workflowInput, externalInputPayloadStoragePath);

        // Generate ID if it's not present
        String workflowId =
                Optional.ofNullable(input.getWorkflowId()).orElseGet(idGenerator::generate);

        // Persist the Workflow
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setCorrelationId(input.getCorrelationId());
        workflow.setPriority(input.getPriority() == null ? 0 : input.getPriority());
        workflow.setWorkflowDefinition(workflowDefinition);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setParentWorkflowId(input.getParentWorkflowId());
        workflow.setParentWorkflowTaskId(input.getParentWorkflowTaskId());
        workflow.setOwnerApp(WorkflowContext.get().getClientApp());
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setUpdatedBy(null);
        workflow.setUpdatedTime(null);
        workflow.setEvent(input.getEvent());
        workflow.setTaskToDomain(input.getTaskToDomain());
        workflow.setVariables(workflowDefinition.getVariables());

        if (workflowInput != null && !workflowInput.isEmpty()) {
            Map<String, Object> parsedInput =
                    parametersUtils.getWorkflowInput(workflowDefinition, workflowInput);
            workflow.setInput(parsedInput);
        } else {
            workflow.setExternalInputPayloadStoragePath(externalInputPayloadStoragePath);
        }

        try {
            createAndEvaluate(workflow);
            Monitors.recordWorkflowStartSuccess(
                    workflow.getWorkflowName(),
                    String.valueOf(workflow.getWorkflowVersion()),
                    workflow.getOwnerApp());
            return workflowId;
        } catch (Exception e) {
            Monitors.recordWorkflowStartError(
                    workflowDefinition.getName(), WorkflowContext.get().getClientApp());
            LOGGER.error("Unable to start workflow: {}", workflowDefinition.getName(), e);

            // It's possible the remove workflow call hits an exception as well, in that case we
            // want to log both errors to help diagnosis.
            try {
                executionDAOFacade.removeWorkflow(workflowId, false);
            } catch (Exception rwe) {
                LOGGER.error("Could not remove the workflowId: " + workflowId, rwe);
            }
            throw e;
        }
    }

    /*
     * Acquire and hold the lock till the workflow creation action is completed (in primary and secondary datastores).
     * This is to ensure that workflow creation action precedes any other action on a given workflow.
     */
    private void createAndEvaluate(WorkflowModel workflow) {
        if (!executionLockService.acquireLock(workflow.getWorkflowId())) {
            throw new TransientException("Error acquiring lock when creating workflow: {}");
        }
        try {
            executionDAOFacade.createWorkflow(workflow);
            LOGGER.debug(
                    "A new instance of workflow: {} created with id: {}",
                    workflow.getWorkflowName(),
                    workflow.getWorkflowId());
            executionDAOFacade.populateWorkflowAndTaskPayloadData(workflow);
            eventPublisher.publishEvent(new WorkflowEvaluationEvent(workflow));
        } finally {
            executionLockService.releaseLock(workflow.getWorkflowId());
        }
    }

    /**
     * Performs validations for starting a workflow
     *
     * @throws IllegalArgumentException if the validation fails.
     */
    private void validateWorkflow(
            WorkflowDef workflowDef,
            Map<String, Object> workflowInput,
            String externalStoragePath) {
        // Check if the input to the workflow is not null
        if (workflowInput == null && StringUtils.isBlank(externalStoragePath)) {
            LOGGER.error("The input for the workflow '{}' cannot be NULL", workflowDef.getName());
            Monitors.recordWorkflowStartError(
                    workflowDef.getName(), WorkflowContext.get().getClientApp());

            throw new IllegalArgumentException("NULL input passed when starting workflow");
        }
    }
}
