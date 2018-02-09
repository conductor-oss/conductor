package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.tasks.Event;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EventTaskMapper implements TaskMapper {


    private ParametersUtils parametersUtils;

    public EventTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        taskToSchedule.getInputParameters().put("sink", taskToSchedule.getSink());
        Map<String, Object> eventTaskInput = parametersUtils.getTaskInputV2(taskToSchedule.getInputParameters(), workflowInstance, taskId, null);
        String sink = (String) eventTaskInput.get("sink");

        Task eventTask = new Task();
        eventTask.setTaskType(Event.NAME);
        eventTask.setTaskDefName(taskToSchedule.getName());
        eventTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        eventTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        eventTask.setCorrelationId(workflowInstance.getCorrelationId());
        eventTask.setScheduledTime(System.currentTimeMillis());
        eventTask.setEndTime(System.currentTimeMillis());
        eventTask.setInputData(eventTaskInput);
        eventTask.getInputData().put("sink", sink);
        eventTask.setTaskId(taskId);
        eventTask.setStatus(Task.Status.SCHEDULED);
        eventTask.setWorkflowTask(taskToSchedule);

        return Arrays.asList(eventTask);
    }
}
