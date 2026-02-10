/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.core.execution.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * TaskMapper for @WorkerTask annotated system tasks. Unlike user-defined tasks, annotated system
 * tasks do not require a task definition and can be scheduled directly from the workflow
 * definition. If a task definition exists, it will be used for rate limiting and other settings.
 */
public class AnnotatedSystemTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedSystemTaskMapper.class);

    private final String taskType;
    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public AnnotatedSystemTaskMapper(
            String taskType, ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.taskType = taskType;
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public String getTaskType() {
        return taskType;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in AnnotatedSystemTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();

        // Try to get task definition - don't fail if not found (unlike
        // SimpleTaskMapper)
        TaskDef taskDefinition =
                Optional.ofNullable(workflowTask.getTaskDefinition())
                        .orElseGet(() -> metadataDAO.getTaskDef(workflowTask.getName()));

        // Get input - pass taskDefinition (may be null)
        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        workflowTask.getInputParameters(),
                        workflowModel,
                        taskDefinition,
                        taskMapperContext.getTaskId());

        // Create task model
        TaskModel task = taskMapperContext.createTaskModel();
        task.setTaskType(taskType);
        task.setStartDelayInSeconds(workflowTask.getStartDelay());
        task.setInputData(input);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setRetryCount(retryCount);
        task.setCallbackAfterSeconds(workflowTask.getStartDelay());
        task.setRetriedTaskId(retriedTaskId);

        // If task definition exists, use its settings for rate limiting etc.
        if (Objects.nonNull(taskDefinition)) {
            task.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
            task.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
            task.setRateLimitFrequencyInSeconds(taskDefinition.getRateLimitFrequencyInSeconds());
        }

        return List.of(task);
    }
}
