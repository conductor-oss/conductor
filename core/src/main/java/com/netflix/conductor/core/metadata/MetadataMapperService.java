package com.netflix.conductor.core.metadata;

import com.google.common.annotations.VisibleForTesting;
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
import com.netflix.conductor.service.utils.ServiceUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Populates metadata definitions within workflow objects.
 * Benefits of loading and populating metadata definitions upfront could be:
 * - Immutable definitions within a workflow execution with the added benefit of guaranteeing consistency at runtime.
 * - Stress is reduced on the storage layer
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
        ServiceUtils.checkNotNullOrEmpty(workflowName, "Workflow name must be specified when searching for a definition");
        return metadataDAO.get(workflowName, workflowVersion);
    }

    @VisibleForTesting
    Optional<WorkflowDef> lookupLatestWorkflowDefinition(String workflowName) {
        ServiceUtils.checkNotNullOrEmpty(workflowName, "Workflow name must be specified when searching for a definition");
        return metadataDAO.getLatest(workflowName);
    }

    public Workflow populateWorkflowWithDefinitions(Workflow workflow) {

        WorkflowDef workflowDefinition = Optional.ofNullable(workflow.getWorkflowDefinition())
                .orElseGet(() -> {
                    WorkflowDef wd = lookupForWorkflowDefinition(workflow.getWorkflowName(), workflow.getWorkflowVersion());
                    workflow.setWorkflowDefinition(wd);
                    return wd;
                });

        workflowDefinition.collectTasks().forEach(
                workflowTask -> {
                    if (shouldPopulateDefinition(workflowTask)) {
                        workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
                    } else if (workflowTask.getType().equals(TaskType.SUB_WORKFLOW.name())) {
                        populateVersionForSubWorkflow(workflowTask);
                    }
                }
        );

        checkNotEmptyDefinitions(workflowDefinition);

        return workflow;
    }

    public WorkflowDef populateTaskDefinitions(WorkflowDef workflowDefinition) {
        workflowDefinition.collectTasks().forEach(
                this::populateWorkflowTaskWithDefinition
        );
        checkNotEmptyDefinitions(workflowDefinition);
        return workflowDefinition;
    }

    private WorkflowTask populateWorkflowTaskWithDefinition(WorkflowTask workflowTask) {
        if (shouldPopulateDefinition(workflowTask)) {
            workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
        } else if (workflowTask.getType().equals(TaskType.SUB_WORKFLOW.name())) {
            populateVersionForSubWorkflow(workflowTask);
        }
        return workflowTask;
    }

    private void populateVersionForSubWorkflow(WorkflowTask workflowTask) {
        SubWorkflowParams subworkflowParams = workflowTask.getSubWorkflowParam();
        if (subworkflowParams.getVersion() == null) {
            String subWorkflowName = subworkflowParams.getName();
            Integer subWorkflowVersion =
                    metadataDAO.getLatest(subWorkflowName)
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

        // Obtain the names of the tasks with missing definitions
        Set<String> missingTaskDefinitionNames = workflowDefinition.collectTasks().stream()
                .filter(MetadataMapperService::shouldPopulateDefinition)
                .map(WorkflowTask::getName)
                .collect(Collectors.toSet());

        if (!missingTaskDefinitionNames.isEmpty()) {
            logger.error("Cannot find the task definitions for the following tasks used in workflow: {}", missingTaskDefinitionNames);
            Monitors.recordWorkflowStartError(workflowDefinition.getName(), WorkflowContext.get().getClientApp());
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, "Cannot find the task definitions for the following tasks used in workflow: " + missingTaskDefinitionNames);
        }
    }

    public Task populateTaskWithDefinition(Task task) {
        populateWorkflowTaskWithDefinition(task.getWorkflowTask());
        return task;
    }

    public static boolean shouldPopulateDefinition(WorkflowTask workflowTask) {
        return workflowTask.getType().equals(TaskType.SIMPLE.name()) &&
                workflowTask.getTaskDefinition() == null;
    }

}
