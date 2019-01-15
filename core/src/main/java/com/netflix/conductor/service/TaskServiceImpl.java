/*
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
package com.netflix.conductor.service;

import com.netflix.conductor.annotations.Audit;
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Audit
@Singleton
@Trace
public class TaskServiceImpl implements TaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final ExecutionService executionService;

    private final QueueDAO queueDAO;

    @Inject
    public TaskServiceImpl(ExecutionService executionService, QueueDAO queueDAO) {
        this.executionService = executionService;
        this.queueDAO = queueDAO;
    }

    /**
     * Poll for a task of a certain type.
     *
     * @param taskType Task name
     * @param workerId Id of the workflow
     * @param domain   Domain of the workflow
     * @return polled {@link Task}
     */
    @Service
    public Task poll(String taskType, String workerId, String domain) {
        LOGGER.debug("Task being polled: /tasks/poll/{}?{}&{}", taskType, workerId, domain);
        Task task = executionService.getLastPollTask(taskType, workerId, domain);
        if (task != null) {
            LOGGER.debug("The Task {} being returned for /tasks/poll/{}?{}&{}", task, taskType, workerId, domain);
        }
        Monitors.recordTaskPollCount(taskType, domain, 1);
        return task;
    }

    /**
     * Batch Poll for a task of a certain type.
     *
     * @param taskType Task Name
     * @param workerId Id of the workflow
     * @param domain   Domain of the workflow
     * @param count    Number of tasks
     * @param timeout  Timeout for polling in milliseconds
     * @return list of {@link Task}
     */
    @Service
    public List<Task> batchPoll(String taskType, String workerId, String domain, Integer count, Integer timeout) {
        List<Task> polledTasks = executionService.poll(taskType, workerId, domain, count, timeout);
        LOGGER.debug("The Tasks {} being returned for /tasks/poll/{}?{}&{}",
                polledTasks.stream()
                        .map(Task::getTaskId)
                        .collect(Collectors.toList()), taskType, workerId, domain);
        Monitors.recordTaskPollCount(taskType, domain, polledTasks.size());
        return polledTasks;
    }

    /**
     * Get in progress tasks. The results are paginated.
     *
     * @param taskType Task Name
     * @param startKey Start index of pagination
     * @param count    Number of entries
     * @return list of {@link Task}
     */
    @Service
    public List<Task> getTasks(String taskType, String startKey, Integer count) {
        return executionService.getTasks(taskType, startKey, count);
    }

    /**
     * Get in progress task for a given workflow id.
     *
     * @param workflowId        Id of the workflow
     * @param taskReferenceName Task reference name.
     * @return instance of {@link Task}
     */
    @Service
    public Task getPendingTaskForWorkflow(String workflowId, String taskReferenceName) {
        return executionService.getPendingTaskForWorkflow(taskReferenceName, workflowId);
    }

    /**
     * Updates a task.
     *
     * @param taskResult Instance of {@link TaskResult}
     * @return task Id of the updated task.
     */
    @Service
    public String updateTask(TaskResult taskResult) {
        LOGGER.debug("Update Task: {} with callback time: {}", taskResult, taskResult.getCallbackAfterSeconds());
        executionService.updateTask(taskResult);
        LOGGER.debug("Task: {} updated successfully with callback time: {}", taskResult, taskResult.getCallbackAfterSeconds());
        return taskResult.getTaskId();
    }

    /**
     * Ack Task is received.
     *
     * @param taskId   Id of the task
     * @param workerId Id of the worker
     * @return `true|false` if task if received or not
     */
    @Service
    public String ackTaskReceived(String taskId, String workerId) {
        LOGGER.debug("Ack received for task: {} from worker: {}", taskId, workerId);
        return String.valueOf(ackTaskReceived(taskId));
    }

    /**
     * Ack Task is received.
     *
     * @param taskId   Id of the task
     * @return `true|false` if task if received or not
     */
    @Service
    public boolean ackTaskReceived(String taskId) {
        LOGGER.debug("Ack received for task: {}", taskId);
        boolean ackResult;
        try {
            ackResult = executionService.ackTaskReceived(taskId);
        } catch (Exception e) {
            // safe to ignore exception here, since the task will not be processed by the worker due to ack failure
            // The task will eventually be available to be polled again after the unack timeout
            LOGGER.error("Exception when trying to ack task {}", taskId, e);
            ackResult = false;
        }
        return ackResult;
    }

    /**
     * Log Task Execution Details.
     *
     * @param taskId Id of the task
     * @param log    Details you want to log
     */
    @Service
    public void log(String taskId, String log) {
        executionService.log(taskId, log);
    }

    /**
     * Get Task Execution Logs.
     *
     * @param taskId Id of the task.
     * @return list of {@link TaskExecLog}
     */
    @Service
    public List<TaskExecLog> getTaskLogs(String taskId) {
        return executionService.getTaskLogs(taskId);
    }

    /**
     * Get task by Id.
     *
     * @param taskId Id of the task.
     * @return instance of {@link Task}
     */
    @Service
    public Task getTask(String taskId) {
        return executionService.getTask(taskId);
    }

    /**
     * Remove Task from a Task type queue.
     *
     * @param taskType Task Name
     * @param taskId   ID of the task
     */
    @Service
    public void removeTaskFromQueue(String taskType, String taskId) {
        executionService.removeTaskfromQueue(taskId);
    }

    /**
     * Remove Task from a Task type queue.
     *
     * @param taskId   ID of the task
     */
    @Service
    public void removeTaskFromQueue(String taskId) {
        executionService.removeTaskfromQueue(taskId);
    }

    /**
     * Get Task type queue sizes.
     *
     * @param taskTypes List of task types.
     * @return map of task type as Key and queue size as value.
     */
    @Service
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
                .sorted(Comparator.comparing(Entry::getKey))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
    }

    /**
     * Get the last poll data for a given task type.
     *
     * @param taskType Task Name
     * @return list of {@link PollData}
     */
    @Service
    public List<PollData> getPollData(String taskType) {
        //TODO: check task type is valid or not
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
     * Requeue pending tasks for all the running workflows.
     *
     * @return number of tasks requeued.
     */
    public String requeue() {
        return String.valueOf(executionService.requeuePendingTasks());
    }

    /**
     * Requeue pending tasks.
     *
     * @param taskType Task name.
     * @return number of tasks requeued.
     */
    @Service
    public String requeuePendingTask(String taskType) {
        return String.valueOf(executionService.requeuePendingTasks(taskType));
    }

    /**
     * Search for tasks based in payload and other parameters. Use sort options as ASC or DESC e.g.
     * sort=name or sort=workflowId. If order is not specified, defaults to ASC.
     *
     * @param start    Start index of pagination
     * @param size     Number of entries
     * @param sort     Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query    Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<TaskSummary> search(int start, int size, String sort, String freeText, String query) {
        return executionService.getSearchTasks(query, freeText, start, size, sort);
    }

    /**
     * Get the external storage location where the task output payload is stored/to be stored
     *
     * @param path the path for which the external storage location is to be populated
     * @return {@link ExternalStorageLocation} containing the uri and the path to the payload is stored in external storage
     */
    public ExternalStorageLocation getExternalStorageLocation(String path) {
        return executionService.getExternalStorageLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, path);
    }
}
