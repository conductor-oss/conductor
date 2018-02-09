package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.SystemTaskType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinTaskMapper implements TaskMapper {

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", taskToSchedule.getJoinOn());

        Task joinTask = new Task();
        joinTask.setTaskType(SystemTaskType.JOIN.name());
        joinTask.setTaskDefName(SystemTaskType.JOIN.name());
        joinTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        joinTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        joinTask.setCorrelationId(workflowInstance.getCorrelationId());
        joinTask.setScheduledTime(System.currentTimeMillis());
        joinTask.setEndTime(System.currentTimeMillis());
        joinTask.setInputData(joinInput);
        joinTask.setTaskId(taskId);
        joinTask.setStatus(Task.Status.IN_PROGRESS);
        joinTask.setWorkflowTask(taskToSchedule);

        return Arrays.asList(joinTask);
    }
}
