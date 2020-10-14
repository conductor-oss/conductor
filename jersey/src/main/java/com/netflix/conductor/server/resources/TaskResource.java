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
package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.service.TaskService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 *
 * @author visingh
 *
 */
@Api(value="/tasks", produces=MediaType.APPLICATION_JSON, consumes=MediaType.APPLICATION_JSON, tags="Task Management")
@Path("/tasks")
@Produces({ MediaType.APPLICATION_JSON })
@Consumes({ MediaType.APPLICATION_JSON })
@Singleton
public class TaskResource {
	private final TaskService taskService;

	@Inject
	public TaskResource(TaskService taskService) {
		this.taskService = taskService;
	}

	@GET
	@Path("/poll/{tasktype}")
	@ApiOperation("Poll for a task of a certain type")
	@Consumes({MediaType.WILDCARD})
	public Task poll(@PathParam("tasktype") String taskType,
					 @QueryParam("workerid") String workerId,
					 @QueryParam("domain") String domain) {
		return taskService.poll(taskType, workerId, domain);
	}

	@GET
	@Path("/poll/batch/{tasktype}")
	@ApiOperation("batch Poll for a task of a certain type")
	@Consumes({MediaType.WILDCARD})
	public List<Task> batchPoll(@PathParam("tasktype") String taskType,
								@QueryParam("workerid") String workerId,
								@QueryParam("domain") String domain,
								@DefaultValue("1") @QueryParam("count") Integer count,
								@DefaultValue("100") @QueryParam("timeout") Integer timeout) {
		return taskService.batchPoll(taskType, workerId, domain, count, timeout);
	}

	@GET
	@Path("/in_progress/{tasktype}")
	@ApiOperation("Get in progress tasks. The results are paginated.")
	@Consumes({MediaType.WILDCARD})
	public List<Task> getTasks(@PathParam("tasktype") String taskType,
							   @QueryParam("startKey") String startKey,
							   @QueryParam("count") @DefaultValue("100") Integer count) {
		return taskService.getTasks(taskType, startKey, count);
	}

	@GET
	@Path("/in_progress/{workflowId}/{taskRefName}")
	@ApiOperation("Get in progress task for a given workflow id.")
	@Consumes({MediaType.WILDCARD})
	public Task getPendingTaskForWorkflow(@PathParam("workflowId") String workflowId,
										  @PathParam("taskRefName") String taskReferenceName) {
		return taskService.getPendingTaskForWorkflow(workflowId, taskReferenceName);
	}

	@POST
	@ApiOperation("Update a task")
	@Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
	public String updateTask(TaskResult taskResult) {
		return taskService.updateTask(taskResult);
	}

	@POST
	@Path("/{taskId}/ack")
	@ApiOperation("Ack Task is received")
	@Consumes({MediaType.WILDCARD})
	public String ack(@PathParam("taskId") String taskId,
					  @QueryParam("workerid") String workerId) {
		return taskService.ackTaskReceived(taskId, workerId);
	}

	@POST
	@Path("/{taskId}/log")
	@ApiOperation("Log Task Execution Details")
	public void log(@PathParam("taskId") String taskId, String log) {
		taskService.log(taskId, log);
	}

	@GET
	@Path("/{taskId}/log")
	@ApiOperation("Get Task Execution Logs")
	public List<TaskExecLog> getTaskLogs(@PathParam("taskId") String taskId) {
		return taskService.getTaskLogs(taskId);
	}

	@GET
	@Path("/{taskId}")
	@ApiOperation("Get task by Id")
	@Consumes(MediaType.WILDCARD)
	public Task getTask(@PathParam("taskId") String taskId) {
		return taskService.getTask(taskId);
	}

	@DELETE
	@Path("/queue/{taskType}/{taskId}")
	@ApiOperation("Remove Task from a Task type queue")
	@Consumes({MediaType.WILDCARD})
	public void removeTaskFromQueue(@PathParam("taskType") String taskType,
									@PathParam("taskId") String taskId) {
		taskService.removeTaskFromQueue(taskType, taskId);
	}

	@GET
	@Path("/queue/sizes")
	@ApiOperation("Get Task type queue sizes")
	@Consumes({MediaType.WILDCARD})
	public Map<String, Integer> size(@QueryParam("taskType") List<String> taskTypes) {
		return taskService.getTaskQueueSizes(taskTypes);
	}

	@GET
	@Path("/queue/all/verbose")
	@ApiOperation("Get the details about each queue")
	@Consumes({MediaType.WILDCARD})
	public Map<String, Map<String, Map<String, Long>>> allVerbose() {
		return taskService.allVerbose();
	}

	@GET
	@Path("/queue/all")
	@ApiOperation("Get the details about each queue")
	@Consumes({MediaType.WILDCARD})
	public Map<String, Long> all() {
		return taskService.getAllQueueDetails();
	}

	@GET
	@Path("/queue/polldata")
	@ApiOperation("Get the last poll data for a given task type")
	@Consumes({MediaType.WILDCARD})
	public List<PollData> getPollData(@QueryParam("taskType") String taskType) {
		return taskService.getPollData(taskType);
	}

	@GET
	@Path("/queue/polldata/all")
	@ApiOperation("Get the last poll data for all task types")
	@Consumes(MediaType.WILDCARD)
	public List<PollData> getAllPollData() {
		return taskService.getAllPollData();
	}

	@Deprecated
	@POST
	@Path("/queue/requeue")
	@ApiOperation("Requeue pending tasks for all the running workflows")
	public String requeue() {
		return taskService.requeue();
	}

	@POST
	@Path("/queue/requeue/{taskType}")
	@ApiOperation("Requeue pending tasks")
	@Consumes(MediaType.WILDCARD)
	@Produces({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON })
	public String requeuePendingTask(@PathParam("taskType") String taskType) {
		return taskService.requeuePendingTask(taskType);
	}

	@ApiOperation(value="Search for tasks based in payload and other parameters",
			notes="use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC." +
					" If order is not specified, defaults to ASC")
	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/search")
	public SearchResult<TaskSummary> search(@QueryParam("start") @DefaultValue("0") int start,
											@QueryParam("size") @DefaultValue("100") int size,
											@QueryParam("sort") String sort,
											@QueryParam("freeText") @DefaultValue("*") String freeText,
											@QueryParam("query") String query) {
		return taskService.search(start, size, sort, freeText, query);
	}

	@GET
	@ApiOperation("Get the external uri where the task payload is to be stored")
	@Consumes(MediaType.WILDCARD)
	@Path("/externalstoragelocation")
	public ExternalStorageLocation getExternalStorageLocation(@QueryParam("path") String path, @QueryParam("operation") String operation, @QueryParam("payloadType") String payloadType) {
		return taskService.getExternalStorageLocation(path, operation, payloadType);
	}
}
