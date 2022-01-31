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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Audit;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

@Audit
@Trace
@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskServiceImpl.class);
    private final ExecutionService executionService;
    private final QueueDAO queueDAO;

    public TaskServiceImpl(ExecutionService executionService, QueueDAO queueDAO) {
        this.executionService = executionService;
        this.queueDAO = queueDAO;
    }

    /**
     * Poll for a task of a certain type.
     *
     * @param taskType Task name
     * @param workerId id of the workflow
     * @param domain Domain of the workflow
     * @return polled {@link Task}
     */
    public Task poll(String taskType, String workerId, String domain) {
        LOGGER.debug("Task being polled: /tasks/poll/{}?{}&{}", taskType, workerId, domain);
        Task task = executionService.getLastPollTask(taskType, workerId, domain);
        if (task != null) {
            LOGGER.debug(
                    "The Task {} being returned for /tasks/poll/{}?{}&{}",
                    task,
                    taskType,
                    workerId,
                    domain);
        }
        Monitors.recordTaskPollCount(taskType, domain, 1);
        return task;
    }

    /**
     * Batch Poll for a task of a certain type.
     *
     * @param taskType Task Name
     * @param workerId id of the workflow
     * @param domain Domain of the workflow
     * @param count Number of tasks
     * @param timeout Timeout for polling in milliseconds
     * @return list of {@link Task}
     */
    public List<Task> batchPoll(
            String taskType, String workerId, String domain, Integer count, Integer timeout) {
        List<Task> polledTasks = executionService.poll(taskType, workerId, domain, count, timeout);
        LOGGER.debug(
                "The Tasks {} being returned for /tasks/poll/{}?{}&{}",
                polledTasks.stream().map(Task::getTaskId).collect(Collectors.toList()),
                taskType,
                workerId,
                domain);
        Monitors.recordTaskPollCount(taskType, domain, polledTasks.size());
        return polledTasks;
    }

    /**
     * Get in progress tasks. The results are paginated.
     *
     * @param taskType Task Name
     * @param startKey Start index of pagination
     * @param count Number of entries
     * @return list of {@link Task}
     */
    public List<Task> getTasks(String taskType, String startKey, Integer count) {
        return executionService.getTasks(taskType, startKey, count);
    }

    /**
     * Get in progress task for a given workflow id.
     *
     * @param workflowId id of the workflow
     * @param taskReferenceName Task reference name.
     * @return instance of {@link Task}
     */
    public Task getPendingTaskForWorkflow(String workflowId, String taskReferenceName) {
        return executionService.getPendingTaskForWorkflow(taskReferenceName, workflowId);
    }

    /**
     * Updates a task.
     *
     * @param taskResult Instance of {@link TaskResult}
     * @return task Id of the updated task.
     */
    public String updateTask(TaskResult taskResult) {
        LOGGER.debug(
                "Update Task: {} with callback time: {}",
                taskResult,
                taskResult.getCallbackAfterSeconds());
        executionService.updateTask(taskResult);
        LOGGER.debug(
                "Task: {} updated successfully with callback time: {}",
                taskResult,
                taskResult.getCallbackAfterSeconds());
        return taskResult.getTaskId();
    }

    /**
     * Ack Task is received.
     *
     * @param taskId id of the task
     * @param workerId id of the worker
     * @return `true|false` if task is received or not
     */
    public String ackTaskReceived(String taskId, String workerId) {
        LOGGER.debug("Ack received for task: {} from worker: {}", taskId, workerId);
        return String.valueOf(ackTaskReceived(taskId));
    }

    /**
     * Ack Task is received.
     *
     * @param taskId id of the task
     * @return `true|false` if task is received or not
     */
    public boolean ackTaskReceived(String taskId) {
        LOGGER.debug("Ack received for task: {}", taskId);
        String ackTaskDesc = "Ack Task with taskId: " + taskId;
        String ackTaskOperation = "ackTaskReceived";
        AtomicBoolean ackResult = new AtomicBoolean(false);
        try {
            new RetryUtil<>()
                    .retryOnException(
                            () -> {
                                ackResult.set(executionService.ackTaskReceived(taskId));
                                return null;
                            },
                            null,
                            null,
                            3,
                            ackTaskDesc,
                            ackTaskOperation);

        } catch (Exception e) {
            // Fail the task and let decide reevaluate the workflow, thereby preventing workflow
            // being stuck from transient ack errors.
            String errorMsg = String.format("Error when trying to ack task %s", taskId);
            LOGGER.error(errorMsg, e);
            Task task = executionService.getTask(taskId);
            Monitors.recordAckTaskError(task.getTaskType());
            failTask(task, errorMsg);
            ackResult.set(false);
        }
        return ackResult.get();
    }

    /** Updates the task with FAILED status; On exception, fails the workflow. */
    private void failTask(Task task, String errorMsg) {
        try {
            TaskResult taskResult = new TaskResult();
            taskResult.setStatus(TaskResult.Status.FAILED);
            taskResult.setTaskId(task.getTaskId());
            taskResult.setWorkflowInstanceId(task.getWorkflowInstanceId());
            taskResult.setReasonForIncompletion(errorMsg);
            executionService.updateTask(taskResult);
        } catch (Exception e) {
            LOGGER.error(
                    "Unable to fail task: {} in workflow: {}",
                    task.getTaskId(),
                    task.getWorkflowInstanceId(),
                    e);
            executionService.terminateWorkflow(
                    task.getWorkflowInstanceId(), "Failed to ack task: " + task.getTaskId());
        }
    }

    /**
     * Log Task Execution Details.
     *
     * @param taskId id of the task
     * @param log Details you want to log
     */
    public void log(String taskId, String log) {
        executionService.log(taskId, log);
    }

    /**
     * Get Task Execution Logs.
     *
     * @param taskId id of the task.
     * @return list of {@link TaskExecLog}
     */
    public List<TaskExecLog> getTaskLogs(String taskId) {
        return executionService.getTaskLogs(taskId);
    }

    /**
     * Get task by Id.
     *
     * @param taskId id of the task.
     * @return instance of {@link Task}
     */
    public Task getTask(String taskId) {
        return executionService.getTask(taskId);
    }

    /**
     * Remove Task from a Task type queue.
     *
     * @param taskType Task Name
     * @param taskId ID of the task
     */
    public void removeTaskFromQueue(String taskType, String taskId) {
        executionService.removeTaskFromQueue(taskId);
    }

    /**
     * Remove Task from a Task type queue.
     *
     * @param taskId ID of the task
     */
    public void removeTaskFromQueue(String taskId) {
        executionService.removeTaskFromQueue(taskId);
    }

    /**
     * Get Task type queue sizes.
     *
     * @param taskTypes List of task types.
     * @return map of task type as Key and queue size as value.
     */
    public Map<String, Integer> getTaskQueueSizes(List<String> taskTypes) {
        return executionService.getTaskQueueSizes(taskTypes);
    }

    /**
     * Get the details about each queue.
     *
     * @return map of queue details.
     */
    public Map<String, Map<String, Map<String, Long>>> allVerbose() {
        return queueDAO.queuesDetailVerbose();
    }

    /**
     * Get the details about each queue.
     *
     * @return map of details about each queue.
     */
    public Map<String, Long> getAllQueueDetails() {
        return queueDAO.queuesDetail().entrySet().stream()
                .sorted(Entry.comparingByKey())
                .collect(
                        Collectors.toMap(
                                Entry::getKey,
                                Entry::getValue,
                                (v1, v2) -> v1,
                                LinkedHashMap::new));
    }

    /**
     * Get the last poll data for a given task type.
     *
     * @param taskType Task Name
     * @return list of {@link PollData}
     */
    public List<PollData> getPollData(String taskType) {
        return executionService.getPollData(taskType);
    }

    /**
     * Get the last poll data for all task types.
     *
     * @return list of {@link PollData}
     */
    public List<PollData> getAllPollData() {
        return executionService.getAllPollData();
    }

    /**
     * Requeue pending tasks.
     *
     * @param taskType Task name.
     * @return number of tasks requeued.
     */
    public String requeuePendingTask(String taskType) {
        return String.valueOf(executionService.requeuePendingTasks(taskType));
    }

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
    public SearchResult<TaskSummary> search(
            int start, int size, String sort, String freeText, String query) {
        return executionService.getSearchTasks(query, freeText, start, size, sort);
    }

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
    public SearchResult<Task> searchV2(
            int start, int size, String sort, String freeText, String query) {
        return executionService.getSearchTasksV2(query, freeText, start, size, sort);
    }

    /**
     * Get the external storage location where the task output payload is stored/to be stored
     *
     * @param path the path for which the external storage location is to be populated
     * @param operation the operation to be performed (read or write)
     * @param type the type of payload (input or output)
     * @return {@link ExternalStorageLocation} containing the uri and the path to the payload is
     *     stored in external storage
     */
    public ExternalStorageLocation getExternalStorageLocation(
            String path, String operation, String type) {
        try {
            ExternalPayloadStorage.Operation payloadOperation =
                    ExternalPayloadStorage.Operation.valueOf(StringUtils.upperCase(operation));
            ExternalPayloadStorage.PayloadType payloadType =
                    ExternalPayloadStorage.PayloadType.valueOf(StringUtils.upperCase(type));
            return executionService.getExternalStorageLocation(payloadOperation, payloadType, path);
        } catch (Exception e) {
            // FIXME: for backwards compatibility
            LOGGER.error(
                    "Invalid input - Operation: {}, PayloadType: {}, defaulting to WRITE/TASK_OUTPUT",
                    operation,
                    type);
            return executionService.getExternalStorageLocation(
                    ExternalPayloadStorage.Operation.WRITE,
                    ExternalPayloadStorage.PayloadType.TASK_OUTPUT,
                    path);
        }
    }
}
