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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.UpgradeWorkflowRequest;
import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.run.WorkflowTestRequest;

import io.orkes.conductor.client.model.CorrelationIdsSearchRequest;
import io.orkes.conductor.client.model.WorkflowRun;
import io.orkes.conductor.client.model.WorkflowStateUpdate;
import io.orkes.conductor.client.model.WorkflowStatus;

public class OrkesWorkflowClient implements AutoCloseable {

    private final WorkflowResource workflowResource;

    private final WorkflowBulkResource bulkResource;

    private final WorkflowClient workflowClient;

    private final ExecutorService executorService;

    public OrkesWorkflowClient(ConductorClient client) {
        this(client, 0);
    }

    public OrkesWorkflowClient(ConductorClient client, int executorThreadCount) {
        this.workflowResource = new WorkflowResource(client);
        this.bulkResource = new WorkflowBulkResource(client);
        this.workflowClient = new WorkflowClient(client);
        ThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("WorkflowClient Executor %d")
                .build();

        if (executorThreadCount < 1) {
            this.executorService = Executors.newCachedThreadPool(factory);
        } else {
            this.executorService = Executors.newFixedThreadPool(executorThreadCount, factory);
        }
    }

    @Deprecated
    public CompletableFuture<WorkflowRun> executeWorkflow(StartWorkflowRequest request, String waitUntilTask) {
        return executeWorkflowHttp(request, waitUntilTask);
    }

    /**
     * Synchronously executes a workflow
     * @param request workflow execution request
     * @param waitUntilTask waits until workflow has reached this task.
     *                      Useful for executing it synchronously until this task and then continuing asynchronous execution
     * @param waitForSeconds maximum amount of time to wait before returning
     * @return WorkflowRun
     */
    public CompletableFuture<WorkflowRun> executeWorkflow(StartWorkflowRequest request, String waitUntilTask, Integer waitForSeconds) {
        return executeWorkflowHttp(request, waitUntilTask, waitForSeconds);
    }

    /**
     * Synchronously executes a workflow
     * @param request workflow execution request
     * @param waitUntilTasks waits until workflow has reached one of these tasks.
     *                       Useful for executing it synchronously until this task and then continuing asynchronous execution
     *                       Useful when workflow has multiple branches to wait for any of the branches to reach the task
     * @param waitForSeconds maximum amount of time to wait before returning
     * @return WorkflowRun
     */
    public CompletableFuture<WorkflowRun> executeWorkflow(StartWorkflowRequest request, List<String> waitUntilTasks, Integer waitForSeconds) {
        String waitUntilTask = String.join(",", waitUntilTasks);
        return executeWorkflowHttp(request, waitUntilTask, waitForSeconds);
    }

    /**
     * Synchronously executes a workflow
     * @param request workflow execution request
     * @param waitUntilTask waits until workflow has reached one of these tasks.
     *                       Useful for executing it synchronously until this task and then continuing asynchronous execution
     * @param waitTimeout maximum amount of time to wait before returning
     * @return WorkflowRun
     */
    public WorkflowRun executeWorkflow(StartWorkflowRequest request, String waitUntilTask, Duration waitTimeout) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<WorkflowRun> future = executeWorkflow(request, waitUntilTask);
        return future.get(waitTimeout.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
    }

