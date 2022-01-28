/*
 * Copyright 2021 Netflix, Inc.
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServicePb;
import com.netflix.conductor.proto.WorkflowPb;

import com.google.common.base.Preconditions;

public class WorkflowClient extends ClientBase {

    private final WorkflowServiceGrpc.WorkflowServiceBlockingStub stub;

    public WorkflowClient(String address, int port) {
        super(address, port);
        this.stub = WorkflowServiceGrpc.newBlockingStub(this.channel);
    }

    /**
     * Starts a workflow
     *
     * @param startWorkflowRequest the {@link StartWorkflowRequest} object to start the workflow
     * @return the id of the workflow instance that can be used for tracking
     */
    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) {
        Preconditions.checkNotNull(startWorkflowRequest, "StartWorkflowRequest cannot be null");
        return stub.startWorkflow(protoMapper.toProto(startWorkflowRequest)).getWorkflowId();
    }

    /**
     * Retrieve a workflow by workflow id
     *
     * @param workflowId the id of the workflow
     * @param includeTasks specify if the tasks in the workflow need to be returned
     * @return the requested workflow
     */
    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        WorkflowPb.Workflow workflow =
                stub.getWorkflowStatus(
                        WorkflowServicePb.GetWorkflowStatusRequest.newBuilder()
                                .setWorkflowId(workflowId)
                                .setIncludeTasks(includeTasks)
                                .build());
        return protoMapper.fromProto(workflow);
    }

    /**
     * Retrieve all workflows for a given correlation id and name
     *
     * @param name the name of the workflow
     * @param correlationId the correlation id
     * @param includeClosed specify if all workflows are to be returned or only running workflows
     * @param includeTasks specify if the tasks in the workflow need to be returned
     * @return list of workflows for the given correlation id and name
     */
    public List<Workflow> getWorkflows(
            String name, String correlationId, boolean includeClosed, boolean includeTasks) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be blank");
        Preconditions.checkArgument(
                StringUtils.isNotBlank(correlationId), "correlationId cannot be blank");

        WorkflowServicePb.GetWorkflowsResponse workflows =
                stub.getWorkflows(
                        WorkflowServicePb.GetWorkflowsRequest.newBuilder()
                                .setName(name)
                                .addCorrelationId(correlationId)
                                .setIncludeClosed(includeClosed)
                                .setIncludeTasks(includeTasks)
                                .build());

        if (!workflows.containsWorkflowsById(correlationId)) {
            return Collections.emptyList();
        }

        return workflows.getWorkflowsByIdOrThrow(correlationId).getWorkflowsList().stream()
                .map(protoMapper::fromProto)
                .collect(Collectors.toList());
    }

    /**
     * Removes a workflow from the system
     *
     * @param workflowId the id of the workflow to be deleted
     * @param archiveWorkflow flag to indicate if the workflow should be archived before deletion
     */
    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "Workflow id cannot be blank");
        stub.removeWorkflow(
                WorkflowServicePb.RemoveWorkflowRequest.newBuilder()
                        .setWorkflodId(workflowId)
                        .setArchiveWorkflow(archiveWorkflow)
                        .build());
    }

    /*
     * Retrieve all running workflow instances for a given name and version
     *
     * @param workflowName the name of the workflow
     * @param version      the version of the wokflow definition. Defaults to 1.
     * @return the list of running workflow instances
     */
    public List<String> getRunningWorkflow(String workflowName, @Nullable Integer version) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowName), "Workflow name cannot be blank");

        WorkflowServicePb.GetRunningWorkflowsResponse workflows =
                stub.getRunningWorkflows(
                        WorkflowServicePb.GetRunningWorkflowsRequest.newBuilder()
                                .setName(workflowName)
                                .setVersion(version == null ? 1 : version)
                                .build());
        return workflows.getWorkflowIdsList();
    }

    /**
     * Retrieve all workflow instances for a given workflow name between a specific time period
     *
     * @param workflowName the name of the workflow
     * @param version the version of the workflow definition. Defaults to 1.
     * @param startTime the start time of the period
     * @param endTime the end time of the period
     * @return returns a list of workflows created during the specified during the time period
     */
    public List<String> getWorkflowsByTimePeriod(
            String workflowName, int version, Long startTime, Long endTime) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowName), "Workflow name cannot be blank");
        Preconditions.checkNotNull(startTime, "Start time cannot be null");
        Preconditions.checkNotNull(endTime, "End time cannot be null");
        // TODO
        return null;
    }

    /*
     * Starts the decision task for the given workflow instance
     *
     * @param workflowId the id of the workflow instance
     */
    public void runDecider(String workflowId) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.decideWorkflow(
                WorkflowServicePb.DecideWorkflowRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .build());
    }

    /**
     * Pause a workflow by workflow id
     *
     * @param workflowId the workflow id of the workflow to be paused
     */
    public void pauseWorkflow(String workflowId) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.pauseWorkflow(
                WorkflowServicePb.PauseWorkflowRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .build());
    }

    /**
     * Resume a paused workflow by workflow id
     *
     * @param workflowId the workflow id of the paused workflow
     */
    public void resumeWorkflow(String workflowId) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.resumeWorkflow(
                WorkflowServicePb.ResumeWorkflowRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .build());
    }

    /**
     * Skips a given task from a current RUNNING workflow
     *
     * @param workflowId the id of the workflow instance
     * @param taskReferenceName the reference name of the task to be skipped
     */
    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        Preconditions.checkArgument(
                StringUtils.isNotBlank(taskReferenceName), "Task reference name cannot be blank");
        stub.skipTaskFromWorkflow(
                WorkflowServicePb.SkipTaskRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskReferenceName(taskReferenceName)
                        .build());
    }

    /**
     * Reruns the workflow from a specific task
     *
     * @param rerunWorkflowRequest the request containing the task to rerun from
     * @return the id of the workflow
     */
    public String rerunWorkflow(RerunWorkflowRequest rerunWorkflowRequest) {
        Preconditions.checkNotNull(rerunWorkflowRequest, "RerunWorkflowRequest cannot be null");
        return stub.rerunWorkflow(protoMapper.toProto(rerunWorkflowRequest)).getWorkflowId();
    }

    /**
     * Restart a completed workflow
     *
     * @param workflowId the workflow id of the workflow to be restarted
     */
    public void restart(String workflowId, boolean useLatestDefinitions) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.restartWorkflow(
                WorkflowServicePb.RestartWorkflowRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .setUseLatestDefinitions(useLatestDefinitions)
                        .build());
    }

    /**
     * Retries the last failed task in a workflow
     *
     * @param workflowId the workflow id of the workflow with the failed task
     */
    public void retryLastFailedTask(String workflowId, boolean resumeSubworkflowTasks) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.retryWorkflow(
                WorkflowServicePb.RetryWorkflowRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .setResumeSubworkflowTasks(resumeSubworkflowTasks)
                        .build());
    }

    /**
     * Resets the callback times of all IN PROGRESS tasks to 0 for the given workflow
     *
     * @param workflowId the id of the workflow
     */
    public void resetCallbacksForInProgressTasks(String workflowId) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.resetWorkflowCallbacks(
                WorkflowServicePb.ResetWorkflowCallbacksRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .build());
    }

    /**
     * Terminates the execution of the given workflow instance
     *
     * @param workflowId the id of the workflow to be terminated
     * @param reason the reason to be logged and displayed
     */
    public void terminateWorkflow(String workflowId, String reason) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        stub.terminateWorkflow(
                WorkflowServicePb.TerminateWorkflowRequest.newBuilder()
                        .setWorkflowId(workflowId)
                        .setReason(reason)
                        .build());
    }

    /**
     * Search for workflows based on payload
     *
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link WorkflowSummary} that match the query
     */
    public SearchResult<WorkflowSummary> search(String query) {
        return search(null, null, null, null, query);
    }

    /**
     * Search for workflows based on payload
     *
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link Workflow} that match the query
     */
    public SearchResult<Workflow> searchV2(String query) {
        return searchV2(null, null, null, null, query);
    }

    /**
     * Paginated search for workflows based on payload
     *
     * @param start start value of page
     * @param size number of workflows to be returned
     * @param sort sort order
     * @param freeText additional free text query
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link WorkflowSummary} that match the query
     */
    public SearchResult<WorkflowSummary> search(
            @Nullable Integer start,
            @Nullable Integer size,
            @Nullable String sort,
            @Nullable String freeText,
            @Nullable String query) {

        SearchPb.Request searchRequest = createSearchRequest(start, size, sort, freeText, query);
        WorkflowServicePb.WorkflowSummarySearchResult result = stub.search(searchRequest);
        return new SearchResult<>(
                result.getTotalHits(),
                result.getResultsList().stream()
                        .map(protoMapper::fromProto)
                        .collect(Collectors.toList()));
    }

    /**
     * Paginated search for workflows based on payload
     *
     * @param start start value of page
     * @param size number of workflows to be returned
     * @param sort sort order
     * @param freeText additional free text query
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link Workflow} that match the query
     */
    public SearchResult<Workflow> searchV2(
            @Nullable Integer start,
            @Nullable Integer size,
            @Nullable String sort,
            @Nullable String freeText,
            @Nullable String query) {
        SearchPb.Request searchRequest = createSearchRequest(start, size, sort, freeText, query);
        WorkflowServicePb.WorkflowSearchResult result = stub.searchV2(searchRequest);
        return new SearchResult<>(
                result.getTotalHits(),
                result.getResultsList().stream()
                        .map(protoMapper::fromProto)
                        .collect(Collectors.toList()));
    }
}
