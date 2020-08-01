/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.metadata;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Populates metadata definitions within workflow objects.
 * Benefits of loading and populating metadata definitions upfront could be:
 * <ul>
 *  <li> Immutable definitions within a workflow execution with the added benefit of guaranteeing consistency at runtime.</li>
 *  <li> Stress is reduced on the storage layer</li>
 * </ul>
 */
@Singleton
public class MetadataMapperService {

    public static final Logger logger = LoggerFactory.getLogger(MetadataMapperService.class);

    private final MetadataDAO metadataDAO;

    @Inject
    public MetadataMapperService(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    public WorkflowDef lookupForWorkflowDefinition(String name, Integer version) {
        Optional<WorkflowDef> potentialDef =
                version == null ? lookupLatestWorkflowDefinition(name) : lookupWorkflowDefinition(name, version);

        //Check if the workflow definition is valid
        WorkflowDef workflowDefinition = potentialDef
                .orElseThrow(() -> {
                            logger.error("There is no workflow defined with name {} and version {}", name, version);
                            return new ApplicationException(
                                    ApplicationException.Code.NOT_FOUND,
                                    String.format("No such workflow defined. name=%s, version=%s", name, version)
                            );
                        }
                );
        return workflowDefinition;
    }

    @VisibleForTesting
    Optional<WorkflowDef> lookupWorkflowDefinition(String workflowName, int workflowVersion) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowName), "Workflow name must be specified when searching for a definition");
        return metadataDAO.getWorkflowDef(workflowName, workflowVersion);
    }

    @VisibleForTesting
    Optional<WorkflowDef> lookupLatestWorkflowDefinition(String workflowName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowName), "Workflow name must be specified when searching for a definition");
        return metadataDAO.getLatestWorkflowDef(workflowName);
    }

    public Workflow populateWorkflowWithDefinitions(Workflow workflow) {
        Preconditions.checkNotNull(workflow, "workflow cannot be null");
        WorkflowDef workflowDefinition = Optional.ofNullable(workflow.getWorkflowDefinition())
                .orElseGet(() -> {
                    WorkflowDef wd = lookupForWorkflowDefinition(workflow.getWorkflowName(), workflow.getWorkflowVersion());
                    workflow.setWorkflowDefinition(wd);
                    return wd;
                });

        workflowDefinition.collectTasks().forEach(this::populateWorkflowTaskWithDefinition);
        checkNotEmptyDefinitions(workflowDefinition);

        return workflow;
    }

    public WorkflowDef populateTaskDefinitions(WorkflowDef workflowDefinition) {
        Preconditions.checkNotNull(workflowDefinition, "workflowDefinition cannot be null");
        workflowDefinition.collectTasks().forEach(
                this::populateWorkflowTaskWithDefinition
        );
        checkNotEmptyDefinitions(workflowDefinition);
        return workflowDefinition;
    }

    private void populateWorkflowTaskWithDefinition(WorkflowTask workflowTask) {
        Preconditions.checkNotNull(workflowTask, "WorkflowTask cannot be null");
        if (shouldPopulateTaskDefinition(workflowTask)) {
            workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
        }
        if (workflowTask.getType().equals(TaskType.SUB_WORKFLOW.name())) {
            populateVersionForSubWorkflow(workflowTask);
        }
    }

    private void populateVersionForSubWorkflow(WorkflowTask workflowTask) {
        Preconditions.checkNotNull(workflowTask, "WorkflowTask cannot be null");
        SubWorkflowParams subworkflowParams = workflowTask.getSubWorkflowParam();
        if (subworkflowParams.getVersion() == null) {
            String subWorkflowName = subworkflowParams.getName();
            Integer subWorkflowVersion =
                    metadataDAO.getLatestWorkflowDef(subWorkflowName)
                            .map(WorkflowDef::getVersion)
                            .orElseThrow(
                                    () -> {
                                        String reason = String.format("The Task %s defined as a sub-workflow has no workflow definition available ", subWorkflowName);
                                        logger.error(reason);
                                        return new TerminateWorkflowException(reason);
                                    }
                            );
            subworkflowParams.setVersion(subWorkflowVersion);
        }
    }

    private void checkNotEmptyDefinitions(WorkflowDef workflowDefinition) {
        Preconditions.checkNotNull(workflowDefinition, "WorkflowDefinition cannot be null");

        // Obtain the names of the tasks with missing definitions
        Set<String> missingTaskDefinitionNames = workflowDefinition.collectTasks().stream()
                .filter(workflowTask -> workflowTask.getType().equals(TaskType.SIMPLE.name()))
                .filter(this::shouldPopulateTaskDefinition)
                .map(WorkflowTask::getName)
                .collect(Collectors.toSet());

        if (!missingTaskDefinitionNames.isEmpty()) {
            logger.error("Cannot find the task definitions for the following tasks used in workflow: {}", missingTaskDefinitionNames);
            Monitors.recordWorkflowStartError(workflowDefinition.getName(), WorkflowContext.get().getClientApp());
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, "Cannot find the task definitions for the following tasks used in workflow: " + missingTaskDefinitionNames);
        }
    }

    public Task populateTaskWithDefinition(Task task) {
        Preconditions.checkNotNull(task, "Task cannot be null");
        populateWorkflowTaskWithDefinition(task.getWorkflowTask());
        return task;
    }

    @VisibleForTesting
    boolean shouldPopulateTaskDefinition(WorkflowTask workflowTask) {
        Preconditions.checkNotNull(workflowTask, "WorkflowTask cannot be null");
        Preconditions.checkNotNull(workflowTask.getType(), "WorkflowTask type cannot be null");
        return workflowTask.getTaskDefinition() == null && StringUtils.isNotBlank(workflowTask.getName());
    }
}
