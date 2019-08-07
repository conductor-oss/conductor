package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.tasks.Terminate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

public class TerminateTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(TerminateTaskMapper.class);
    private ParametersUtils parametersUtils;

    public TerminateTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        logger.debug("TaskMapperContext {} in TerminateTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> taskInput = parametersUtils.getTaskInputV2(taskMapperContext.getTaskToSchedule().getInputParameters(), workflowInstance, taskId, null);

        Task task = new Task();
        task.setTaskType(Terminate.TASK_NAME);
        task.setTaskDefName(taskToSchedule.getName());
        task.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        task.setWorkflowType(workflowInstance.getWorkflowName());
        task.setCorrelationId(workflowInstance.getCorrelationId());
        task.setScheduledTime(System.currentTimeMillis());
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        task.setTaskId(taskId);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setWorkflowTask(taskToSchedule);
        task.setWorkflowPriority(workflowInstance.getPriority());
        return singletonList(task);
    }
}