    public void terminateWorkflowWithFailure(String workflowId, String reason, boolean triggerFailureWorkflow) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        workflowResource.terminateWithAReason(workflowId, reason, triggerFailureWorkflow);
    }

    public BulkResponse pauseWorkflow(List<String> workflowIds) {
        return bulkResource.pauseWorkflows(workflowIds);
    }

    public BulkResponse restartWorkflow(List<String> workflowIds, Boolean useLatestDefinitions) {
        return bulkResource.restartWorkflows(workflowIds, useLatestDefinitions);
    }

    public BulkResponse resumeWorkflow(List<String> workflowIds) {
        return bulkResource.resumeWorkflows(workflowIds);
    }

    public BulkResponse retryWorkflow(List<String> workflowIds) {
        return bulkResource.retryWorkflows(workflowIds);
    }

    public BulkResponse terminateWorkflowsWithFailure(List<String> workflowIds, String reason, boolean triggerFailureWorkflow) {
        return bulkResource.terminateWorkflows(workflowIds, reason, triggerFailureWorkflow);
    }

    public WorkflowStatus getWorkflowStatusSummary(String workflowId, Boolean includeOutput, Boolean includeVariables) {
        return workflowResource.getWorkflowStatusSummary(workflowId, includeOutput, includeVariables);
    }

    public void uploadCompletedWorkflows() {
        workflowResource.uploadCompletedWorkflows();
    }

    public Map<String, List<Workflow>> getWorkflowsByNamesAndCorrelationIds(
            List<String> correlationIds, List<String> workflowNames, Boolean includeClosed, Boolean includeTasks) {
        CorrelationIdsSearchRequest request = new CorrelationIdsSearchRequest(correlationIds, workflowNames);
        return workflowResource.getWorkflowsByNamesAndCorrelationIds(request, includeClosed, includeTasks);
    }

    public Workflow updateVariables(String workflowId, Map<String, Object> variables) {
        return workflowResource.updateVariables(workflowId, variables);
    }

    public void upgradeRunningWorkflow(String workflowId, UpgradeWorkflowRequest upgradeWorkflowRequest) {
        workflowResource.upgradeRunningWorkflow(upgradeWorkflowRequest, workflowId);
    }

    public WorkflowRun updateWorkflow(String workflowId, List<String> waitUntilTaskRefNames, Integer waitForSeconds, WorkflowStateUpdate updateRequest) {
        String joinedReferenceNames = "";
        if (waitUntilTaskRefNames != null) {
            joinedReferenceNames = String.join(",", waitUntilTaskRefNames);
        }
        return workflowResource.updateWorkflowState(updateRequest, UUID.randomUUID().toString(), workflowId, joinedReferenceNames, waitForSeconds);
    }

    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) {
        return workflowClient.startWorkflow(startWorkflowRequest);
    }

    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        return workflowClient.getWorkflow(workflowId, includeTasks);
    }

    public void pauseWorkflow(String workflowId) {
        workflowClient.pauseWorkflow(workflowId);
    }

    public void resumeWorkflow(String workflowId) {
        workflowClient.resumeWorkflow(workflowId);
    }

    public void terminateWorkflow(String workflowId, String reason) {
        workflowClient.terminateWorkflow(workflowId, reason);
    }

    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) {
        workflowClient.deleteWorkflow(workflowId, archiveWorkflow);
    }

    public void retryLastFailedTask(String workflowId) {
        workflowClient.retryLastFailedTask(workflowId);
    }

    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName) {
        workflowClient.skipTaskFromWorkflow(workflowId, taskReferenceName);
    }

    public Workflow testWorkflow(WorkflowTestRequest testRequest) {
        return workflowClient.testWorkflow(testRequest);
    }

    public SearchResult<WorkflowSummary> search(String query) {
        return workflowClient.search(query);
    }

    public SearchResult<WorkflowSummary> search(Integer start, Integer size, String sort, String freeText, String query) {
        return workflowClient.search(start, size, sort, freeText, query);
    }

    @Override
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private CompletableFuture<WorkflowRun> executeWorkflowHttp(StartWorkflowRequest startWorkflowRequest, String waitUntilTask) {
        CompletableFuture<WorkflowRun> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        executorService.submit(
                () -> {
                    try {
                        WorkflowRun response = workflowResource.executeWorkflow(
                                startWorkflowRequest,
                                startWorkflowRequest.getName(),
                                startWorkflowRequest.getVersion(),
                                waitUntilTask,
                                requestId);
                        future.complete(response);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        return future;
    }

    private CompletableFuture<WorkflowRun> executeWorkflowHttp(StartWorkflowRequest startWorkflowRequest, String waitUntilTask, Integer waitForSeconds) {
        CompletableFuture<WorkflowRun> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        executorService.submit(
                () -> {
                    try {
                        WorkflowRun response = workflowResource.executeWorkflow(
                                startWorkflowRequest,
                                startWorkflowRequest.getName(),
                                startWorkflowRequest.getVersion(),
                                waitUntilTask,
                                requestId,
                                waitForSeconds);
                        future.complete(response);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        return future;
    }
}
