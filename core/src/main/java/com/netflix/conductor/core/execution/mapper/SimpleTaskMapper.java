package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflow;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SimpleTaskMapper implements TaskMapper {

    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;

    public SimpleTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }


    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();

        TaskDef taskDefinition = Optional.ofNullable(metadataDAO.getTaskDef(taskToSchedule.getName()))
                .orElseThrow(() -> {
                    String reason = "Invalid task specified.  Cannot find task by name " + taskToSchedule.getName() + " in the task definitions";
                    return new TerminateWorkflow(reason);
                });

        String taskId = IDGenerator.generate();
        Map<String, Object> input = parametersUtils.getTaskInput(taskToSchedule.getInputParameters(), workflowInstance, taskDefinition, taskId);
        //return SystemTask.createSimpleTask(workflowInstance, taskId, taskToSchedule, input, taskDefinition, retryCount);
        Task theTask = new Task();
        theTask.setStartDelayInSeconds(taskToSchedule.getStartDelay());
        theTask.setTaskId(taskId);
        theTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        theTask.setInputData(input);
        theTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        theTask.setStatus(Task.Status.SCHEDULED);
        theTask.setTaskType(taskToSchedule.getName());
        theTask.setTaskDefName(taskToSchedule.getName());
        theTask.setCorrelationId(workflowInstance.getCorrelationId());
        theTask.setScheduledTime(System.currentTimeMillis());
        theTask.setRetryCount(retryCount);
        theTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        theTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        theTask.setWorkflowTask(taskToSchedule);
        theTask.setRetriedTaskId(retriedTaskId);
        return Arrays.asList(theTask);
    }
}
