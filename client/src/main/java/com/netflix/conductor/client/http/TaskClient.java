/*
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
package com.netflix.conductor.client.http;

import com.google.common.base.Preconditions;
import com.google.common.io.CountingOutputStream;
import com.netflix.conductor.client.task.WorkflowTaskMetrics;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * @author visingh
 * @author Viren
 * Client for conductor task management including polling for task, updating task status etc.
 */
@SuppressWarnings("unchecked")
public class TaskClient extends ClientBase {

    private static GenericType<List<Task>> taskList = new GenericType<List<Task>>() {
    };

    private static GenericType<List<TaskDef>> taskDefList = new GenericType<List<TaskDef>>() {
    };

    private static GenericType<List<TaskExecLog>> taskExecLogList = new GenericType<List<TaskExecLog>>() {
    };

    private static GenericType<List<PollData>> pollDataList = new GenericType<List<PollData>>() {
    };

    private static GenericType<SearchResult<TaskSummary>> searchResultTaskSummary = new GenericType<SearchResult<TaskSummary>>() {
    };

    private static final Logger logger = LoggerFactory.getLogger(TaskClient.class);

    /**
     * Creates a default task client
     */
    public TaskClient() {
        super();
    }

    /**
     * @param config REST Client configuration
     */
    public TaskClient(ClientConfig config) {
        super(config);
    }

    /**
     * @param config  REST Client configuration
     * @param handler Jersey client handler. Useful when plugging in various http client interaction modules (e.g. ribbon)
     */
    public TaskClient(ClientConfig config, ClientHandler handler) {
        super(config, handler);
    }

    /**
     * @param config  config REST Client configuration
     * @param handler handler Jersey client handler. Useful when plugging in various http client interaction modules (e.g. ribbon)
     * @param filters Chain of client side filters to be applied per request
     */
    public TaskClient(ClientConfig config, ClientHandler handler, ClientFilter... filters) {
        super(config, handler);
        for (ClientFilter filter : filters) {
            super.client.addFilter(filter);
        }
    }

    /**
     * Perform a poll for a task of a specific task type.
     *
     * @param taskType The taskType to poll for
     * @param domain   The domain of the task type
     * @param workerId Name of the client worker. Used for logging.
     * @return Task waiting to be executed.
     */
    public Task pollTask(String taskType, String workerId, String domain) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(domain), "Domain cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(workerId), "Worker id cannot be blank");

        Object[] params = new Object[]{"workerid", workerId, "domain", domain};
        return getForEntity("tasks/poll/{taskType}", params, Task.class, taskType);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link #batchPollTasksByTaskType(String, String, int, int)} instead
     */
    @Deprecated
    public List<Task> poll(String taskType, String workerId, int count, int timeoutInMillisecond) {
        return batchPollTasksByTaskType(taskType, workerId, count, timeoutInMillisecond);
    }

