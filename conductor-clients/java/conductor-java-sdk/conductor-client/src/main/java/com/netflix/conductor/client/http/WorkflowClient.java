/*
 * Copyright 2021 Orkes, Inc.
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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.run.WorkflowTestRequest;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.fasterxml.jackson.core.type.TypeReference;


public final class WorkflowClient {

    private ConductorClient client;

    /** Creates a default workflow client */
    public WorkflowClient() {
    }

    public WorkflowClient(ConductorClient client) {
        this.client = client;
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
    }

    /**
     * Starts a workflow. If the size of the workflow input payload is bigger than {@link
     * ExternalPayloadStorage}, if enabled, else the workflow is rejected.
     *
     * @param startWorkflowRequest the {@link StartWorkflowRequest} object to start the workflow
     * @return the id of the workflow instance that can be used for tracking
     */
    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) {
        Validate.notNull(startWorkflowRequest, "StartWorkflowRequest cannot be null");
        Validate.notBlank(startWorkflowRequest.getName(), "Workflow name cannot be null or empty");
        Validate.isTrue(
                StringUtils.isBlank(startWorkflowRequest.getExternalInputPayloadStoragePath()),
                "External Storage Path must not be set");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow")
                .body(startWorkflowRequest)
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Retrieve a workflow by workflow id
     *
     * @param workflowId the id of the workflow
     * @param includeTasks specify if the tasks in the workflow need to be returned
     * @return the requested workflow
     */
    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/{workflowId}")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("includeTasks", includeTasks)
                .build();

        ConductorClientResponse<Workflow> resp = client.execute(request, new TypeReference<>() {
        });

        Workflow workflow = resp.getData();
        populateWorkflowOutput(workflow);
        return workflow;
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
    public List<Workflow> getWorkflows(String name, String correlationId, boolean includeClosed, boolean includeTasks){
        Validate.notBlank(name, "name cannot be blank");
        Validate.notBlank(correlationId, "correlationId cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/{name}/correlated/{correlationId}")
                .addPathParam("name", name)
                .addPathParam("correlationId", correlationId)
                .addQueryParam("includeClosed", includeClosed)
                .addQueryParam("includeTasks", includeTasks)
                .build();

        ConductorClientResponse<List<Workflow>> resp = client.execute(request, new TypeReference<>() {
        });

        List<Workflow> workflows = resp.getData();
        workflows.forEach(this::populateWorkflowOutput);
        return workflows;
    }

    /**
     * Removes a workflow from the system
     *
     * @param workflowId the id of the workflow to be deleted
     * @param archiveWorkflow flag to indicate if the workflow should be archived before deletion
     */
    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) {
        Validate.notBlank(workflowId, "Workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/workflow/{workflowId}/remove")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("archiveWorkflow", archiveWorkflow)
                .build();

        client.execute(request);
    }

    /**
     * Terminates the execution of all given workflows instances
     *
     * @param workflowIds the ids of the workflows to be terminated
     * @param reason the reason to be logged and displayed
     * @return the {@link BulkResponse} contains bulkErrorResults and bulkSuccessfulResults
     */
    public BulkResponse terminateWorkflows(List<String> workflowIds, String reason) {
        Validate.isTrue(!workflowIds.isEmpty(), "workflow id cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/bulk/terminate")
                .addQueryParam("reason", reason)
                .body(workflowIds)
                .build();

        ConductorClientResponse<BulkResponse> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Retrieve all running workflow instances for a given name and version
     *
     * @param workflowName the name of the workflow
     * @param version the version of the wokflow definition. Defaults to 1.
     * @return the list of running workflow instances
     */
    public List<String> getRunningWorkflow(String workflowName, Integer version) {
        return getRunningWorkflow(workflowName, version, null, null);
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
    public List<String> getWorkflowsByTimePeriod(String workflowName, int version, Long startTime, Long endTime) {
        Validate.notBlank(workflowName, "Workflow name cannot be blank");
        Validate.notNull(startTime, "Start time cannot be null");
        Validate.notNull(endTime, "End time cannot be null");

        return getRunningWorkflow(workflowName, version, startTime, endTime);
    }

    /**
     * Starts the decision task for the given workflow instance
     *
     * @param workflowId the id of the workflow instance
     */
    public void runDecider(String workflowId) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/workflow/decide/{workflowId}")
                .addPathParam("workflowId", workflowId)
                .build();

        client.execute(request);
    }

    /**
     * Pause a workflow by workflow id
     *
     * @param workflowId the workflow id of the workflow to be paused
     */
    public void pauseWorkflow(String workflowId) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/workflow/{workflowId}/pause")
                .addPathParam("workflowId", workflowId)
                .build();

        client.execute(request);
    }

    /**
     * Resume a paused workflow by workflow id
     *
     * @param workflowId the workflow id of the paused workflow
     */
    public void resumeWorkflow(String workflowId) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/workflow/{workflowId}/resume")
                .addPathParam("workflowId", workflowId)
                .build();

        client.execute(request);
    }

    /**
     * Skips a given task from a current RUNNING workflow
     *
     * @param workflowId the id of the workflow instance
     * @param taskReferenceName the reference name of the task to be skipped
     */
    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        Validate.notBlank(taskReferenceName, "Task reference name cannot be blank");

        //FIXME skipTaskRequest content is always empty
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/workflow/{workflowId}/skiptask/{taskReferenceName}")
                .addPathParam("workflowId", workflowId)
                .addPathParam("taskReferenceName", taskReferenceName)
                .body(skipTaskRequest) //FIXME review this. It was passed as a query param?!
                .build();

        client.execute(request);
    }

    /**
     * Reruns the workflow from a specific task
     *
     * @param workflowId the id of the workflow
     * @param rerunWorkflowRequest the request containing the task to rerun from
     * @return the id of the workflow
     */
    public String rerunWorkflow(String workflowId, RerunWorkflowRequest rerunWorkflowRequest) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        Validate.notNull(rerunWorkflowRequest, "RerunWorkflowRequest cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/rerun")
                .addPathParam("workflowId", workflowId)
                .body(rerunWorkflowRequest)
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Restart a completed workflow
     *
     * @param workflowId the workflow id of the workflow to be restarted
     * @param useLatestDefinitions if true, use the latest workflow and task definitions when
     *     restarting the workflow if false, use the workflow and task definitions embedded in the
     *     workflow execution when restarting the workflow
     */
    public void restart(String workflowId, boolean useLatestDefinitions) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/restart")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("useLatestDefinitions", useLatestDefinitions)
                .build();

        client.execute(request);
    }

    /**
     * Retries the last failed task in a workflow
     *
     * @param workflowId the workflow id of the workflow with the failed task
     */
    public void retryLastFailedTask(String workflowId) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/retry")
                .addPathParam("workflowId", workflowId)
                .build();

        client.execute(request);
    }

    /**
     * Resets the callback times of all IN PROGRESS tasks to 0 for the given workflow
     *
     * @param workflowId the id of the workflow
     */
    public void resetCallbacksForInProgressTasks(String workflowId) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/{workflowId}/resetcallbacks")
                .addPathParam("workflowId", workflowId)
                .build();
        client.execute(request);
    }

    /**
     * Terminates the execution of the given workflow instance
     *
     * @param workflowId the id of the workflow to be terminated
     * @param reason the reason to be logged and displayed
     */
    public void terminateWorkflow(String workflowId, String reason) {
        Validate.notBlank(workflowId, "workflow id cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/workflow/{workflowId}")
                .addPathParam("workflowId", workflowId)
                .addQueryParam("reason", reason)
                .build();

        client.execute(request);
    }

    /**
     * Search for workflows based on payload
     *
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link WorkflowSummary} that match the query
     */
    public SearchResult<WorkflowSummary> search(String query) {
        return search(null, null, null, "", query);
    }

    /**
     * Search for workflows based on payload
     *
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link Workflow} that match the query
     */
    public SearchResult<Workflow> searchV2(String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/search-v2")
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<Workflow>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
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
            Integer start, Integer size, String sort, String freeText, String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/search")
                .addQueryParam("start", start)
                .addQueryParam("size", size)
                .addQueryParam("sort", sort)
                .addQueryParam("freeText", freeText)
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<WorkflowSummary>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
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
    public SearchResult<Workflow> searchV2(Integer start, Integer size, String sort, String freeText, String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/search-v2")
                .addQueryParam("start", start)
                .addQueryParam("size", size)
                .addQueryParam("sort", sort)
                .addQueryParam("freeText", freeText)
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResult<Workflow>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public Workflow testWorkflow(WorkflowTestRequest testRequest) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/workflow/test")
                .body(testRequest)
                .build();

        ConductorClientResponse<Workflow> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }


    /**
     * Populates the workflow output from external payload storage if the external storage path is
     * specified.
     *
     * @param workflow the workflow for which the output is to be populated.
     */
    private void populateWorkflowOutput(Workflow workflow) {
        //TODO FIXME OSS MISMATCH - https://github.com/conductor-oss/conductor-java-sdk/issues/27
        if (StringUtils.isNotBlank(workflow.getExternalOutputPayloadStoragePath())) {
            throw new UnsupportedOperationException("No external storage support");
        }
    }

    private List<String> getRunningWorkflow(String name, Integer version, Long startTime, Long endTime) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/workflow/running/{name}")
                .addPathParam("name", name)
                .addQueryParam("version", version)
                .addQueryParam("startTime", startTime)
                .addQueryParam("endTime", endTime)
                .build();

        ConductorClientResponse<List<String>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
