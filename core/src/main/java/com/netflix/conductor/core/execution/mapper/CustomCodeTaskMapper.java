package com.netflix.conductor.core.execution.mapper;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component
public class CustomCodeTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(CustomCodeTaskMapper.class);
    private final ParametersUtils parametersUtils;

    public CustomCodeTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.CUSTOM_CODE.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in PythonScriptTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(), workflowModel, taskId, null);

        TaskModel customCodeTask = taskMapperContext.createTaskModel();
        customCodeTask.setTaskType(TaskType.CUSTOM_CODE.name());
        customCodeTask.setStartTime(System.currentTimeMillis());
        customCodeTask.setInputData(taskInput);
        customCodeTask.setStatus(TaskModel.Status.IN_PROGRESS);

        return List.of(customCodeTask);
    }
}