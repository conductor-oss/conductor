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
package com.netflix.conductor.dao;

import java.util.List;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/** Data access layer for storing workflow executions */
public interface ExecutionDAO {

    /**
     * @param taskName Name of the task
     * @param workflowId Workflow instance id
     * @return List of pending tasks (in_progress)
     */
    List<TaskModel> getPendingTasksByWorkflow(String taskName, String workflowId);

    /**
     * @param taskType Type of task
     * @param startKey start
     * @param count number of tasks to return
     * @return List of tasks starting from startKey
     */
    List<TaskModel> getTasks(String taskType, String startKey, int count);

    /**
     * @param tasks tasks to be created
     * @return List of tasks that were created.
     *     <p><b>Note on the primary key constraint</b>
     *     <p>For a given task reference name and retryCount should be considered unique/primary
     *     key. Given two tasks with the same reference name and retryCount only one should be added
     *     to the database.
     */
    List<TaskModel> createTasks(List<TaskModel> tasks);

    /** @param task Task to be updated */
    void updateTask(TaskModel task);

    /**
     * Checks if the number of tasks in progress for the given taskDef will exceed the limit if the
     * task is scheduled to be in progress (given to the worker or for system tasks start() method
     * called)
     *
     * @param task The task to be executed. Limit is set in the Task's definition
     * @return true if by executing this task, the limit is breached. false otherwise.
     * @see TaskDef#concurrencyLimit()
     * @deprecated Since v3.3.5. Use {@link ConcurrentExecutionLimitDAO#exceedsLimit(TaskModel)}.
     */
    @Deprecated
    default boolean exceedsInProgressLimit(TaskModel task) {
        throw new UnsupportedOperationException(
                getClass() + "does not support exceedsInProgressLimit");
    }

    /**
     * @param taskId id of the task to be removed.
     * @return true if the deletion is successful, false otherwise.
     */
    boolean removeTask(String taskId);

    /**
     * @param taskId Task instance id
     * @return Task
     */
    TaskModel getTask(String taskId);

    /**
     * @param taskIds Task instance ids
     * @return List of tasks
     */
    List<TaskModel> getTasks(List<String> taskIds);

    /**
     * @param taskType Type of the task for which to retrieve the list of pending tasks
     * @return List of pending tasks
     */
    List<TaskModel> getPendingTasksForTaskType(String taskType);

    /**
     * @param workflowId Workflow instance id
     * @return List of tasks for the given workflow instance id
     */
    List<TaskModel> getTasksForWorkflow(String workflowId);

    /**
     * @param workflow Workflow to be created
     * @return Id of the newly created workflow
     */
    String createWorkflow(WorkflowModel workflow);

    /**
     * @param workflow Workflow to be updated
     * @return Id of the updated workflow
     */
    String updateWorkflow(WorkflowModel workflow);

    /**
     * @param workflowId workflow instance id
     * @return true if the deletion is successful, false otherwise
     */
    boolean removeWorkflow(String workflowId);

    /**
     * Removes the workflow with ttl seconds
     *
     * @param workflowId workflowId workflow instance id
     * @param ttlSeconds time to live in seconds.
     * @return
     */
    boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds);

    /**
     * @param workflowType Workflow Type
     * @param workflowId workflow instance id
     */
    void removeFromPendingWorkflow(String workflowType, String workflowId);

    /**
     * @param workflowId workflow instance id
     * @return Workflow
     */
    WorkflowModel getWorkflow(String workflowId);

    /**
     * @param workflowId workflow instance id
     * @param includeTasks if set, includes the tasks (pending and completed) sorted by Task
     *     Sequence number in Workflow.
     * @return Workflow instance details
     */
    WorkflowModel getWorkflow(String workflowId, boolean includeTasks);

    /**
     * @param workflowName name of the workflow
     * @param version the workflow version
     * @return List of workflow ids which are running
     */
    List<String> getRunningWorkflowIds(String workflowName, int version);

    /**
     * @param workflowName Name of the workflow
     * @param version the workflow version
     * @return List of workflows that are running
     */
    List<WorkflowModel> getPendingWorkflowsByType(String workflowName, int version);

    /**
     * @param workflowName Name of the workflow
     * @return No. of running workflows
     */
    long getPendingWorkflowCount(String workflowName);

    /**
     * @param taskDefName Name of the task
     * @return Number of task currently in IN_PROGRESS status
     */
    long getInProgressTaskCount(String taskDefName);

    /**
     * @param workflowName Name of the workflow
     * @param startTime epoch time
     * @param endTime epoch time
     * @return List of workflows between start and end time
     */
    List<WorkflowModel> getWorkflowsByType(String workflowName, Long startTime, Long endTime);

    /**
     * @param workflowName workflow name
     * @param correlationId Correlation Id
     * @param includeTasks Option to includeTasks in results
     * @return List of workflows by correlation id
     */
    List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowName, String correlationId, boolean includeTasks);

    /**
     * @return true, if the DAO implementation is capable of searching across workflows false, if
     *     the DAO implementation cannot perform searches across workflows (and needs to use
     *     indexDAO)
     */
    boolean canSearchAcrossWorkflows();

    // Events

    /**
     * @param eventExecution Event Execution to be stored
     * @return true if the event was added. false otherwise when the event by id is already already
     *     stored.
     */
    boolean addEventExecution(EventExecution eventExecution);

    /** @param eventExecution Event execution to be updated */
    void updateEventExecution(EventExecution eventExecution);

    /** @param eventExecution Event execution to be removed */
    void removeEventExecution(EventExecution eventExecution);
}
