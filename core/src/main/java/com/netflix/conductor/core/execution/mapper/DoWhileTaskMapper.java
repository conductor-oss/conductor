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

import java.util.ArrayList;
import java.util.Collections;
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
import com.netflix.conductor.common.utils.TaskUtils;
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

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();

        TaskModel task = workflowInstance.getTaskByRefName(taskToSchedule.getTaskReferenceName());
        if (task != null && task.getStatus().isTerminal()) {
            // Since loopTask is already completed no need to schedule task again.
            return Collections.emptyList();
        }

        String taskId = taskMapperContext.getTaskId();
        List<TaskModel> tasksToBeScheduled = new ArrayList<>();
        int retryCount = taskMapperContext.getRetryCount();
        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(
                                () ->
                                        Optional.ofNullable(
                                                        metadataDAO.getTaskDef(
                                                                taskToSchedule.getName()))
                                                .orElseGet(TaskDef::new));

        TaskModel loopTask = new TaskModel();
        loopTask.setTaskType(TaskType.TASK_TYPE_DO_WHILE);
        loopTask.setTaskDefName(taskToSchedule.getName());
        loopTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        loopTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        loopTask.setCorrelationId(workflowInstance.getCorrelationId());
        loopTask.setWorkflowType(workflowInstance.getWorkflowName());
        loopTask.setScheduledTime(System.currentTimeMillis());
        loopTask.setTaskId(taskId);
        loopTask.setIteration(1);
        loopTask.setStatus(TaskModel.Status.IN_PROGRESS);
        loopTask.setWorkflowTask(taskToSchedule);
        loopTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        loopTask.setRateLimitFrequencyInSeconds(taskDefinition.getRateLimitFrequencyInSeconds());

        tasksToBeScheduled.add(loopTask);
        List<WorkflowTask> loopOverTasks = taskToSchedule.getLoopOver();
        List<TaskModel> tasks2 =
                taskMapperContext
                        .getDeciderService()
                        .getTasksToBeScheduled(workflowInstance, loopOverTasks.get(0), retryCount);
        tasks2.forEach(
                t -> {
                    t.setReferenceTaskName(
                            TaskUtils.appendIteration(
                                    t.getReferenceTaskName(), loopTask.getIteration()));
                    t.setIteration(loopTask.getIteration());
                });
        tasksToBeScheduled.addAll(tasks2);

        return tasksToBeScheduled;
    }
}
