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
package io.orkes.conductor.client.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientResponse;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;

import io.orkes.conductor.client.enums.ReturnStrategy;
import io.orkes.conductor.client.model.SignalResponse;
import io.orkes.conductor.client.model.WorkflowRun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OrkesTaskClient {

    private final ObjectMapper objectMapper;
    private final TaskClient taskClient;
    private final ConductorClient client;

    public OrkesTaskClient(ConductorClient client) {
        this.client = client;
        this.taskClient = new TaskClient(client);
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    /**
     * Update the task status and output based given workflow id and task reference name
     *
     * @param workflowId        Workflow Id
     * @param taskReferenceName Reference name of the task to be updated
     * @param status            Status of the task
     * @param output            Output for the task
     */
    public void updateTask(String workflowId, String taskReferenceName, TaskResult.Status status, Object output) {
        updateTaskByRefName(getOutputMap(output), workflowId, taskReferenceName, status.toString(), getWorkerId());
    }

    /**
     * Update the task status and output based given workflow id and task reference name and return back the updated workflow status
     *
     * @param workflowId        Workflow Id
     * @param taskReferenceName Reference name of the task to be updated
     * @param status            Status of the task
     * @param output            Output for the task
     * @return Status of the workflow after updating the task
     */
    public Workflow updateTaskSync(String workflowId, String taskReferenceName, TaskResult.Status status, Object output) {
        return updateTaskSync(getOutputMap(output), workflowId, taskReferenceName, status.toString(), getWorkerId());
    }

    /**
     * Signals a workflow with the given status without providing any output
     *
     * @param workflowId Workflow Id of the workflow to be signaled
     * @param status     Signal status to be set for the workflow
     * @param output     Output for the task
     * @return Updated workflow with the applied signal status
     */
    public WorkflowRun signal(String workflowId, String status, Object output) {
        return signal(getOutputMap(output), workflowId, status);
    }

    private Map<String, Object> getOutputMap(Object output) {
        try {
            return objectMapper.convertValue(output, new TypeReference<>() {
            });
        } catch (Exception e) {
            Map<String, Object> outputMap = new HashMap<>();
            outputMap.put("result", output);
            return outputMap;
        }
    }

    //TODO extract this to a strategy that will be used by TaskResource
    private String getWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return System.getenv("HOSTNAME");
        }
    }

    private String updateTaskByRefName(Map<String, Object> output,
                                       String workflowId,
                                       String taskRefName,
                                       String status,
                                       String workerId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.POST)
                .path("/tasks/{workflowId}/{taskRefName}/{status}")
                .addPathParam("workflowId", workflowId)
                .addPathParam("taskRefName", taskRefName)
                .addPathParam("status", status)
                .addQueryParam("workerid", workerId)
                .body(output)
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    private Workflow updateTaskSync(Map<String, Object> output,
                                    String workflowId,
                                    String taskRefName,
                                    String status,
                                    String workerId) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.POST)
                .path("/tasks/{workflowId}/{taskRefName}/{status}/sync")
                .addPathParam("workflowId", workflowId)
                .addPathParam("taskRefName", taskRefName)
                .addPathParam("status", status)
                .addQueryParam("workerid", workerId)
                .body(output)
                .build();

        ConductorClientResponse<Workflow> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Signals a task with default return strategy (TARGET_WORKFLOW)
     */
    public SignalResponse signal(String workflowId, Task.Status status, Map<String, Object> output) {
        return signal(workflowId, status, output, ReturnStrategy.TARGET_WORKFLOW);
    }

    /**
     * Signals a task in a workflow synchronously and returns data based on the specified return strategy.
     *
     * @param workflowId     Workflow Id of the workflow to be signaled
     * @param status         Signal status to be set for the workflow
     * @param output         Output for the task
     * @param returnStrategy Strategy for what data to return
     * @return SignalResponse with data based on the return strategy
     */
    public SignalResponse signal(String workflowId, Task.Status status, Map<String, Object> output, ReturnStrategy returnStrategy) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.POST)
                .path("/tasks/{workflowId}/{status}/signal/sync")
                .addPathParam("workflowId", workflowId)
                .addPathParam("status", status.name())
                .addQueryParam("returnStrategy", returnStrategy.name())
                .body(output)
                .build();

        ConductorClientResponse<SignalResponse> resp = client.execute(request, new TypeReference<SignalResponse>() {
        });
        return resp.getData();
    }

    private WorkflowRun signal(Map<String, Object> output,
                               String workflowId,
                               String status) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.POST)
                .path("/tasks/{workflowId}/{status}/signal")
                .addPathParam("workflowId", workflowId)
                .addPathParam("status", status)
                .body(output)
                .build();

        ConductorClientResponse<WorkflowRun> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    private <T extends SignalResponse> T signalWithReturnStrategy(Map<String, Object> output,
                                                                  String workflowId,
                                                                  Task.Status status,
                                                                  ReturnStrategy returnStrategy,
                                                                  TypeReference<T> responseType) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.POST)
                .path("/tasks/{workflowId}/{status}/signal/sync")
                .addPathParam("workflowId", workflowId)
                .addPathParam("status", status.name())
                .addQueryParam("returnStrategy", returnStrategy.name())
                .body(output)
                .build();

        ConductorClientResponse<T> resp = client.execute(request, responseType);

        return resp.getData();
    }

    // Delegate only methods which are supported

    public Task pollTask(String taskType, String workerId, String domain) {
        return taskClient.pollTask(taskType, workerId, domain);
    }

    public List<Task> batchPollTasksByTaskType(String taskType, String workerId, int count, int timeoutInMillisecond) {
        return taskClient.batchPollTasksByTaskType(taskType, workerId, count, timeoutInMillisecond);
    }

    public List<Task> batchPollTasksInDomain(String taskType, String domain, String workerId, int count, int timeoutInMillisecond) {
        return taskClient.batchPollTasksInDomain(taskType, domain, workerId, count, timeoutInMillisecond);
    }

    public Task updateTaskV2(TaskResult taskResult) {
        return taskClient.updateTaskV2(taskResult);
    }

    public void updateTask(TaskResult taskResult) {
        taskClient.updateTask(taskResult);
    }

    public void logMessageForTask(String taskId, String logMessage) {
        taskClient.logMessageForTask(taskId, logMessage);
    }

    public List<TaskExecLog> getTaskLogs(String taskId) {
        return taskClient.getTaskLogs(taskId);
    }

    public Task getTaskDetails(String taskId) {
        return taskClient.getTaskDetails(taskId);
    }

    public int getQueueSizeForTask(String taskType) {
        Validate.notBlank(taskType, "Task type cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/tasks/queue/sizes")
                .addQueryParams("taskType", List.of(taskType))
                .build();
        ConductorClientResponse<Map<String, Integer>> response = client.execute(request, new TypeReference<>() {
        });

        Integer queueSize = response.getData().get(taskType);
        return queueSize != null ? queueSize : 0;
    }

    public int getQueueSizeForTask(String taskType, String domain, String isolationGroupId, String executionNamespace) {
        // This is not supported by Orkes Conductor
        // taskClient.getQueueSizeForTask(taskType, domain, isolationGroupId, executionNamespace);
        return getQueueSizeForTask(taskType);
    }

    public String requeuePendingTasksByTaskType(String taskType) {
        return taskClient.requeuePendingTasksByTaskType(taskType);
    }

    public SearchResult<TaskSummary> search(Integer start, Integer size, String sort, String freeText, String query) {
        return taskClient.search(start, size, sort, freeText, query);
    }

    public SearchResult<Task> searchV2(Integer start, Integer size, String sort, String freeText, String query) {
        return taskClient.searchV2(start, size, sort, freeText, query);
    }

}