    /**
     * Perform a batch poll for tasks by task type. Batch size is configurable by count.
     *
     * @param taskType             Type of task to poll for
     * @param workerId             Name of the client worker. Used for logging.
     * @param count                Maximum number of tasks to be returned. Actual number of tasks returned can be less than this number.
     * @param timeoutInMillisecond Long poll wait timeout.
     * @return List of tasks awaiting to be executed.
     */
    public List<Task> batchPollTasksByTaskType(String taskType, String workerId, int count, int timeoutInMillisecond) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(workerId), "Worker id cannot be blank");
        Preconditions.checkArgument(count > 0, "Count must be greater than 0");

        Object[] params = new Object[]{"workerid", workerId, "count", count, "timeout", timeoutInMillisecond};
        return getForEntity("tasks/poll/batch/{taskType}", params, taskList, taskType);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link #batchPollTasksInDomain(String, String, String, int, int)} instead
     */
    @Deprecated
    public List<Task> poll(String taskType, String domain, String workerId, int count, int timeoutInMillisecond) {
        return batchPollTasksInDomain(taskType, domain, workerId, count, timeoutInMillisecond);
    }

    /**
     * Batch poll for tasks in a domain. Batch size is configurable by count.
     *
     * @param taskType             Type of task to poll for
     * @param domain               The domain of the task type
     * @param workerId             Name of the client worker. Used for logging.
     * @param count                Maximum number of tasks to be returned. Actual number of tasks returned can be less than this number.
     * @param timeoutInMillisecond Long poll wait timeout.
     * @return List of tasks awaiting to be executed.
     */
    public List<Task> batchPollTasksInDomain(String taskType, String domain, String workerId, int count, int timeoutInMillisecond) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(workerId), "Worker id cannot be blank");
        Preconditions.checkArgument(count > 0, "Count must be greater than 0");

        Object[] params = new Object[]{"workerid", workerId, "count", count, "timeout", timeoutInMillisecond, "domain", domain};
        return getForEntity("tasks/poll/batch/{taskType}", params, taskList, taskType);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link #getPendingTasksByType(String, String, Integer)} instead
     */
    @Deprecated
    public List<Task> getTasks(String taskType, String startKey, Integer count) {
        return getPendingTasksByType(taskType, startKey, count);
    }

    /**
     * Retrieve pending tasks by type
     *
     * @param taskType Type of task
     * @param startKey id of the task from where to return the results. NULL to start from the beginning.
     * @param count    number of tasks to retrieve
     * @return Returns the list of PENDING tasks by type, starting with a given task Id.
     */
    public List<Task> getPendingTasksByType(String taskType, String startKey, Integer count) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");

        Object[] params = new Object[]{"startKey", startKey, "count", count};
        return getForEntity("tasks/in_progress/{taskType}", params, taskList, taskType);
    }

    /**
     * Retrieve pending task identified by reference name for a workflow
     *
     * @param workflowId        Workflow instance id
     * @param taskReferenceName reference name of the task
     * @return Returns the pending workflow task identified by the reference name
     */
    public Task getPendingTaskForWorkflow(String workflowId, String taskReferenceName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "Workflow id cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(taskReferenceName), "Task reference name cannot be blank");

        return getForEntity("tasks/in_progress/{workflowId}/{taskRefName}", null, Task.class, workflowId, taskReferenceName);
    }

    /**
     * Updates the result of a task execution.
     *
     * @param taskResult TaskResults to be updated.
     * @param taskType
     */
    public void updateTask(TaskResult taskResult, String taskType) {
        Preconditions.checkNotNull(taskResult, "Task result cannot be null");

        long taskResultSize = 0;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             CountingOutputStream countingOutputStream = new CountingOutputStream(byteArrayOutputStream)) {
            objectMapper.writeValue(countingOutputStream, taskResult);
            taskResultSize = countingOutputStream.getCount();
            WorkflowTaskMetrics.recordTaskResultPayloadSize(taskType, taskResultSize);
            if (taskResultSize > (3 * 1024 * 1024)) { //There is hard coded since there is no easy way to pass a config in here
                taskResult.setReasonForIncompletion(String.format("The TaskResult payload: %d is greater than the permissible 3MB", taskResultSize));
                taskResult.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
                taskResult.setOutputData(null);
            }
        } catch (Exception e) {
            logger.error("Unable to parse the TaskResult: {}", taskResult);
            throw new RuntimeException(e);
        }
        postForEntity("tasks", taskResult);
    }

    /**
     * Ack for the task poll.
     *
     * @param taskId   Id of the task to be polled
     * @param workerId user identified worker.
     * @return true if the task was found with the given ID and acknowledged. False otherwise. If the server returns false, the client should NOT attempt to ack again.
     */
    public Boolean ack(String taskId, String workerId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");

        String response = postForEntity("tasks/{taskId}/ack", null, new Object[]{"workerid", workerId}, String.class, taskId);
        return Boolean.valueOf(response);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link #logMessageForTask(String, String)} instead
     */
    @Deprecated
    public void log(String taskId, String logMessage) {
        logMessageForTask(taskId, logMessage);
    }

    /**
     * Log execution messages for a task.
     *
     * @param taskId     id of the task
     * @param logMessage the message to be logged
     */
    public void logMessageForTask(String taskId, String logMessage) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");
        postForEntity("tasks/" + taskId + "/log", logMessage);
    }

    /**
     * Fetch execution logs for a task.
     *
     * @param taskId id of the task.
     */
    public List<TaskExecLog> getTaskLogs(String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");
        return getForEntity("tasks/{taskId}/log", null, taskExecLogList, taskId);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link #getTaskDetails(String)} instead
     */
    @Deprecated
    public Task get(String taskId) {
        return getTaskDetails(taskId);
    }

    /**
     * Retrieve information about the task
     *
     * @param taskId ID of the task
     * @return Task details
     */
    public Task getTaskDetails(String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");
        return getForEntity("tasks/{taskId}", null, Task.class, taskId);
    }

    /**
     * Removes a task from a taskType queue
     *
     * @param taskType the taskType to identify the queue
     * @param taskId   the id of the task to be removed
     */
    public void removeTaskFromQueue(String taskType, String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");

        delete("tasks/queue/{taskType}/{taskId}", taskType, taskId);
    }

    public int getQueueSizeForTask(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");

        Map<String, Integer> queueSizeMap = getForEntity("tasks/queue/sizes", new Object[]{"taskType", taskType}, Map.class);
        if (queueSizeMap.keySet().contains(taskType)) {
            return queueSizeMap.get(taskType);
        }
        return 0;
    }

    /**
     * Get last poll data for a given task type
     *
     * @param taskType the task type for which poll data is to be fetched
     * @return returns the list of poll data for the task type
     */
    public List<PollData> getPollData(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");

        Object[] params = new Object[]{"taskType", taskType};
        return getForEntity("tasks/queue/polldata", params, pollDataList);
    }

    /**
     * Get the last poll data for all task types
     *
     * @return returns a list of poll data for all task types
     */
    public List<PollData> getAllPollData() {
        return getForEntity("tasks/queue/polldata/all", null, pollDataList);
    }

    /**
     * Requeue pending tasks for all running workflows
     *
     * @return returns the  number of tasks that have been requeued
     */
    public String requeueAllPendingTasks() {
        return postForEntity("tasks/queue/requeue", null, null, String.class);
    }

    /**
     * Requeue pending tasks of a specific task type
     *
     * @return returns the number of tasks that have been requeued
     */
    public String requeuePendingTasksByTaskType(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        return postForEntity("tasks/queue/requeue/{taskType}", null, null, String.class, taskType);
    }

    /**
     * Search for tasks based on payload
     *
     * @param query the search string
     * @return returns the {@link SearchResult} containing the {@link TaskSummary} matching the query
     */
    public SearchResult<TaskSummary> search(String query) {
        return getForEntity("tasks/search", new Object[]{"query", query}, searchResultTaskSummary);
    }

    /**
     * Paginated search for tasks based on payload
     *
     * @param start    start value of page
     * @param size     number of tasks to be returned
     * @param sort     sort order
     * @param freeText additional free text query
     * @param query    the search query
     * @return the {@link SearchResult} containing the {@link TaskSummary} that match the query
     */
    public SearchResult<TaskSummary> search(Integer start, Integer size, String sort, String freeText, String query) {
        Object[] params = new Object[]{"start", start, "size", size, "sort", sort, "freeText", freeText, "query", query};
        return getForEntity("tasks/search", params, searchResultTaskSummary);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#getAllTaskDefs()} instead
     */
    @Deprecated
    public List<TaskDef> getTaskDef() {
        return getForEntity("metadata/taskdefs", null, taskDefList);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#getTaskDef(String)} instead
     */
    @Deprecated
    public TaskDef getTaskDef(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        return getForEntity("metadata/taskdefs/{tasktype}", null, TaskDef.class, taskType);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#unregisterTaskDef(String)} instead
     */
    @Deprecated
    public void unregisterTaskDef(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        delete("metadata/taskdefs/{tasktype}", taskType);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#registerTaskDefs(List)} instead
     */
    @Deprecated
    public void registerTaskDefs(List<TaskDef> taskDefs) {
        Preconditions.checkNotNull(taskDefs, "Task defs cannot be null");
        postForEntity("metadata/taskdefs", taskDefs);
    }
}
