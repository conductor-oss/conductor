/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.mapper;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * TaskType#HTTP} to a {@link TaskModel} of type {@link TaskType#HTTP} with {@link
 * TaskModel.Status#SCHEDULED}
 */
@Component
public class HTTPTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    @Autowired
    public HTTPTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.HTTP;
    }

    /**
     * This method maps a {@link WorkflowTask} of type {@link TaskType#HTTP} to a {@link TaskModel}
     * in a {@link TaskModel.Status#SCHEDULED} state
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return a List with just one HTTP task
     * @throws TerminateWorkflowException In case if the task definition does not exist
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in HTTPTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        taskToSchedule.getInputParameters().put("asyncComplete", taskToSchedule.isAsyncComplete());
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();
        int retryCount = taskMapperContext.getRetryCount();

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(() -> metadataDAO.getTaskDef(taskToSchedule.getName()));

        Map<String, Object> input =
                parametersUtils.getTaskInputV2(
                        taskToSchedule.getInputParameters(),
                        workflowInstance,
                        taskId,
                        taskDefinition);
        Boolean asynComplete = (Boolean) input.get("asyncComplete");

        TaskModel httpTask = new TaskModel();
        httpTask.setTaskType(taskToSchedule.getType());
        httpTask.setTaskDefName(taskToSchedule.getName());
        httpTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        httpTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        httpTask.setWorkflowType(workflowInstance.getWorkflowName());
        httpTask.setCorrelationId(workflowInstance.getCorrelationId());
        httpTask.setScheduledTime(System.currentTimeMillis());
        httpTask.setTaskId(taskId);
        httpTask.setInputData(input);
        httpTask.getInputData().put("asyncComplete", asynComplete);
        httpTask.setStatus(TaskModel.Status.SCHEDULED);
        httpTask.setRetryCount(retryCount);
        httpTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        httpTask.setWorkflowTask(taskToSchedule);
        httpTask.setWorkflowPriority(workflowInstance.getPriority());
        if (Objects.nonNull(taskDefinition)) {
            httpTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
            httpTask.setRateLimitFrequencyInSeconds(
                    taskDefinition.getRateLimitFrequencyInSeconds());
            httpTask.setIsolationGroupId(taskDefinition.getIsolationGroupId());
            httpTask.setExecutionNameSpace(taskDefinition.getExecutionNameSpace());
        }
        return Collections.singletonList(httpTask);
    }
}
