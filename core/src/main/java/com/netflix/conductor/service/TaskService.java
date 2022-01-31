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
package com.netflix.conductor.service;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;

@Validated
public interface TaskService {

    /**
     * Poll for a task of a certain type.
     *
     * @param taskType Task name
     * @param workerId Id of the workflow
     * @param domain Domain of the workflow
     * @return polled {@link Task}
     */
    Task poll(
            @NotEmpty(message = "TaskType cannot be null or empty.") String taskType,
            String workerId,
            String domain);

    /**
     * Batch Poll for a task of a certain type.
     *
     * @param taskType Task Name
     * @param workerId Id of the workflow
     * @param domain Domain of the workflow
     * @param count Number of tasks
     * @param timeout Timeout for polling in milliseconds
     * @return list of {@link Task}
     */
    List<Task> batchPoll(
            @NotEmpty(message = "TaskType cannot be null or empty.") String taskType,
            String workerId,
            String domain,
            Integer count,
            Integer timeout);

    /**
     * Get in progress tasks. The results are paginated.
     *
     * @param taskType Task Name
     * @param startKey Start index of pagination
     * @param count Number of entries
     * @return list of {@link Task}
     */
    List<Task> getTasks(
            @NotEmpty(message = "TaskType cannot be null or empty.") String taskType,
            String startKey,
            Integer count);

    /**
     * Get in progress task for a given workflow id.
     *
     * @param workflowId Id of the workflow
     * @param taskReferenceName Task reference name.
     * @return instance of {@link Task}
     */
    Task getPendingTaskForWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            @NotEmpty(message = "TaskReferenceName cannot be null or empty.")
                    String taskReferenceName);

    /**
     * Updates a task.
     *
     * @param taskResult Instance of {@link TaskResult}
     * @return task Id of the updated task.
     */
    String updateTask(
            @NotNull(message = "TaskResult cannot be null or empty.") @Valid TaskResult taskResult);

    /**
     * Ack Task is received.
     *
     * @param taskId Id of the task
     * @param workerId Id of the worker
     * @return `true|false` if task if received or not
     */
    String ackTaskReceived(
            @NotEmpty(message = "TaskId cannot be null or empty.") String taskId, String workerId);

    /**
     * Ack Task is received.
     *
     * @param taskId Id of the task
     * @return `true|false` if task if received or not
     */
    boolean ackTaskReceived(@NotEmpty(message = "TaskId cannot be null or empty.") String taskId);

    /**
     * Log Task Execution Details.
     *
     * @param taskId Id of the task
     * @param log Details you want to log
     */
    void log(@NotEmpty(message = "TaskId cannot be null or empty.") String taskId, String log);

    /**
     * Get Task Execution Logs.
     *
     * @param taskId Id of the task.
     * @return list of {@link TaskExecLog}
     */
    List<TaskExecLog> getTaskLogs(
            @NotEmpty(message = "TaskId cannot be null or empty.") String taskId);

    /**
     * Get task by Id.
     *
     * @param taskId Id of the task.
     * @return instance of {@link Task}
     */
    Task getTask(@NotEmpty(message = "TaskId cannot be null or empty.") String taskId);

    /**
     * Remove Task from a Task type queue.
     *
     * @param taskType Task Name
     * @param taskId ID of the task
     */
    void removeTaskFromQueue(
            @NotEmpty(message = "TaskType cannot be null or empty.") String taskType,
            @NotEmpty(message = "TaskId cannot be null or empty.") String taskId);

    /**
     * Remove Task from a Task type queue.
     *
     * @param taskId ID of the task
     */
    void removeTaskFromQueue(@NotEmpty(message = "TaskId cannot be null or empty.") String taskId);

    /**
     * Get Task type queue sizes.
     *
     * @param taskTypes List of task types.
     * @return map of task type as Key and queue size as value.
     */
    Map<String, Integer> getTaskQueueSizes(List<String> taskTypes);

    /**
     * Get the details about each queue.
     *
     * @return map of queue details.
     */
    Map<String, Map<String, Map<String, Long>>> allVerbose();

    /**
     * Get the details about each queue.
     *
     * @return map of details about each queue.
     */
    Map<String, Long> getAllQueueDetails();

    /**
     * Get the last poll data for a given task type.
     *
     * @param taskType Task Name
     * @return list of {@link PollData}
     */
    List<PollData> getPollData(
            @NotEmpty(message = "TaskType cannot be null or empty.") String taskType);

    /**
     * Get the last poll data for all task types.
     *
     * @return list of {@link PollData}
     */
    List<PollData> getAllPollData();

    /**
     * Requeue pending tasks.
     *
     * @param taskType Task name.
     * @return number of tasks requeued.
     */
    String requeuePendingTask(
            @NotEmpty(message = "TaskType cannot be null or empty.") String taskType);

    /**
     * Search for tasks based in payload and other parameters. Use sort options as ASC or DESC e.g.
     * sort=name or sort=workflowId. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    SearchResult<TaskSummary> search(
            int start, int size, String sort, String freeText, String query);

    /**
     * Search for tasks based in payload and other parameters. Use sort options as ASC or DESC e.g.
     * sort=name or sort=workflowId. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    SearchResult<Task> searchV2(int start, int size, String sort, String freeText, String query);

    /**
     * Get the external storage location where the task output payload is stored/to be stored
     *
     * @param path the path for which the external storage location is to be populated
     * @param operation the operation to be performed (read or write)
     * @param payloadType the type of payload (input or output)
     * @return {@link ExternalStorageLocation} containing the uri and the path to the payload is
     *     stored in external storage
     */
    ExternalStorageLocation getExternalStorageLocation(
            String path, String operation, String payloadType);
}
