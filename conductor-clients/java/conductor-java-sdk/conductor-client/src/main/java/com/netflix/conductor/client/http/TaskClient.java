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
package com.netflix.conductor.client.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.netflix.conductor.client.config.ConductorClientConfiguration;
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration;
import com.netflix.conductor.client.events.dispatcher.EventDispatcher;
import com.netflix.conductor.client.events.listeners.ListenerRegister;
import com.netflix.conductor.client.events.listeners.TaskClientListener;
import com.netflix.conductor.client.events.task.TaskClientEvent;
import com.netflix.conductor.client.events.task.TaskPayloadUsedEvent;
import com.netflix.conductor.client.events.task.TaskResultPayloadSizeEvent;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/** Client for conductor task management including polling for task, updating task status etc. */
@Slf4j
public final class TaskClient {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final ConductorClientConfiguration conductorClientConfiguration;

    private final EventDispatcher<TaskClientEvent> eventDispatcher = new EventDispatcher<>();

    private PayloadStorage payloadStorage;

    private ConductorClient client;

    /** Creates a default task client */
    public TaskClient() {
        // client will be set once root uri is set
        this(null, new DefaultConductorClientConfiguration());
    }

    public TaskClient(ConductorClient client) {
        this(client, new DefaultConductorClientConfiguration());
    }

    public TaskClient(ConductorClient client, ConductorClientConfiguration config) {
        this.client = client;
        this.payloadStorage = new PayloadStorage(client);
        this.conductorClientConfiguration = config;
    }

    /**
     * Kept only for backwards compatibility
     *
     * @param rootUri basePath for the ApiClient
     */
    @Deprecated
    public void setRootURI(String rootUri) {
        if (client != null) {
            client.shutdown();
        }
        client = new ConductorClient(rootUri);
        payloadStorage = new PayloadStorage(client);
    }

    public void registerListener(TaskClientListener listener) {
        ListenerRegister.register(listener, eventDispatcher);
    }

