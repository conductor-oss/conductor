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
/**
 *
 */
package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.service.WorkflowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;


/**
 * @author Viren
 */
@Api(value = "/workflow", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON, tags = "Workflow Management")
@Path("/workflow")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
@Singleton
public class WorkflowResource {

    private final WorkflowService workflowService;

    @Inject
    public WorkflowResource(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @POST
    @Produces({MediaType.TEXT_PLAIN})
    @ApiOperation("Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain")
    public String startWorkflow(StartWorkflowRequest request) {
        return workflowService.startWorkflow(request);
    }

    @POST
    @Path("/{name}")
    @Produces({MediaType.TEXT_PLAIN})
    @ApiOperation("Start a new workflow. Returns the ID of the workflow instance that can be later used for tracking")
    public String startWorkflow(@PathParam("name") String name,
                                @QueryParam("version") Integer version,
                                @QueryParam("correlationId") String correlationId,
                                @QueryParam("priority") @DefaultValue("0") Integer priority,
                                Map<String, Object> input) {
        return workflowService.startWorkflow(name, version, correlationId, priority, input);
    }

    @GET
    @Path("/{name}/correlated/{correlationId}")
    @ApiOperation("Lists workflows for the given correlation id")
    @Consumes(MediaType.WILDCARD)
    public List<Workflow> getWorkflows(@PathParam("name") String name,
                                       @PathParam("correlationId") String correlationId,
                                       @QueryParam("includeClosed") @DefaultValue("false") boolean includeClosed,
                                       @QueryParam("includeTasks") @DefaultValue("false") boolean includeTasks) {
        return workflowService.getWorkflows(name, correlationId, includeClosed, includeTasks);
    }

    @POST
    @Path("/{name}/correlated")
    @ApiOperation("Lists workflows for the given correlation id list")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, List<Workflow>> getWorkflows(@PathParam("name") String name,
                                                    @QueryParam("includeClosed") @DefaultValue("false") boolean includeClosed,
                                                    @QueryParam("includeTasks") @DefaultValue("false") boolean includeTasks,
                                                    List<String> correlationIds) {
        return workflowService.getWorkflows(name, includeClosed, includeTasks, correlationIds);
    }

    @GET
    @Path("/{workflowId}")
    @ApiOperation("Gets the workflow by workflow id")
    @Consumes(MediaType.WILDCARD)
    public Workflow getExecutionStatus(@PathParam("workflowId") String workflowId,
                                       @QueryParam("includeTasks") @DefaultValue("true") boolean includeTasks) {
        return workflowService.getExecutionStatus(workflowId, includeTasks);
    }

    @DELETE
    @Path("/{workflowId}/remove")
    @ApiOperation("Removes the workflow from the system")
    @Consumes(MediaType.WILDCARD)
    public void delete(@PathParam("workflowId") String workflowId,
                       @QueryParam("archiveWorkflow") @DefaultValue("true") boolean archiveWorkflow) {
        workflowService.deleteWorkflow(workflowId, archiveWorkflow);
    }

    @GET
    @Path("/running/{name}")
    @ApiOperation("Retrieve all the running workflows")
    @Consumes(MediaType.WILDCARD)
    public List<String> getRunningWorkflow(@PathParam("name") String workflowName,
                                           @QueryParam("version") @DefaultValue("1") Integer version,
                                           @QueryParam("startTime") Long startTime,
                                           @QueryParam("endTime") Long endTime) {
        return workflowService.getRunningWorkflows(workflowName, version, startTime, endTime);
    }

    @PUT
    @Path("/decide/{workflowId}")
    @ApiOperation("Starts the decision task for a workflow")
    @Consumes(MediaType.WILDCARD)
    public void decide(@PathParam("workflowId") String workflowId) {
        workflowService.decideWorkflow(workflowId);
    }

    @PUT
    @Path("/{workflowId}/pause")
    @ApiOperation("Pauses the workflow")
    @Consumes(MediaType.WILDCARD)
    public void pauseWorkflow(@PathParam("workflowId") String workflowId) {
        workflowService.pauseWorkflow(workflowId);
    }

    @PUT
    @Path("/{workflowId}/resume")
    @ApiOperation("Resumes the workflow")
    @Consumes(MediaType.WILDCARD)
    public void resumeWorkflow(@PathParam("workflowId") String workflowId) {
        workflowService.resumeWorkflow(workflowId);
    }

    @PUT
    @Path("/{workflowId}/skiptask/{taskReferenceName}")
    @ApiOperation("Skips a given task from a current running workflow")
    @Consumes(MediaType.APPLICATION_JSON)
    public void skipTaskFromWorkflow(@PathParam("workflowId") String workflowId,
                                     @PathParam("taskReferenceName") String taskReferenceName,
                                     SkipTaskRequest skipTaskRequest) {
        workflowService.skipTaskFromWorkflow(workflowId, taskReferenceName, skipTaskRequest);
    }

    @POST
    @Path("/{workflowId}/rerun")
    @ApiOperation("Reruns the workflow from a specific task")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String rerun(@PathParam("workflowId") String workflowId,
                        RerunWorkflowRequest request) {
        return workflowService.rerunWorkflow(workflowId, request);
    }

    @POST
    @Path("/{workflowId}/restart")
    @ApiOperation("Restarts a completed workflow")
    @Consumes(MediaType.WILDCARD)
    public void restart(@PathParam("workflowId") String workflowId, @QueryParam("useLatestDefinitions") @DefaultValue("false") boolean useLatestDefinitions) {
        workflowService.restartWorkflow(workflowId, useLatestDefinitions);
    }

    @POST
    @Path("/{workflowId}/retry")
    @ApiOperation("Retries the last failed task")
    @Consumes(MediaType.WILDCARD)
    public void retry(@PathParam("workflowId") String workflowId) {
        workflowService.retryWorkflow(workflowId);
    }

    @POST
    @Path("/{workflowId}/resetcallbacks")
    @ApiOperation("Resets callback times of all non-terminal SIMPLE tasks to 0")
    @Consumes(MediaType.WILDCARD)
    public void resetWorkflow(@PathParam("workflowId") String workflowId) {
        workflowService.resetWorkflow(workflowId);
    }

    @DELETE
    @Path("/{workflowId}")
    @ApiOperation("Terminate workflow execution")
    @Consumes(MediaType.WILDCARD)
    public void terminate(@PathParam("workflowId") String workflowId,
                          @QueryParam("reason") String reason) {
        workflowService.terminateWorkflow(workflowId, reason);
    }

    @ApiOperation(value = "Search for workflows based on payload and other parameters",
            notes = "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC." +
                    " If order is not specified, defaults to ASC.")
    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/search")
    public SearchResult<WorkflowSummary> search(@QueryParam("start") @DefaultValue("0") int start,
                                                @QueryParam("size") @DefaultValue("100") int size,
                                                @QueryParam("sort") String sort,
                                                @QueryParam("freeText") @DefaultValue("*") String freeText,
                                                @QueryParam("query") String query) {
        return workflowService.searchWorkflows(start, size, sort, freeText, query);
    }

    @ApiOperation(value = "Search for workflows based on task parameters",
            notes = "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC." +
                    " If order is not specified, defaults to ASC")
    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/search-by-tasks")
    public SearchResult<WorkflowSummary> searchWorkflowsByTasks(@QueryParam("start") @DefaultValue("0") int start,
                                                                @QueryParam("size") @DefaultValue("100") int size,
                                                                @QueryParam("sort") String sort,
                                                                @QueryParam("freeText") @DefaultValue("*") String freeText,
                                                                @QueryParam("query") String query) {
        return workflowService.searchWorkflowsByTasks(start, size, sort, freeText, query);
    }

    @GET
    @ApiOperation("Get the uri and path of the external storage where the workflow payload is to be stored")
    @Consumes(MediaType.WILDCARD)
    @Path("/externalstoragelocation")
    public ExternalStorageLocation getExternalStorageLocation(@QueryParam("path") String path, @QueryParam("operation") String operation, @QueryParam("payloadType") String payloadType) {
        return workflowService.getExternalStorageLocation(path, operation, payloadType);
    }
}