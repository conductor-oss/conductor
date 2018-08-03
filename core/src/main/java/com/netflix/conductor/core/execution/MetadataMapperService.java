package com.netflix.conductor.core.execution;

import com.google.inject.Singleton;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

@Singleton
public class MetadataMapperService {

    public static final Logger logger = LoggerFactory.getLogger(MetadataMapperService.class);

    private MetadataDAO metadataDAO;

    @Inject
    public MetadataMapperService(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    public WorkflowDef populateTaskDefinitions(WorkflowDef workflowDefinition) {
        List<WorkflowTask> workflowTasks = workflowDefinition.collectTasks();
        for (WorkflowTask workflowTask : workflowTasks) {


            if (workflowTask.getType().equals(WorkflowTask.Type.SIMPLE.name()) && workflowTask.getTaskDefinition() == null) {
                workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
            } else if (workflowTask.getType().equals(WorkflowTask.Type.SUB_WORKFLOW.name())) {
                populateVersionForSubWorkflow(workflowTask);
            }
        }
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

}
