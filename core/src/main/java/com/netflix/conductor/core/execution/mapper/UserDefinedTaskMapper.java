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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * TaskType#USER_DEFINED} to a {@link TaskModel} of type {@link TaskType#USER_DEFINED} with {@link
 * TaskModel.Status#SCHEDULED}
 */
@Component
public class UserDefinedTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(UserDefinedTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public UserDefinedTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.USER_DEFINED;
    }

    /**
     * This method maps a {@link WorkflowTask} of type {@link TaskType#USER_DEFINED} to a {@link
     * TaskModel} in a {@link TaskModel.Status#SCHEDULED} state
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return a List with just one User defined task
     * @throws TerminateWorkflowException In case if the task definition does not exist
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in UserDefinedTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();
        int retryCount = taskMapperContext.getRetryCount();

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(
                                () ->
                                        Optional.ofNullable(
                                                        metadataDAO.getTaskDef(
                                                                taskToSchedule.getName()))
                                                .orElseThrow(
                                                        () -> {
                                                            String reason =
                                                                    String.format(
                                                                            "Invalid task specified. Cannot find task by name %s in the task definitions",
                                                                            taskToSchedule
                                                                                    .getName());
                                                            return new TerminateWorkflowException(
                                                                    reason);
                                                        }));

        Map<String, Object> input =
                parametersUtils.getTaskInputV2(
                        taskToSchedule.getInputParameters(),
                        workflowInstance,
                        taskId,
                        taskDefinition);

        TaskModel userDefinedTask = new TaskModel();
        userDefinedTask.setTaskType(taskToSchedule.getType());
        userDefinedTask.setTaskDefName(taskToSchedule.getName());
        userDefinedTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        userDefinedTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        userDefinedTask.setWorkflowType(workflowInstance.getWorkflowName());
        userDefinedTask.setCorrelationId(workflowInstance.getCorrelationId());
        userDefinedTask.setScheduledTime(System.currentTimeMillis());
        userDefinedTask.setTaskId(taskId);
        userDefinedTask.setInputData(input);
        userDefinedTask.setStatus(TaskModel.Status.SCHEDULED);
        userDefinedTask.setRetryCount(retryCount);
        userDefinedTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        userDefinedTask.setWorkflowTask(taskToSchedule);
        userDefinedTask.setWorkflowPriority(workflowInstance.getPriority());
        userDefinedTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        userDefinedTask.setRateLimitFrequencyInSeconds(
                taskDefinition.getRateLimitFrequencyInSeconds());
        return Collections.singletonList(userDefinedTask);
    }
}
