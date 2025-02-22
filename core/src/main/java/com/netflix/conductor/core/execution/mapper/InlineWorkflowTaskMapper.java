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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#INLINE_WORKFLOW} to a {@link TaskModel} of type {@link TaskType#INLINE_WORKFLOW}
 */
@Component
public class InlineWorkflowTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineWorkflowTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final Map<String, TaskMapper> taskMappers;
    private final IDGenerator idGenerator;

    public InlineWorkflowTaskMapper(
            ParametersUtils parametersUtils,
            List<TaskMapper> taskMappers,
            IDGenerator idGenerator) {
        this.parametersUtils = parametersUtils;
        this.taskMappers = new HashMap<>();
        for (TaskMapper taskMapper : taskMappers) {
            this.taskMappers.put(taskMapper.getTaskType(), taskMapper);
        }
        this.idGenerator = idGenerator;
    }

    @Override
    public String getTaskType() {
        return TaskType.INLINE_WORKFLOW.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in InlineWorkflowTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();

        TaskModel task = workflowModel.getTaskByRefName(workflowTask.getTaskReferenceName());
        if (task != null && task.getStatus().isTerminal()) {
            // Since loopTask is already completed no need to schedule task again.
            return List.of();
        }

        TaskModel inlineWorkflowTask = taskMapperContext.createTaskModel();
        inlineWorkflowTask.setTaskType(TaskType.TASK_TYPE_INLINE_WORKFLOW);
        inlineWorkflowTask.setStatus(TaskModel.Status.IN_PROGRESS);
        inlineWorkflowTask.setStartTime(System.currentTimeMillis());
        inlineWorkflowTask.setRetryCount(taskMapperContext.getRetryCount());

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(),
                        workflowModel,
                        inlineWorkflowTask.getTaskId(),
                        null);
        inlineWorkflowTask.setInputData(taskInput);

        WorkflowTask firstTask = workflowTask.getInlineWorkflow().getTasks().get(0);

        TaskMapper firstTaskMapper = taskMappers.get(firstTask.getType());
        String firstTaskId = idGenerator.generate();
        TaskMapperContext firstTaskMapperContext =
                TaskMapperContext.newBuilder(taskMapperContext)
                        .withWorkflowTask(firstTask)
                        .withTaskId(firstTaskId)
                        .withWorkflowModel(workflowModel)
                        .build();

        List<TaskModel> tasks = new ArrayList<>();
        tasks.add(inlineWorkflowTask);
        tasks.addAll(firstTaskMapper.getMappedTasks(firstTaskMapperContext));
        return tasks;
    }
}
