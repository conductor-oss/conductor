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
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Map;


/**
 * @author Viren
 */
public class WorkflowClient extends ClientBase {

    private static GenericType<List<WorkflowDef>> workflowDefList = new GenericType<List<WorkflowDef>>() {
    };

    private static GenericType<SearchResult<WorkflowSummary>> searchResultWorkflowSummary = new GenericType<SearchResult<WorkflowSummary>>() {
    };

    /**
     * Creates a default task client
     */
    public WorkflowClient() {
        super();
    }

    /**
     * @param config REST Client configuration
     */
    public WorkflowClient(ClientConfig config) {
        super(config);
    }

    /**
     * @param config  REST Client configuration
     * @param handler Jersey client handler.  Useful when plugging in various http client interaction modules (e.g. ribbon)
     */
    public WorkflowClient(ClientConfig config, ClientHandler handler) {
        super(config, handler);
    }

    /**
     * @param config  config REST Client configuration
     * @param handler handler Jersey client handler.  Useful when plugging in various http client interaction modules (e.g. ribbon)
     * @param filters Chain of client side filters to be applied per request
     */
    public WorkflowClient(ClientConfig config, ClientHandler handler, ClientFilter... filters) {
        super(config, handler);
        for (ClientFilter filter : filters) {
            super.client.addFilter(filter);
        }
    }


    //Metadata Operations

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#getAllWorkflowDefs()} instead
     */
    @Deprecated
    public List<WorkflowDef> getAllWorkflowDefs() {
        return getForEntity("metadata/workflow", null, workflowDefList);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#registerWorkflowDef(WorkflowDef)} instead
     */
    @Deprecated
    public void registerWorkflow(WorkflowDef workflowDef) {
        Preconditions.checkNotNull(workflowDef, "Worfklow definition cannot be null");
        postForEntity("metadata/workflow", workflowDef);
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link MetadataClient#getWorkflowDef(String, Integer)} instead
     */
    @Deprecated
    public WorkflowDef getWorkflowDef(String name, Integer version) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be blank");
        return getForEntity("metadata/workflow/{name}", new Object[]{"version", version}, WorkflowDef.class, name);
    }


    //Runtime Operations

    /**
     * Starts a workflow identified by the name and version
     *
     * @param name          the name of the workflow
     * @param version       the version of the workflow def
     * @param correlationId the correlation id
     * @param input         the input to set in the workflow
     * @return the id of the workflow instance that can be used for tracking
     */
    public String startWorkflow(String name, Integer version, String correlationId, Map<String, Object> input) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be blank");

        Object[] params = new Object[]{"version", version, "correlationId", correlationId};
        return postForEntity("workflow/{name}", input, params, String.class, name);
    }

