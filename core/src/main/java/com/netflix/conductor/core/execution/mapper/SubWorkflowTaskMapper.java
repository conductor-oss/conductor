package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.dao.MetadataDAO;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubWorkflowTaskMapper implements TaskMapper {

    private ParametersUtils parametersUtils;

    private MetadataDAO metadataDAO;

    @Inject
    public SubWorkflowTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        SubWorkflowParams subWorkflowParams = taskToSchedule.getSubWorkflowParam();
        if (subWorkflowParams == null) {
            throw new TerminateWorkflow("Task " + taskToSchedule.getName() + " is defined as sub-workflow and is missing subWorkflowParams.  Please check the blueprint");
        }
        String name = subWorkflowParams.getName();
        Object version = subWorkflowParams.getVersion();
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        if (version != null) {
            params.put("version", version.toString());
        }
        Map<String, Object> resolvedParams = parametersUtils.getTaskInputV2(params, workflowInstance, null, null);
        String subWorkflowName = resolvedParams.get("name").toString();
        version = resolvedParams.get("version");
        int subWorkflowVersion;
        if (version == null) {
            try {
                subWorkflowVersion = metadataDAO.getLatest(subWorkflowName).getVersion();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            subWorkflowVersion = Integer.parseInt(version.toString());
        }

        Task subWorkflowTask = new Task();
        subWorkflowTask.setTaskType(SubWorkflow.NAME);
        subWorkflowTask.setTaskDefName(taskToSchedule.getName());
        subWorkflowTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        subWorkflowTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        subWorkflowTask.setCorrelationId(workflowInstance.getCorrelationId());
        subWorkflowTask.setScheduledTime(System.currentTimeMillis());
        subWorkflowTask.setEndTime(System.currentTimeMillis());
        subWorkflowTask.getInputData().put("subWorkflowName", subWorkflowName);
        subWorkflowTask.getInputData().put("subWorkflowVersion", subWorkflowVersion);
        subWorkflowTask.getInputData().put("workflowInput", taskMapperContext.getTaskInput());
        subWorkflowTask.setTaskId(taskId);
        subWorkflowTask.setStatus(Task.Status.SCHEDULED);
        subWorkflowTask.setWorkflowTask(taskToSchedule);
        return Arrays.asList(subWorkflowTask);
    }
}
