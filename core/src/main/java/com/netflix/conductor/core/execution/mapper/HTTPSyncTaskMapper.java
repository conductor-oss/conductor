package com.netflix.conductor.core.execution.mapper;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#HTTP_SYNC} to a {@link TaskModel} of type {@link TaskType#HTTP_SYNC} with {@link
 * TaskModel.Status#SCHEDULED}
 */
@Component
public class HTTPSyncTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPSyncTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public HTTPSyncTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public String getTaskType() {
        return TaskType.HTTP_SYNC.name();
    }

    /**
     * This method maps a {@link WorkflowTask} of type {@link TaskType#HTTP_SYNC} to a {@link
     * TaskModel} in a {@link TaskModel.Status#SCHEDULED} state
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return a List with just one HTTP_SYNC task
     * @throws TerminateWorkflowException In case if the task definition does not exist
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in HTTPSyncTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();
        int retryCount = taskMapperContext.getRetryCount();

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(() -> metadataDAO.getTaskDef(workflowTask.getName()));

        Map<String, Object> input =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(), workflowModel, taskId, taskDefinition);

        TaskModel httpSyncTask = taskMapperContext.createTaskModel();
        httpSyncTask.setInputData(input);
        httpSyncTask.setStatus(TaskModel.Status.SCHEDULED);
        httpSyncTask.setRetryCount(retryCount);
        httpSyncTask.setCallbackAfterSeconds(workflowTask.getStartDelay());
        if (Objects.nonNull(taskDefinition)) {
            httpSyncTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
            httpSyncTask.setRateLimitFrequencyInSeconds(
                    taskDefinition.getRateLimitFrequencyInSeconds());
            httpSyncTask.setIsolationGroupId(taskDefinition.getIsolationGroupId());
            httpSyncTask.setExecutionNameSpace(taskDefinition.getExecutionNameSpace());
        }
        return List.of(httpSyncTask);
    }
}