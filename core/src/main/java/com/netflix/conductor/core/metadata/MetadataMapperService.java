package com.netflix.conductor.core.metadata;

import com.google.inject.Singleton;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;

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

    public WorkflowDef populateTaskDefinitions(WorkflowDef workflowDefinition) {

        // Populate definitions on the workflow definition
        workflowDefinition.collectTasks().stream().forEach(
                workflowTask -> {
                    if (shouldPopulateDefinition(workflowTask)) {
                        workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
                    } else if (workflowTask.getType().equals(TaskType.SUB_WORKFLOW.name())) {
                        populateVersionForSubWorkflow(workflowTask);
                    }
                }
        );

        checkForMissingDefinitions(workflowDefinition);

        return workflowDefinition;
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

    private void checkForMissingDefinitions(WorkflowDef workflowDefinition) {

        // Obtain the names of the tasks with missing definitions
        Set<String> missingTaskDefinitionNames = workflowDefinition.collectTasks().stream()
                .filter(workflowTask -> shouldPopulateDefinition(workflowTask))
                .map(workflowTask -> workflowTask.getName())
                .collect(Collectors.toSet());

        if (!missingTaskDefinitionNames.isEmpty()) {
            logger.error("Cannot find the task definitions for the following tasks used in workflow: {}", missingTaskDefinitionNames);
            Monitors.recordWorkflowStartError(workflowDefinition.getName(), WorkflowContext.get().getClientApp());
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, "Cannot find the task definitions for the following tasks used in workflow: " + missingTaskDefinitionNames);
        }
    }

    public static boolean shouldPopulateDefinition(WorkflowTask workflowTask) {
        return workflowTask.getType().equals(TaskType.SIMPLE.name()) &&
                workflowTask.getTaskDefinition() == null;
    }

}
