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
package com.netflix.conductor.client.http;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

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



/**
 * @author Viren
 *
 */
public class WorkflowClient extends ClientBase {

	/**
	 * Creates a default task client
	 */
	public WorkflowClient() {
		super();
	}
	
	/**
	 * 
	 * @param config REST Client configuration
	 */
	public WorkflowClient(ClientConfig config) {
		super(config);
	}
	
	/**
	 * 
	 * @param config REST Client configuration
	 * @param handler Jersey client handler.  Useful when plugging in various http client interaction modules (e.g. ribbon)
	 */
	public WorkflowClient(ClientConfig config, ClientHandler handler) {
		super(config, handler);
	}
	
	/**
	 * 
	 * @param config config REST Client configuration
	 * @param handler handler Jersey client handler.  Useful when plugging in various http client interaction modules (e.g. ribbon)
	 * @param filters Chain of client side filters to be applied per request
	 */
	public WorkflowClient(ClientConfig config, ClientHandler handler, ClientFilter...filters) {
		super(config, handler);
		for(ClientFilter filter : filters) {
			super.client.addFilter(filter);
		}
	}

	//Metadata Operations
	
	public List<WorkflowDef> getAllWorkflowDefs() {
		WorkflowDef[] defs = getForEntity("metadata/workflow", null, WorkflowDef[].class);
		return Arrays.asList(defs);
	}
	
	public void registerWorkflow(WorkflowDef def) {
		postForEntity("metadata/workflow", def);
	}
	
	public WorkflowDef getWorkflowDef(@PathParam("name") String name, @QueryParam("version") Integer version) {
		return getForEntity("metadata/workflow/{name}", new Object[]{"version", version}, WorkflowDef.class, name);
	}
	
	//Runtime Operations
	
	public String startWorkflow (String name, Integer version, String correlationId, Map<String, Object> input) {
		Object[] params = new Object[]{"version", version, "correlationId", correlationId};
		return postForEntity("workflow/{name}", input, params, String.class, name);
	}

	public String startWorkflow (StartWorkflowRequest startWorkflowRequest) {
		return postForEntity("workflow", startWorkflowRequest, null, String.class, startWorkflowRequest.getName());
	}

	public Workflow getExecutionStatus(String workflowId, boolean includeTasks) {
		return getWorkflow(workflowId, includeTasks);
	}
	
	public Workflow getWorkflow(String workflowId, boolean includeTasks) {
		return getForEntity("workflow/{workflowId}", new Object[]{"includeTasks", includeTasks}, Workflow.class, workflowId);
	}
	
	public List<Workflow> getWorkflows(String name, String correlationId, boolean includeClosed, boolean includeTasks) {
		Object[] params = new Object[]{"includeClosed", includeClosed, "includeTasks", includeTasks};
		return getForEntity("workflow/{name}/correlated/{correlationId}", params, new GenericType<List<Workflow>>() {}, name, correlationId);
	}
	
	public List<String> getRunningWorkflow(String workflowName, Integer version) {
		return getForEntity("workflow/running/{name}", new Object[]{"version", version}, new GenericType<List<String>>() {}, workflowName);
	}

	public void pauseWorkflow(String workflowId) {
		put("workflow/{workflowId}/pause", null, null, workflowId);		
	}

	public void resumeWorkflow(String workflowId) {
		put("workflow/{workflowId}/resume", null, null, workflowId);		
	}
	
	public void restart(String workflowId) {
		postForEntity1("workflow/{workflowId}/restart", workflowId);		
	}
	
	public void retryLastFailedTask(String workflowId) {
		postForEntity1("workflow/{workflowId}/retry", workflowId);		
	}

	public void resetCallbacksForInProgressTasks(String workflowId) {
		postForEntity1("workflow/{workflowId}//{workflowId}/resetcallbacks", workflowId);		
	}	
	
	public void terminateWorkflow(String workflowId, String reason) {
		delete(new Object[]{"reason", reason}, "workflow/{workflowId}", workflowId);		
	}

	public String rerunWorkflow (String workflowId, RerunWorkflowRequest request) {
		return postForEntity("workflow/{workflowId}/rerun", request, null, String.class, workflowId);
	}
	
	public void skipTaskFromWorkflow(String workflowId, String taskReferenceName) {
		put("workflow/{workflowId}/skiptask/{taskReferenceName}", null, workflowId, taskReferenceName);		
	}

	public void runDecider(String workflowId) {
		put("workflow/decide/{workflowId}", null, null, workflowId);
	}

	public SearchResult<WorkflowSummary> search(String query) {
		SearchResult<WorkflowSummary> result = getForEntity("workflow/search", new Object[]{"query", query}, new GenericType<SearchResult<WorkflowSummary>>() {});
		return result;
	}

	public SearchResult<WorkflowSummary> search(Integer start, Integer size, String sort, String freeText, String query) {
		Object[] params = new Object[]{"start", start, "size", size, "sort", sort, "freeText", freeText, "query", query};
		return getForEntity("workflow/search", params, new GenericType<SearchResult<WorkflowSummary>>() {});
	}
	
}