    /**
     * Starts a workflow
     *
     * @param startWorkflowRequest the {@link StartWorkflowRequest} object to start the workflow
     * @return the id of the workflow instance that can be used for tracking
     */
    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) {
        Preconditions.checkNotNull(startWorkflowRequest, "StartWorkflowRequest cannot be null");
        return postForEntity("workflow", startWorkflowRequest, null, String.class, startWorkflowRequest.getName());
    }

    /**
     * @deprecated This API is deprecated and will be removed in the next version
     * use {@link #getWorkflow(String, boolean)} instead
     */
    @Deprecated
    public Workflow getExecutionStatus(String workflowId, boolean includeTasks) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        return getWorkflow(workflowId, includeTasks);
    }

    /**
     * Retrieve a workflow by workflow id
     *
     * @param workflowId   the id of the workflow
     * @param includeTasks specify if the tasks in the workflow need to be returned
     * @return the requested workflow
     */
    public Workflow getWorkflow(String workflowId, boolean includeTasks) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        return getForEntity("workflow/{workflowId}", new Object[]{"includeTasks", includeTasks}, Workflow.class, workflowId);
    }

    /**
     * Retrieve all workflows for a given correlation id and name
     *
     * @param name          the name of the workflow
     * @param correlationId the correlation id
     * @param includeClosed specify if all workflows are to be returned or only running workflows
     * @param includeTasks  specify if the tasks in the workflow need to be returned
     * @return list of workflows for the given correlation id and name
     */
    public List<Workflow> getWorkflows(String name, String correlationId, boolean includeClosed, boolean includeTasks) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(correlationId), "correlationId cannot be blank");

        Object[] params = new Object[]{"includeClosed", includeClosed, "includeTasks", includeTasks};
        return getForEntity("workflow/{name}/correlated/{correlationId}", params, new GenericType<List<Workflow>>() {
        }, name, correlationId);
    }

    /**
     * Removes a workflow from the system
     *
     * @param workflowId      the id of the workflow to be deleted
     * @param archiveWorkflow flag to indicate if the workflow should be archived before deletion
     */
    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "Workflow id cannot be blank");

        Object[] params = new Object[]{"archiveWorkflow", archiveWorkflow};
        delete(params, "workflow/{workflowId}/remove", workflowId);
    }

    /**
     * Retrieve all running workflow instances for a given name and version
     *
     * @param workflowName the name of the workflow
     * @param version      the version of the wokflow definition. Defaults to 1.
     * @return the list of running workflow instances
     */
    public List<String> getRunningWorkflow(String workflowName, Integer version) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowName), "Workflow name cannot be blank");
        return getForEntity("workflow/running/{name}", new Object[]{"version", version}, new GenericType<List<String>>() {}, workflowName);
    }

    /**
     * Retrieve all workflow instances for a given workflow name between a specific time period
     *
     * @param workflowName the name of the workflow
     * @param version      the version of the workflow definition. Defaults to 1.
     * @param startTime    the start time of the period
     * @param endTime      the end time of the period
     * @return returns a list of workflows created during the specified during the time period
     */
    public List<String> getWorkflowsByTimePeriod(String workflowName, int version, Long startTime, Long endTime) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowName), "Workflow name cannot be blank");
        Preconditions.checkNotNull(startTime, "Start time cannot be null");
        Preconditions.checkNotNull(endTime, "End time cannot be null");

        Object[] params = new Object[]{"version", version, "startTime", startTime, "endTime", endTime};
        return getForEntity("workflow/running/{name}", params, new GenericType<List<String>>() {}, workflowName);
    }

    /**
     * Starts the decision task for the given workflow instance
     *
     * @param workflowId the id of the workflow instance
     */
    public void runDecider(String workflowId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        put("workflow/decide/{workflowId}", null, null, workflowId);
    }

    /**
     * Pause a workflow by workflow id
     *
     * @param workflowId the workflow id of the workflow to be paused
     */
    public void pauseWorkflow(String workflowId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        put("workflow/{workflowId}/pause", null, null, workflowId);
    }

    /**
     * Resume a paused workflow by workflow id
     *
     * @param workflowId the workflow id of the paused workflow
     */
    public void resumeWorkflow(String workflowId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        put("workflow/{workflowId}/resume", null, null, workflowId);
    }

    /**
     * Skips a given task from a current RUNNING workflow
     *
     * @param workflowId        the id of the workflow instance
     * @param taskReferenceName the reference name of the task to be skipped
     */
    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(taskReferenceName), "Task reference name cannot be blank");

        put("workflow/{workflowId}/skiptask/{taskReferenceName}", null, workflowId, taskReferenceName);
    }

    /**
     * Reruns the workflow from a specific task
     *
     * @param workflowId           the id of the workflow
     * @param rerunWorkflowRequest the request containing the task to rerun from
     * @return the id of the workflow
     */
    public String rerunWorkflow(String workflowId, RerunWorkflowRequest rerunWorkflowRequest) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        Preconditions.checkNotNull(rerunWorkflowRequest, "RerunWorkflowRequest cannot be null");

        return postForEntity("workflow/{workflowId}/rerun", rerunWorkflowRequest, null, String.class, workflowId);
    }

    /**
     * Restart a completed workflow
     *
     * @param workflowId the workflow id of the workflow to be restarted
     */
    public void restart(String workflowId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        postForEntity1("workflow/{workflowId}/restart", workflowId);
    }

    /**
     * Retries the last failed task in a workflow
     *
     * @param workflowId the workflow id of the workflow with the failed task
     */
    public void retryLastFailedTask(String workflowId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        postForEntity1("workflow/{workflowId}/retry", workflowId);
    }

    /**
     * Resets the callback times of all IN PROGRESS tasks to 0 for the given workflow
     *
     * @param workflowId the id of the workflow
     */
    public void resetCallbacksForInProgressTasks(String workflowId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        postForEntity1("workflow/{workflowId}/resetcallbacks", workflowId);
    }

    /**
     * Terminates the execution of the given workflow instance
     *
     * @param workflowId the id of the workflow to be terminated
     * @param reason     the reason to be logged and displayed
     */
    public void terminateWorkflow(String workflowId, String reason) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "workflow id cannot be blank");
        delete(new Object[]{"reason", reason}, "workflow/{workflowId}", workflowId);
    }

    /**
     * Search for workflows based on payload
     *
     * @param query the search query
     * @return the {@link SearchResult} containing the {@link WorkflowSummary} that match the query
     */
    public SearchResult<WorkflowSummary> search(String query) {
        return getForEntity("workflow/search", new Object[]{"query", query}, searchResultWorkflowSummary);
    }

    /**
     * Paginated search for workflows based on payload
     *
     * @param start    start value of page
     * @param size     number of workflows to be returned
     * @param sort     sort order
     * @param freeText additional free text query
     * @param query    the search query
     * @return the {@link SearchResult} containing the {@link WorkflowSummary} that match the query
     */
    public SearchResult<WorkflowSummary> search(Integer start, Integer size, String sort, String freeText, String query) {
        Object[] params = new Object[]{"start", start, "size", size, "sort", sort, "freeText", freeText, "query", query};
        return getForEntity("workflow/search", params, searchResultWorkflowSummary);
    }
}
