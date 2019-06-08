/**
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.SystemTaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#DO_WHILE}
 * to a {@link Task} of type {@link SystemTaskType#DO_WHILE}
 */
public class DoWhileTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(DoWhileTaskMapper.class);

    /**
     * This method maps {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#DO_WHILE} to a {@link Task} of type {@link SystemTaskType#DO_WHILE}
     * with a status of {@link Task.Status#IN_PROGRESS}
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link WorkflowDef}, {@link Workflow} and a string representation of the TaskId
     * @return: A {@link Task} of type {@link SystemTaskType#DO_WHILE} in a List
     */
    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        logger.debug("TaskMapperContext {} in DoWhileTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();

        Task task = workflowInstance.getTaskByRefName(taskToSchedule.getTaskReferenceName());
        if (task != null && task.getStatus().isTerminal()) {
            //Since loopTask is already completed no need to schedule task again.
            return Collections.emptyList();
        }

        String taskId = taskMapperContext.getTaskId();
        List<Task> tasksToBeScheduled = new ArrayList<>();
        int retryCount = taskMapperContext.getRetryCount();

        Task loopTask = new Task();
        loopTask.setTaskType(SystemTaskType.DO_WHILE.name());
        loopTask.setTaskDefName(taskToSchedule.getName());
        loopTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        loopTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        loopTask.setCorrelationId(workflowInstance.getCorrelationId());
        loopTask.setWorkflowType(workflowInstance.getWorkflowName());
        loopTask.setScheduledTime(System.currentTimeMillis());
        loopTask.setTaskId(taskId);
        loopTask.setStatus(Task.Status.IN_PROGRESS);
        loopTask.setWorkflowTask(taskToSchedule);

        List<WorkflowTask>loopOverTasks = taskToSchedule.getLoopOver();
        for (WorkflowTask wft : loopOverTasks) {
            List<Task> tasks2 = taskMapperContext.getDeciderService()
                    .getTasksToBeScheduled(workflowInstance, wft, retryCount);
            tasksToBeScheduled.addAll(tasks2);
        }
        tasksToBeScheduled.add(loopTask);

        return tasksToBeScheduled;
    }
}
