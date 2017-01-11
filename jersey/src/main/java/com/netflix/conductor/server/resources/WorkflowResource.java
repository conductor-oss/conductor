/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;


/**
 * @author Viren
 *
 */
@Api(value="/workflow", produces=MediaType.APPLICATION_JSON, consumes=MediaType.APPLICATION_JSON, tags="Workflow Management")
@Path("/workflow")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
@Singleton
public class WorkflowResource {
	
	private WorkflowExecutor executor;
	
	private ExecutionService service;
	
	private MetadataService metadata;
	
	private int maxSearchSize;
	
	@Inject
	public WorkflowResource(WorkflowExecutor executor, ExecutionService service, MetadataService metadata, Configuration config) {
		this.executor = executor;
		this.service = service;
		this.metadata = metadata;
		this.maxSearchSize = config.getIntProperty("workflow.max.search.size", 100);
	}

	@POST
	@Path("/{name}")
	@Produces({ MediaType.TEXT_PLAIN })
	@ApiOperation("Start a new workflow.  Returns the ID of the workflow instance that can be later used for tracking")
	public String startWorkflow (
			@PathParam("name") String name, @QueryParam("version") Integer version, 
			@QueryParam("correlationId") String correlationId, Map<String, Object> input) throws Exception {
		
		WorkflowDef def = metadata.getWorkflowDef(name, version);
		if(def == null){
			throw new ApplicationException(Code.NOT_FOUND, "No such workflow found by name=" + name + ", version=" + version);
		}
		return executor.startWorkflow(def.getName(), def.getVersion(), correlationId, input);
	}
	
	@GET
	@Path("/{name}/correlated/{correlationId}")
	@ApiOperation("Lists workflows for the given correlation id")
	@Consumes(MediaType.WILDCARD)
	public List<Workflow> getWorkflows(@PathParam("name") String name, @PathParam("correlationId") String correlationId, 
				@QueryParam("includeClosed") @DefaultValue("false") boolean includeClosed, 
				@QueryParam("includeTasks") @DefaultValue("false") boolean includeTasks) throws Exception {
			return service.getWorkflowInstances(name, correlationId, includeClosed, includeTasks);
	}
	
	@GET
	@Path("/{workflowId}")
	@ApiOperation("Gets the workflow by workflow id")
	@Consumes(MediaType.WILDCARD)
	public Workflow getExecutionStatus(
			@PathParam("workflowId") String workflowId, 
			@QueryParam("includeTasks") @DefaultValue("true") boolean includeTasks) throws Exception {
		return service.getExecutionStatus(workflowId, includeTasks);
	}
	
	@DELETE
	@Path("/{workflowId}/remove")
	@ApiOperation("Removes the workflow from the system")
	@Consumes(MediaType.WILDCARD)
	public void delete(@PathParam("workflowId") String workflowId) throws Exception {
		service.removeWorkflow(workflowId);
	}
	
	@GET
	@Path("/running/{name}")
	@ApiOperation("Retrieve all the running workflows")
	@Consumes(MediaType.WILDCARD)
	public List<String> getRunningWorkflow(@PathParam("name") String workflowName, @QueryParam("version") @DefaultValue("1") Integer version,
											@QueryParam("startTime") Long startTime, @QueryParam("endTime") Long endTime) throws Exception {
		if(startTime != null && endTime != null){
			return executor.getWorkflows(workflowName, version, startTime, endTime);
		} else {
			return executor.getRunningWorkflowIds(workflowName);
		}
	}

	@PUT
	@Path("/decide/{workflowId}")
	@ApiOperation("Starts the decision task for a workflow")
	@Consumes(MediaType.WILDCARD)
	public void decide(@PathParam("workflowId") String workflowId) throws Exception {
		executor.decide(workflowId);
	}

	@PUT
	@Path("/{workflowId}/pause")
	@ApiOperation("Pauses the workflow")
	@Consumes(MediaType.WILDCARD)
	public void pauseWorkflow(@PathParam("workflowId") String workflowId) throws Exception {
		executor.pauseWorkflow(workflowId);
	}

	@PUT
	@Path("/{workflowId}/resume")
	@ApiOperation("Resumes the workflow")
	@Consumes(MediaType.WILDCARD)
	public void resumeWorkflow(@PathParam("workflowId") String workflowId) throws Exception {
		executor.resumeWorkflow(workflowId);
	}

	@PUT
	@Path("/{workflowId}/skiptask/{taskReferenceName}")
	@ApiOperation("Skips a given task from a current running workflow")
	@Consumes(MediaType.WILDCARD)
	public void skipTaskFromWorkflow(@PathParam("workflowId") String workflowId, @PathParam("taskReferenceName") String taskReferenceName,
												SkipTaskRequest skipTaskRequest) throws Exception {
		executor.skipTaskFromWorkflow(workflowId, taskReferenceName, skipTaskRequest);
	}
	
	@POST
	@Path("/{workflowId}/rerun")
	@ApiOperation("Reruns the workflow from a specific task")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
	public String rerun(@PathParam("workflowId") String workflowId, RerunWorkflowRequest request) throws Exception {		
		request.setReRunFromWorkflowId(workflowId);
		return executor.rerun(request);
	}
	
	@POST
	@Path("/{workflowId}/restart")
	@ApiOperation("Restarts a completed workflow")
	@Consumes(MediaType.WILDCARD)
	public void restart(@PathParam("workflowId") String workflowId) throws Exception {		
		executor.rewind(workflowId);
	}
	
	@POST
	@Path("/{workflowId}/retry")
	@ApiOperation("Retries the last failed task")
	@Consumes(MediaType.WILDCARD)
	public void retry(@PathParam("workflowId") String workflowId) throws Exception {		
		executor.retry(workflowId);
	}
	
	@DELETE
	@Path("/{workflowId}")
	@ApiOperation("Terminate workflow execution")
	@Consumes(MediaType.WILDCARD)
	public void terminate(@PathParam("workflowId") String workflowId, @QueryParam("reason") String reason) throws Exception {		
		executor.terminateWorkflow(workflowId, reason);
	}
	
	@ApiOperation(value="Search for workflows based in payload and other parameters", notes="use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC.  If order is not specified, defaults to ASC")
	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/search")
    public SearchResult<WorkflowSummary> search(
    		@QueryParam("start") @DefaultValue("0") int start,
    		@QueryParam("size") @DefaultValue("100") int size,
    		@QueryParam("sort") String sort,
    		@QueryParam("freeText") @DefaultValue("*") String freeText,
    		@QueryParam("query") String query
    		){

		if(size > maxSearchSize) {
			throw new ApplicationException(Code.INVALID_INPUT, "Cannot return more than " + maxSearchSize + " workflows.  Please use pagination");
		}
		return service.search(query , freeText, start, size, convert(sort));
	}

	private List<String> convert(String sortStr) {
		List<String> list = new ArrayList<String>();
		if(sortStr != null && sortStr.length() != 0){
			list = Arrays.asList(sortStr.split("\\|"));
		}
		return list;
	}
}
