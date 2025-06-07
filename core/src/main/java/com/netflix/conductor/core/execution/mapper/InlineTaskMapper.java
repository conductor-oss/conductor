/*
 * Copyright 2022 Conductor Authors.
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
import java.util.Objects;
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
import com.netflix.conductor.service.TimeService;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#INLINE} to a List {@link TaskModel} starting with Task of type {@link TaskType#INLINE}
 * which is marked as IN_PROGRESS, followed by the list of {@link TaskModel} based on the case
 * expression evaluation in the Inline task.
 */
@Component
public class InlineTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(InlineTaskMapper.class);
    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public InlineTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public String getTaskType() {
        return TaskType.INLINE.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in InlineTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(() -> metadataDAO.getTaskDef(workflowTask.getName()));

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        taskMapperContext.getWorkflowTask().getInputParameters(),
                        workflowModel,
                        taskId,
                        taskDefinition);

        TaskModel inlineTask = taskMapperContext.createTaskModel();
        inlineTask.setTaskType(TaskType.TASK_TYPE_INLINE);
        inlineTask.setStartTime(TimeService.currentTimeMillis());
        inlineTask.setInputData(taskInput);
        if (Objects.nonNull(taskMapperContext.getTaskDefinition())) {
            inlineTask.setIsolationGroupId(
                    taskMapperContext.getTaskDefinition().getIsolationGroupId());
        }
        inlineTask.setStatus(TaskModel.Status.IN_PROGRESS);

        return List.of(inlineTask);
    }
}
