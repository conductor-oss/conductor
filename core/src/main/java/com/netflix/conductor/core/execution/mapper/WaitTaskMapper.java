package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.tasks.Wait;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WaitTaskMapper implements TaskMapper {

    private ParametersUtils parametersUtils;

    public WaitTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> waitTaskInput = parametersUtils.getTaskInputV2(taskMapperContext.getTaskToSchedule().getInputParameters(),
                workflowInstance, taskId, null);

        Task waitTask = new Task();
        waitTask.setTaskType(Wait.NAME);
        waitTask.setTaskDefName(taskMapperContext.getTaskToSchedule().getName());
        waitTask.setReferenceTaskName(taskMapperContext.getTaskToSchedule().getTaskReferenceName());
        waitTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        waitTask.setCorrelationId(workflowInstance.getCorrelationId());
        waitTask.setScheduledTime(System.currentTimeMillis());
        waitTask.setEndTime(System.currentTimeMillis());
        waitTask.setInputData(waitTaskInput);
        waitTask.setTaskId(taskId);
        waitTask.setStatus(Task.Status.IN_PROGRESS);
        waitTask.setWorkflowTask(taskToSchedule);
        return Arrays.asList(waitTask);
    }
}
