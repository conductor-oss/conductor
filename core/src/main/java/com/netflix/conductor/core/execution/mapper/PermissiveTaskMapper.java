/*
 * Copyright 2023 Netflix, Inc.
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
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#PERMISSIVE} to a {@link TaskModel} with status {@link TaskModel.Status#SCHEDULED}.
 */
@Component
public class PermissiveTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(PermissiveTaskMapper.class);
    private final ParametersUtils parametersUtils;

    public PermissiveTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.PERMISSIVE.name();
    }

    /**
     * This method maps a {@link WorkflowTask} of type {@link TaskType#PERMISSIVE} to a {@link
     * TaskModel}
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return a List with just one exclusive task
     * @throws TerminateWorkflowException In case if the task definition does not exist
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in PermissiveTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();

        TaskDef taskDefinition =
                Optional.ofNullable(workflowTask.getTaskDefinition())
                        .orElseThrow(
                                () -> {
                                    String reason =
                                            String.format(
                                                    "Invalid task. Task %s does not have a definition",
                                                    workflowTask.getName());
                                    return new TerminateWorkflowException(reason);
                                });

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        workflowTask.getInputParameters(),
                        workflowModel,
                        taskDefinition,
                        taskMapperContext.getTaskId());
        TaskModel permissiveTask = taskMapperContext.createTaskModel();
        permissiveTask.setTaskType(workflowTask.getName());
        permissiveTask.setStartDelayInSeconds(workflowTask.getStartDelay());
        permissiveTask.setInputData(input);
        permissiveTask.setStatus(TaskModel.Status.SCHEDULED);
        permissiveTask.setRetryCount(retryCount);
        permissiveTask.setCallbackAfterSeconds(workflowTask.getStartDelay());
        permissiveTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        permissiveTask.setRetriedTaskId(retriedTaskId);
        permissiveTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        permissiveTask.setRateLimitFrequencyInSeconds(
                taskDefinition.getRateLimitFrequencyInSeconds());
        return List.of(permissiveTask);
    }
}
