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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#FORK_JOIN} to a LinkedList of {@link TaskModel} beginning with a completed {@link
 * TaskType#TASK_TYPE_FORK}, followed by the user defined fork tasks
 */
@Component
public class ForkJoinTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(ForkJoinTaskMapper.class);

    @Override
    public String getTaskType() {
        return TaskType.FORK_JOIN.name();
    }

    /**
     * This method gets the list of tasks that need to scheduled when the task to scheduled is of
     * type {@link TaskType#FORK_JOIN}.
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return List of tasks in the following order: *
     *     <ul>
     *       <li>{@link TaskType#TASK_TYPE_FORK} with {@link TaskModel.Status#COMPLETED}
     *       <li>Might be any kind of task, but in most cases is a UserDefinedTask with {@link
     *           TaskModel.Status#SCHEDULED}
     *     </ul>
     *
     * @throws TerminateWorkflowException When the task after {@link TaskType#FORK_JOIN} is not a
     *     {@link TaskType#JOIN}
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in ForkJoinTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        int retryCount = taskMapperContext.getRetryCount();

        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        TaskModel forkTask = taskMapperContext.createTaskModel();
        forkTask.setTaskType(TaskType.TASK_TYPE_FORK);
        forkTask.setTaskDefName(TaskType.TASK_TYPE_FORK);
        long epochMillis = System.currentTimeMillis();
        forkTask.setStartTime(epochMillis);
        forkTask.setEndTime(epochMillis);
        forkTask.setInputData(taskInput);
        forkTask.setStatus(TaskModel.Status.COMPLETED);
        if (Objects.nonNull(taskMapperContext.getTaskDefinition())) {
            forkTask.setIsolationGroupId(
                    taskMapperContext.getTaskDefinition().getIsolationGroupId());
        }

        tasksToBeScheduled.add(forkTask);
        List<List<WorkflowTask>> forkTasks = workflowTask.getForkTasks();
        for (List<WorkflowTask> wfts : forkTasks) {
            WorkflowTask wft = wfts.get(0);
            List<TaskModel> tasks2 =
                    taskMapperContext
                            .getDeciderService()
                            .getTasksToBeScheduled(workflowModel, wft, retryCount);
            tasksToBeScheduled.addAll(tasks2);
        }

        WorkflowTask joinWorkflowTask =
                workflowModel
                        .getWorkflowDefinition()
                        .getNextTask(workflowTask.getTaskReferenceName());

        if (joinWorkflowTask == null || !joinWorkflowTask.getType().equals(TaskType.JOIN.name())) {
            throw new TerminateWorkflowException(
                    "Fork task definition is not followed by a join task.  Check the blueprint");
        }
        List<TaskModel> joinTask =
                taskMapperContext
                        .getDeciderService()
                        .getTasksToBeScheduled(workflowModel, joinWorkflowTask, retryCount);

        tasksToBeScheduled.addAll(joinTask);
        return tasksToBeScheduled;
    }
}
