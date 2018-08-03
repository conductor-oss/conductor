package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;

import javax.inject.Inject;
import java.util.List;

public class MetadataMapperService {

    private MetadataDAO metadataDAO;

    @Inject
    public MetadataMapperService(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    public WorkflowDef populateTaskDefinitionsMetadata(WorkflowDef workflowDefinition) {
        List<WorkflowTask> workflowTasks = workflowDefinition.all();
        for (WorkflowTask workflowTask : workflowTasks) {

            if (workflowTask.getType().equals(WorkflowTask.Type.SIMPLE.name()) && workflowTask.getTaskDefinition() == null) {
                workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
            }
        }
        return workflowDefinition;
    }

}
