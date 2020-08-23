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
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#FORK_JOIN}
 * to a LinkedList of {@link Task} beginning with a completed {@link SystemTaskType#FORK}, followed by the user defined fork tasks
 */
public class ForkJoinTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(ForkJoinTaskMapper.class);

    /**
     * This method gets the list of tasks that need to scheduled when the task to scheduled is of type {@link TaskType#FORK_JOIN}.
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link WorkflowDef}, {@link Workflow} and a string representation of the TaskId
     * @return List of tasks in the following order:
     * * <ul>
     * <li>
     * {@link SystemTaskType#FORK} with {@link Task.Status#COMPLETED}
     * </li>
     * <li>
     * Might be any kind of task, but in most cases is a UserDefinedTask with {@link Task.Status#SCHEDULED}
     * </li>
     * </ul>
     * @throws TerminateWorkflowException When the task after {@link TaskType#FORK_JOIN} is not a {@link TaskType#JOIN}
     */
    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) throws TerminateWorkflowException {

        logger.debug("TaskMapperContext {} in ForkJoinTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        int retryCount = taskMapperContext.getRetryCount();

        String taskId = taskMapperContext.getTaskId();

        List<Task> tasksToBeScheduled = new LinkedList<>();
        Task forkTask = new Task();
        forkTask.setTaskType(SystemTaskType.FORK.name());
        forkTask.setTaskDefName(SystemTaskType.FORK.name());
        forkTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        forkTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        forkTask.setWorkflowType(workflowInstance.getWorkflowName());
        forkTask.setCorrelationId(workflowInstance.getCorrelationId());
        forkTask.setScheduledTime(System.currentTimeMillis());
        forkTask.setStartTime(System.currentTimeMillis());
        forkTask.setInputData(taskInput);
        forkTask.setTaskId(taskId);
        forkTask.setStatus(Task.Status.COMPLETED);
        forkTask.setWorkflowPriority(workflowInstance.getPriority());
        forkTask.setWorkflowTask(taskToSchedule);

        tasksToBeScheduled.add(forkTask);
        List<List<WorkflowTask>> forkTasks = taskToSchedule.getForkTasks();
        for (List<WorkflowTask> wfts : forkTasks) {
            WorkflowTask wft = wfts.get(0);
            List<Task> tasks2 = taskMapperContext.getDeciderService()
                    .getTasksToBeScheduled(workflowInstance, wft, retryCount);
            tasksToBeScheduled.addAll(tasks2);
        }

        WorkflowTask joinWorkflowTask = workflowInstance
                .getWorkflowDefinition()
                .getNextTask(taskToSchedule.getTaskReferenceName());

        if (joinWorkflowTask == null || !joinWorkflowTask.getType().equals(TaskType.JOIN.name())) {
            throw new TerminateWorkflowException("Fork task definition is not followed by a join task.  Check the blueprint");
        }
        return tasksToBeScheduled;
    }
}
