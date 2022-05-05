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

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#DO_WHILE} to a {@link TaskModel} of type {@link TaskType#DO_WHILE}
 */
@Component
public class DoWhileTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoWhileTaskMapper.class);

    private final MetadataDAO metadataDAO;

    @Autowired
    public DoWhileTaskMapper(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.DO_WHILE;
    }

    /**
     * This method maps {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
     * TaskType#DO_WHILE} to a {@link TaskModel} of type {@link TaskType#DO_WHILE} with a status of
     * {@link TaskModel.Status#IN_PROGRESS}
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return: A {@link TaskModel} of type {@link TaskType#DO_WHILE} in a List
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in DoWhileTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();

        TaskModel task = workflowModel.getTaskByRefName(workflowTask.getTaskReferenceName());
        if (task != null && task.getStatus().isTerminal()) {
            // Since loopTask is already completed no need to schedule task again.
            return List.of();
        }

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(
                                () ->
                                        Optional.ofNullable(
                                                        metadataDAO.getTaskDef(
                                                                workflowTask.getName()))
                                                .orElseGet(TaskDef::new));

        TaskModel doWhileTask = taskMapperContext.createTaskModel();
        doWhileTask.setTaskType(TaskType.TASK_TYPE_DO_WHILE);
        doWhileTask.setStatus(TaskModel.Status.IN_PROGRESS);
        doWhileTask.setStartTime(System.currentTimeMillis());
        doWhileTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        doWhileTask.setRateLimitFrequencyInSeconds(taskDefinition.getRateLimitFrequencyInSeconds());
        doWhileTask.setRetryCount(taskMapperContext.getRetryCount());

        return List.of(doWhileTask);
    }
}