    /**
     * Perform a poll for a task of a specific task type.
     *
     * @param taskType The taskType to poll for
     * @param domain The domain of the task type
     * @param workerId Name of the client worker. Used for logging.
     * @return Task waiting to be executed.
     */
    public Task pollTask(String taskType, String workerId, String domain){
        Validate.notBlank(taskType, "Task type cannot be blank");
        Validate.notBlank(workerId, "Worker id cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/poll/{taskType}")
                .addPathParam("taskType", taskType)
                .addQueryParam("workerid", workerId)
                .addQueryParam("domain", domain)
                .build();

        ConductorClientResponse<Task> resp = client.execute(request, new TypeReference<>() {
        });

        Task task = resp.getData();
        populateTaskPayloads(task);
        return task;
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
    public List<Task> batchPollTasksByTaskType(String taskType, String workerId, int count, int timeoutInMillisecond) {
        Validate.notBlank(taskType, "Task type cannot be blank");
        Validate.notBlank(workerId, "Worker id cannot be blank");
        Validate.isTrue(count > 0, "Count must be greater than 0");

        List<Task> tasks = batchPoll(taskType, workerId, null, count, timeoutInMillisecond);
        tasks.forEach(this::populateTaskPayloads);
        return tasks;
    }

    /**
     * Batch poll for tasks in a domain. Batch size is configurable by count.
     *
     * @param taskType Type of task to poll for
     * @param domain The domain of the task type
     * @param workerId Name of the client worker. Used for logging.
     * @param count Maximum number of tasks to be returned. Actual number of tasks returned can be
     *     less than this number.
     * @param timeoutInMillisecond Long poll wait timeout.
     * @return List of tasks awaiting to be executed.
     */
    public List<Task> batchPollTasksInDomain(String taskType, String domain, String workerId, int count, int timeoutInMillisecond){
        Validate.notBlank(taskType, "Task type cannot be blank");
        Validate.notBlank(workerId, "Worker id cannot be blank");
        Validate.isTrue(count > 0, "Count must be greater than 0");

        List<Task> tasks = batchPoll(taskType, workerId, domain, count, timeoutInMillisecond);
        tasks.forEach(this::populateTaskPayloads);
        return tasks;
    }

    /**
     * Updates the result of a task execution. If the size of the task output payload is bigger than
     * {@link ExternalPayloadStorage}, if enabled, else the task is marked as
     * FAILED_WITH_TERMINAL_ERROR.
     *
     * @param taskResult the {@link TaskResult} of the executed task to be updated.
     */
    public void updateTask(TaskResult taskResult) {
        Validate.notNull(taskResult, "Task result cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/tasks")
                .body(taskResult)
                .build();

        client.execute(request);
    }

    /**
     * Updates the result of a task execution. If the size of the task output payload is bigger than
     * {@link ExternalPayloadStorage}, if enabled, else the task is marked as
     * FAILED_WITH_TERMINAL_ERROR.
     *
     * @param taskResult the {@link TaskResult} of the executed task to be updated.
     */
    public Task updateTaskV2(TaskResult taskResult) {
        Validate.notNull(taskResult, "Task result cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
            .method(Method.POST)
            .path("/tasks/update-v2")
            .body(taskResult)
            .build();

        ConductorClientResponse<Task> response = client.execute(request, new TypeReference<>() {
        });
        return response.getData();
    }

    public Optional<String> evaluateAndUploadLargePayload(Map<String, Object> taskOutputData, String taskType) {
        if (!conductorClientConfiguration.isEnforceThresholds()) {
            return Optional.empty();
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            objectMapper.writeValue(byteArrayOutputStream, taskOutputData);
            byte[] taskOutputBytes = byteArrayOutputStream.toByteArray();
            long taskResultSize = taskOutputBytes.length;
            eventDispatcher.publish(new TaskResultPayloadSizeEvent(taskType, taskResultSize));
            long payloadSizeThreshold = conductorClientConfiguration.getTaskOutputPayloadThresholdKB() * 1024L;
            if (taskResultSize > payloadSizeThreshold) {
                if (!conductorClientConfiguration.isExternalPayloadStorageEnabled()  || taskResultSize
                        > conductorClientConfiguration.getTaskOutputMaxPayloadThresholdKB() * 1024L) {
                    throw new IllegalArgumentException(
                            String.format("The TaskResult payload size: %d is greater than the permissible %d bytes",
                                    taskResultSize, payloadSizeThreshold));
                }
                eventDispatcher.publish(new TaskPayloadUsedEvent(taskType,
                        ExternalPayloadStorage.Operation.WRITE.name(),
                        ExternalPayloadStorage.PayloadType.TASK_OUTPUT.name()));
                return Optional.of(uploadToExternalPayloadStorage(taskOutputBytes, taskResultSize));
            }
            return Optional.empty();
        } catch (IOException e) {
            String errorMsg = String.format("Unable to update task: %s with task result", taskType);
            log.error(errorMsg, e);
            throw new ConductorClientException(e);
        }
    }
    /**
     * Ack for the task poll.
     *
     * @param taskId Id of the task to be polled
     * @param workerId user identified worker.
     * @return true if the task was found with the given ID and acknowledged. False otherwise. If
     *     the server returns false, the client should NOT attempt to ack again.
     */
    public Boolean ack(String taskId, String workerId) {
        Validate.notBlank(taskId, "Task id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("tasks/{taskId}/ack")
                .addPathParam("taskId", taskId)
                .addQueryParam("workerid", workerId)
                .build();

        ConductorClientResponse<Boolean> response = client.execute(request, new TypeReference<>() {
        });

        return response.getData();
    }

    /**
     * Log execution messages for a task.
     *
     * @param taskId id of the task
     * @param logMessage the message to be logged
     */
    public void logMessageForTask(String taskId, String logMessage) {
        Validate.notBlank(taskId, "Task id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/tasks/{taskId}/log")
                .addPathParam("taskId", taskId)
                .body(logMessage)
                .build();

        client.execute(request);
    }

    /**
     * Fetch execution logs for a task.
     *
     * @param taskId id of the task.
     */
    public List<TaskExecLog> getTaskLogs(String taskId){
        Validate.notBlank(taskId, "Task id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/{taskId}/log")
                .addPathParam("taskId", taskId)
                .build();

        ConductorClientResponse<List<TaskExecLog>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Retrieve information about the task
     *
     * @param taskId ID of the task
     * @return Task details
     */
    public Task getTaskDetails(String taskId) {
        Validate.notBlank(taskId, "Task id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/{taskId}")
                .addPathParam("taskId", taskId)
                .build();

        ConductorClientResponse<Task> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Removes a task from a taskType queue
     *
     * @param taskType the taskType to identify the queue
     * @param taskId the id of the task to be removed
     */
    public void removeTaskFromQueue(String taskType, String taskId) {
        Validate.notBlank(taskType, "Task type cannot be blank");
        Validate.notBlank(taskId, "Task id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("tasks/queue/{taskType}/{taskId}")
                .addPathParam("taskType", taskType)
                .addPathParam("taskId", taskId)
                .build();

        client.execute(request);
    }

    public int getQueueSizeForTask(String taskType) {
        return getQueueSizeForTask(taskType, null, null, null);
    }

    public int getQueueSizeForTask(String taskType, String domain, String isolationGroupId, String executionNamespace) {
        Validate.notBlank(taskType, "Task type cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/queue/size")  //FIXME Not supported by Orkes Conductor. Orkes Conductor only has "/tasks/queue/sizes"
                .addQueryParam("taskType", taskType)
                .addQueryParam("domain", domain)
                .addQueryParam("isolationGroupId", isolationGroupId)
                .addQueryParam("executionNamespace", executionNamespace)
                .build();
        ConductorClientResponse<Integer> resp = client.execute(request, new TypeReference<>() {
        });

        Integer queueSize = resp.getData();
        return queueSize != null ? queueSize : 0;
    }

    /**
     * Get last poll data for a given task type
     *
     * @param taskType the task type for which poll data is to be fetched
     * @return returns the list of poll data for the task type
     */
    public List<PollData> getPollData(String taskType) {
        Validate.notBlank(taskType, "Task type cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/queue/polldata")
                .addQueryParam("taskType", taskType)
                .build();
        ConductorClientResponse<List<PollData>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Get the last poll data for all task types
     *
     * @return returns a list of poll data for all task types
     */
    public List<PollData> getAllPollData() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/queue/polldata")
                .build();
        ConductorClientResponse<List<PollData>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Requeue pending tasks for all running workflows
     *
     * @return returns the number of tasks that have been requeued
     */
    public String requeueAllPendingTasks() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/tasks/queue/requeue")
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Requeue pending tasks of a specific task type
     *
     * @return returns the number of tasks that have been requeued
     */
    public String requeuePendingTasksByTaskType(String taskType) {
        Validate.notBlank(taskType, "Task type cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/tasks/queue/requeue/{taskType}")
                .addPathParam("taskType", taskType)
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
    /**
     * Search for tasks based on payload
     *
     * @param query the search string
     * @return returns the {@link SearchResult} containing the {@link TaskSummary} matching the
     *     query
     */
    public SearchResult<TaskSummary> search(String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/search")
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<TaskSummary>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Search for tasks based on payload
     *
     * @param query the search string
     * @return returns the {@link SearchResult} containing the {@link Task} matching the query
     */
    public SearchResult<Task> searchV2(String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("tasks/search-v2")
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<Task>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Paginated search for tasks based on payload
     *
     * @param start start value of page
     * @param size number of tasks to be returned
     * @param sort sort order
     * @param freeText additional free text query
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link TaskSummary} that match the query
     */
    public SearchResult<TaskSummary> search(Integer start, Integer size, String sort, String freeText, String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/search")
                .addQueryParam("start", start)
                .addQueryParam("size", size)
                .addQueryParam("sort", sort)
                .addQueryParam("freeText", freeText)
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<TaskSummary>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Paginated search for tasks based on payload
     *
     * @param start start value of page
     * @param size number of tasks to be returned
     * @param sort sort order
     * @param freeText additional free text query
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link Task} that match the query
     */
    public SearchResult<Task> searchV2(Integer start, Integer size, String sort, String freeText, String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("tasks/search-v2")
                .addQueryParam("start", start)
                .addQueryParam("size", size)
                .addQueryParam("sort", sort)
                .addQueryParam("freeText", freeText)
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<Task>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    private void populateTaskPayloads(Task task) {
        if (!conductorClientConfiguration.isEnforceThresholds()) {
            return;
        }

        if (StringUtils.isNotBlank(task.getExternalInputPayloadStoragePath())) {
            eventDispatcher.publish(new TaskPayloadUsedEvent(task.getTaskDefName(),
                    ExternalPayloadStorage.Operation.READ.name(),
                    ExternalPayloadStorage.PayloadType.TASK_INPUT.name()));
            task.setInputData(
                    downloadFromExternalStorage(
                            ExternalPayloadStorage.PayloadType.TASK_INPUT,
                            task.getExternalInputPayloadStoragePath()));
            task.setExternalInputPayloadStoragePath(null);
        }

        if (StringUtils.isNotBlank(task.getExternalOutputPayloadStoragePath())) {
            eventDispatcher.publish(new TaskPayloadUsedEvent(task.getTaskDefName(),
                    ExternalPayloadStorage.Operation.READ.name(),
                    ExternalPayloadStorage.PayloadType.TASK_OUTPUT.name()));
            task.setOutputData(
                    downloadFromExternalStorage(
                            ExternalPayloadStorage.PayloadType.TASK_OUTPUT,
                            task.getExternalOutputPayloadStoragePath()));
            task.setExternalOutputPayloadStoragePath(null);
        }
    }

    private List<Task> batchPoll(String taskType, String workerid, String domain, Integer count, Integer timeout) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/tasks/poll/batch/{taskType}")
                .addPathParam("taskType", taskType)
                .addQueryParam("workerid", workerid)
                .addQueryParam("domain", domain)
                .addQueryParam("count", count)
                .addQueryParam("timeout", timeout)
                .build();

        ConductorClientResponse<List<Task>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    private String uploadToExternalPayloadStorage(byte[] payloadBytes, long payloadSize) {
        ExternalStorageLocation externalStorageLocation =
                payloadStorage.getLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, "");
        payloadStorage.upload(
                externalStorageLocation.getUri(),
                new ByteArrayInputStream(payloadBytes),
                payloadSize);
        return externalStorageLocation.getPath();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> downloadFromExternalStorage(ExternalPayloadStorage.PayloadType payloadType, String path) {
        Validate.notBlank(path, "uri cannot be blank");
        ExternalStorageLocation externalStorageLocation = payloadStorage.getLocation(ExternalPayloadStorage.Operation.READ,
                payloadType, path);
        try (InputStream inputStream = payloadStorage.download(externalStorageLocation.getUri())) {
            return objectMapper.readValue(inputStream, Map.class);
        } catch (IOException e) {
            String errorMsg = String.format("Unable to download payload from external storage location: %s", path);
            log.error(errorMsg, e);
            throw new ConductorClientException(e);
        }
    }
}
