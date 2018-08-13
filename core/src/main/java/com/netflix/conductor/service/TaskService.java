/**
 * Copyright 2016 Netflix, Inc.
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


import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Preconditions;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;

import com.netflix.conductor.dao.QueueDAO;

import com.netflix.conductor.service.utils.ServiceUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fjhaveri
 */

@Singleton
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final ExecutionService executionService;

    private final QueueDAO queueDAO;

    @Inject
    public TaskService(ExecutionService executionService, QueueDAO queueDAO) {
        this.executionService = executionService;
        this.queueDAO = queueDAO;
    }

    public Task poll(String taskType, String workerId, String domain) throws Exception {
        ServiceUtils.isValid(taskType, "TaskType cannot be null or empty.");
        logger.debug("Task being polled: /tasks/poll/{}?{}&{}", taskType, workerId, domain);
        Task task = executionService.getLastPollTask(taskType, workerId, domain);
        if (task != null) {
            logger.debug("The Task {} being returned for /tasks/poll/{}?{}&{}", task, taskType, workerId, domain);
        }
        return task;
    }

    public List<Task> batchPoll(String taskType, String workerId, String domain, Integer count, Integer timeout) throws Exception {
        ServiceUtils.isValid(taskType, "TaskType cannot be null or empty.");
        List<Task> polledTasks = executionService.poll(taskType, workerId, domain, count, timeout);
        logger.debug("The Tasks {} being returned for /tasks/poll/{}?{}&{}",
                polledTasks.stream()
                        .map(Task::getTaskId)
                        .collect(Collectors.toList()), taskType, workerId, domain);
        return polledTasks;
    }

    /**
     * Get in progress tasks. The results are paginated.
     * @param taskType
     * @param startKey
     * @param count
     */
    public List<Task> getTasks(String taskType, String startKey, Integer count) throws Exception {
        ServiceUtils.isValid(taskType, "TaskType cannot be null or empty.");
        return executionService.getTasks(taskType, startKey, count);
    }

    /**
     * Get in progress task for a given workflow id.
     */
    public Task getPendingTaskForWorkflow(String workflowId, String taskReferenceName) {
        ServiceUtils.isValid(workflowId, "WorkflowId cannot be null or empty.");
        ServiceUtils.isValid(taskReferenceName, "TaskReferenceName cannot be null or empty.");
        return executionService.getPendingTaskForWorkflow(taskReferenceName, workflowId);
    }

    /**
     * Updates a task.
     */
    public String updateTask(TaskResult taskResult) throws Exception {
        ServiceUtils.isValid(taskResult, "TaskResult cannot be null or empty.");
        logger.debug("Update Task: {} with callback time: {}", taskResult, taskResult.getCallbackAfterSeconds());
        executionService.updateTask(taskResult);
        logger.debug("Task: {} updated successfully with callback time: {}", taskResult, taskResult.getCallbackAfterSeconds());
        return taskResult.getTaskId();
    }

    /**
     * Ack Task is received
     */
    public String ackTaskReceived(String taskId, String workerId) throws Exception {
        ServiceUtils.isValid(taskId, "TaskId cannot be null or empty.");
        logger.debug("Ack received for task: {} from worker: {}", taskId, workerId);
        return String.valueOf(executionService.ackTaskReceived(taskId));
    }

    /**
     * Log Task Execution Details
     */
    public void log(String taskId, String log) {
        ServiceUtils.isValid(taskId, "TaskId cannot be null or empty.");
        executionService.log(taskId, log);
    }

    /**
     * Get Task Execution Logs
     */
    public List<TaskExecLog> getTaskLogs(String taskId) {
        ServiceUtils.isValid(taskId, "TaskId cannot be null or empty.");
        return executionService.getTaskLogs(taskId);
    }

    /**
     * Get task by Id.
     */
    public Task getTask(String taskId) throws Exception {
        ServiceUtils.isValid(taskId, "TaskId cannot be null or empty.");
        return executionService.getTask(taskId);
    }

    /**
     * Remove Task from a Task type queue.
     */
    public void removeTaskFromQueue(String taskType, String taskId) {
        ServiceUtils.isValid(taskType, "TaskType cannot be null or empty.");
        ServiceUtils.isValid(taskId, "TaskId cannot be null or empty.");
        executionService.removeTaskfromQueue(taskType, taskId);
    }

    /**
     * Get Task type queue sizes.
     */
    public Map<String, Integer> getTaskQueueSizes(List<String> taskTypes) {
        return executionService.getTaskQueueSizes(taskTypes);
    }

    /**
     * Get the details about each queue.
     */
    public Map<String, Map<String, Map<String, Long>>> allVerbose() {
        return queueDAO.queuesDetailVerbose();
    }

    /**
     * Get the details about each queue.
     */
    public Map<String, Long> getAllQueueDetails() {
        return queueDAO.queuesDetail().entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
    }

    /**
     * Get the last poll data for a given task type.
     */
    public List<PollData> getPollData(String taskType) throws Exception {
        ServiceUtils.isValid(taskType, "TaskType cannot be null or empty.");
        return executionService.getPollData(taskType);
    }

    /**
     * Get the last poll data for all task types.
     */
    public List<PollData> getAllPollData() {
        return executionService.getAllPollData();
    }

    /**
     * Requeue pending tasks for all the running workflows.
     */
    public String requeue() throws Exception {
        return String.valueOf(executionService.requeuePendingTasks());
    }

    /**
     * Requeue pending tasks.
     */
    public String requeuePendingTask(String taskType) throws Exception {
        ServiceUtils.isValid(taskType,"TaskType cannot be null or empty.");
        return String.valueOf(executionService.requeuePendingTasks(taskType));
    }

    /**
     * Search for tasks based in payload and other parameters. Use sort options as sort=<field>:ASC|DESC e.g.
     * sort=name&sort=workflowId:DESC. If order is not specified, defaults to ASC."
     */
    public SearchResult<TaskSummary> search(int start, int size, String sort, String freeText, String query) {
        return executionService.getSearchTasks(query, freeText, start, size, sort);
    }
}

