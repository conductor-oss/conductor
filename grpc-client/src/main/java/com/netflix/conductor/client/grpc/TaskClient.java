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
package com.netflix.conductor.client.grpc;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskPb;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class TaskClient extends ClientBase {

    private final TaskServiceGrpc.TaskServiceBlockingStub stub;

    public TaskClient(String address, int port) {
        super(address, port);
        this.stub = TaskServiceGrpc.newBlockingStub(this.channel);
    }

    /**
     * Perform a poll for a task of a specific task type.
     *
     * @param taskType The taskType to poll for
     * @param domain The domain of the task type
     * @param workerId Name of the client worker. Used for logging.
     * @return Task waiting to be executed.
     */
    public Task pollTask(String taskType, String workerId, String domain) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(domain), "Domain cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(workerId), "Worker id cannot be blank");

        TaskServicePb.PollResponse response =
                stub.poll(
                        TaskServicePb.PollRequest.newBuilder()
                                .setTaskType(taskType)
                                .setWorkerId(workerId)
                                .setDomain(domain)
                                .build());
        return protoMapper.fromProto(response.getTask());
    }

    /**
     * Perform a batch poll for tasks by task type. Batch size is configurable by count.
     *
     * @param taskType Type of task to poll for
     * @param workerId Name of the client worker. Used for logging.
     * @param count Maximum number of tasks to be returned. Actual number of tasks returned can be
     *     less than this number.
     * @param timeoutInMillisecond Long poll wait timeout.
     * @return List of tasks awaiting to be executed.
     */
    public List<Task> batchPollTasksByTaskType(
            String taskType, String workerId, int count, int timeoutInMillisecond) {
        return Lists.newArrayList(
                batchPollTasksByTaskTypeAsync(taskType, workerId, count, timeoutInMillisecond));
    }

    /**
     * Perform a batch poll for tasks by task type. Batch size is configurable by count. Returns an
     * iterator that streams tasks as they become available through GRPC.
     *
     * @param taskType Type of task to poll for
     * @param workerId Name of the client worker. Used for logging.
     * @param count Maximum number of tasks to be returned. Actual number of tasks returned can be
     *     less than this number.
     * @param timeoutInMillisecond Long poll wait timeout.
     * @return Iterator of tasks awaiting to be executed.
     */
    public Iterator<Task> batchPollTasksByTaskTypeAsync(
            String taskType, String workerId, int count, int timeoutInMillisecond) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(workerId), "Worker id cannot be blank");
        Preconditions.checkArgument(count > 0, "Count must be greater than 0");

        Iterator<TaskPb.Task> it =
                stub.batchPoll(
                        TaskServicePb.BatchPollRequest.newBuilder()
                                .setTaskType(taskType)
                                .setWorkerId(workerId)
                                .setCount(count)
                                .setTimeout(timeoutInMillisecond)
                                .build());

        return Iterators.transform(it, protoMapper::fromProto);
    }

    /**
     * Updates the result of a task execution.
     *
     * @param taskResult TaskResults to be updated.
     */
    public void updateTask(TaskResult taskResult) {
        Preconditions.checkNotNull(taskResult, "Task result cannot be null");
        stub.updateTask(
                TaskServicePb.UpdateTaskRequest.newBuilder()
                        .setResult(protoMapper.toProto(taskResult))
                        .build());
    }

    /**
     * Log execution messages for a task.
     *
     * @param taskId id of the task
     * @param logMessage the message to be logged
     */
    public void logMessageForTask(String taskId, String logMessage) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");
        stub.addLog(
                TaskServicePb.AddLogRequest.newBuilder()
                        .setTaskId(taskId)
                        .setLog(logMessage)
                        .build());
    }

    /**
     * Fetch execution logs for a task.
     *
     * @param taskId id of the task.
     */
    public List<TaskExecLog> getTaskLogs(String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");
        return stub
                .getTaskLogs(
                        TaskServicePb.GetTaskLogsRequest.newBuilder().setTaskId(taskId).build())
                .getLogsList()
                .stream()
                .map(protoMapper::fromProto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve information about the task
     *
     * @param taskId ID of the task
     * @return Task details
     */
    public Task getTaskDetails(String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "Task id cannot be blank");
        return protoMapper.fromProto(
                stub.getTask(TaskServicePb.GetTaskRequest.newBuilder().setTaskId(taskId).build())
                        .getTask());
    }

    public int getQueueSizeForTask(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");

        TaskServicePb.QueueSizesResponse sizes =
                stub.getQueueSizesForTasks(
                        TaskServicePb.QueueSizesRequest.newBuilder()
                                .addTaskTypes(taskType)
                                .build());

        return sizes.getQueueForTaskOrDefault(taskType, 0);
    }

    public SearchResult<TaskSummary> search(String query) {
        return search(null, null, null, null, query);
    }

    public SearchResult<Task> searchV2(String query) {
        return searchV2(null, null, null, null, query);
    }

    public SearchResult<TaskSummary> search(
            @Nullable Integer start,
            @Nullable Integer size,
            @Nullable String sort,
            @Nullable String freeText,
            @Nullable String query) {
        SearchPb.Request searchRequest = createSearchRequest(start, size, sort, freeText, query);
        TaskServicePb.TaskSummarySearchResult result = stub.search(searchRequest);
        return new SearchResult<>(
                result.getTotalHits(),
                result.getResultsList().stream()
                        .map(protoMapper::fromProto)
                        .collect(Collectors.toList()));
    }

    public SearchResult<Task> searchV2(
            @Nullable Integer start,
            @Nullable Integer size,
            @Nullable String sort,
            @Nullable String freeText,
            @Nullable String query) {
        SearchPb.Request searchRequest = createSearchRequest(start, size, sort, freeText, query);
        TaskServicePb.TaskSearchResult result = stub.searchV2(searchRequest);
        return new SearchResult<>(
                result.getTotalHits(),
                result.getResultsList().stream()
                        .map(protoMapper::fromProto)
                        .collect(Collectors.toList()));
    }
}
