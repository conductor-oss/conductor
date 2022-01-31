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
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component
public class JsonJQTransformTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(JsonJQTransformTaskMapper.class);
    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public JsonJQTransformTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.JSON_JQ_TRANSFORM;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in JsonJQTransformTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(() -> metadataDAO.getTaskDef(taskToSchedule.getName()));

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        taskToSchedule.getInputParameters(),
                        workflowInstance,
                        taskId,
                        taskDefinition);

        TaskModel jsonJQTransformTask = new TaskModel();
        jsonJQTransformTask.setTaskType(taskToSchedule.getType());
        jsonJQTransformTask.setTaskDefName(taskToSchedule.getName());
        jsonJQTransformTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        jsonJQTransformTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        jsonJQTransformTask.setWorkflowType(workflowInstance.getWorkflowName());
        jsonJQTransformTask.setCorrelationId(workflowInstance.getCorrelationId());
        jsonJQTransformTask.setStartTime(System.currentTimeMillis());
        jsonJQTransformTask.setScheduledTime(System.currentTimeMillis());
        jsonJQTransformTask.setInputData(taskInput);
        jsonJQTransformTask.setTaskId(taskId);
        jsonJQTransformTask.setStatus(TaskModel.Status.IN_PROGRESS);
        jsonJQTransformTask.setWorkflowTask(taskToSchedule);
        jsonJQTransformTask.setWorkflowPriority(workflowInstance.getPriority());

        return Collections.singletonList(jsonJQTransformTask);
    }
}
