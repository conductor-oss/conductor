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


import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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

import com.google.common.base.Preconditions;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;

import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger logger = LoggerFactory.getLogger(TaskResource.class);

	private ExecutionService taskService;

	private QueueDAO queueDAO;

	@Inject
	public TaskResource(ExecutionService taskService, QueueDAO queueDAO) {
		this.taskService = taskService;
		this.queueDAO = queueDAO;
	}

	@GET
	@Path("/poll/{tasktype}")
	@ApiOperation("Poll for a task of a certain type")
	@Consumes({MediaType.WILDCARD})
	public Task poll(@PathParam("tasktype") String taskType,
                     @QueryParam("workerid") String workerId,
                     @QueryParam("domain") String domain) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "TaskType cannot be null or empty.");
		logger.debug("Task being polled: /tasks/poll/{}?{}&{}", taskType, workerId, domain);
		Task task = taskService.getLastPollTask(taskType, workerId, domain);
		if (task != null) {
            logger.debug("The Task {} being returned for /tasks/poll/{}?{}&{}", task, taskType, workerId, domain);
        }
		return task;
	}

	@GET
	@Path("/poll/batch/{tasktype}")
	@ApiOperation("batch Poll for a task of a certain type")
	@Consumes({MediaType.WILDCARD})
	public List<Task> batchPoll(@PathParam("tasktype") String taskType,
								@QueryParam("workerid") String workerId,
								@QueryParam("domain") String domain,
								@DefaultValue("1") @QueryParam("count") Integer count,
								@DefaultValue("100") @QueryParam("timeout") Integer timeout) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "TaskType cannot be null or empty.");
		List<Task> polledTasks = taskService.poll(taskType, workerId, domain, count, timeout);
		//TODO: is it okay to put this in service layer
		logger.debug("The Tasks {} being returned for /tasks/poll/{}?{}&{}",
				polledTasks.stream()
						.map(Task::getTaskId)
						.collect(Collectors.toList()), taskType, workerId, domain);
		return polledTasks;
	}

	@GET
	@Path("/in_progress/{tasktype}")
	@ApiOperation("Get in progress tasks. The results are paginated.")
	@Consumes({MediaType.WILDCARD})
	public List<Task> getTasks(@PathParam("tasktype") String taskType,
                               @QueryParam("startKey") String startKey,
                               @QueryParam("count") @DefaultValue("100") Integer count) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "TaskType cannot be null or empty.");
		return taskService.getTasks(taskType, startKey, count);
	}

	@GET
	@Path("/in_progress/{workflowId}/{taskRefName}")
	@ApiOperation("Get in progress task for a given workflow id.")
	@Consumes({MediaType.WILDCARD})
	public Task getPendingTaskForWorkflow(@PathParam("workflowId") String workflowId,
                                          @PathParam("taskRefName") String taskReferenceName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(workflowId), "WorkflowId cannot be null or empty.");
        Preconditions.checkArgument(StringUtils.isNotBlank(taskReferenceName), "TaskReferenceName cannot be null or empty.");
		return taskService.getPendingTaskForWorkflow(taskReferenceName, workflowId);
	}

	@POST
	@ApiOperation("Update a task")
	public String updateTask(TaskResult taskResult) throws Exception {
		Preconditions.checkArgument(taskResult!=null, "TaskResult cannot be null or empty.");
		logger.debug("Update Task: {} with callback time: {}", taskResult, taskResult.getCallbackAfterSeconds());
		taskService.updateTask(taskResult);
		logger.debug("Task: {} updated successfully with callback time: {}", taskResult, taskResult.getCallbackAfterSeconds());
		return taskResult.getTaskId();
	}

	@POST
	@Path("/{taskId}/ack")
	@ApiOperation("Ack Task is recieved")
	@Consumes({MediaType.WILDCARD})
	public String ack(@PathParam("taskId") String taskId,
                      @QueryParam("workerid") String workerId) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "TaskId cannot be null or empty.");
        logger.debug("Ack received for task: {} from worker: {}", taskId, workerId);
		return String.valueOf(taskService.ackTaskReceived(taskId));
	}
	
	@POST
	@Path("/{taskId}/log")
	@ApiOperation("Log Task Execution Details")
	public void log(@PathParam("taskId") String taskId, String log) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "TaskId cannot be null or empty.");
        taskService.log(taskId, log);
	}
	
	@GET
	@Path("/{taskId}/log")
	@ApiOperation("Get Task Execution Logs")
	public List<TaskExecLog> getTaskLogs(@PathParam("taskId") String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "TaskId cannot be null or empty.");
        return taskService.getTaskLogs(taskId);
	}

	@GET
	@Path("/{taskId}")
	@ApiOperation("Get task by Id")
	@Consumes(MediaType.WILDCARD)
	public Task getTask(@PathParam("taskId") String taskId) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "TaskId cannot be null or empty.");
        return taskService.getTask(taskId);
	}

	@DELETE
	@Path("/queue/{taskType}/{taskId}")
	@ApiOperation("Remove Task from a Task type queue")
	@Consumes({MediaType.WILDCARD})
	public void removeTaskFromQueue(@PathParam("taskType") String taskType,
									@PathParam("taskId") String taskId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "TaskType cannot be null or empty.");
        Preconditions.checkArgument(StringUtils.isNotBlank(taskId), "TaskId cannot be null or empty.");
        taskService.removeTaskfromQueue(taskType, taskId);
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
	    //TODO: add validation here
		return queueDAO.queuesDetailVerbose();
	}

	@GET
	@Path("/queue/all")
	@ApiOperation("Get the details about each queue")
	@Consumes({MediaType.WILDCARD})
	public Map<String, Long> all() {
		return queueDAO.queuesDetail().entrySet().stream()
				.sorted(Comparator.comparing(Entry::getKey))
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
	}

	@GET
	@Path("/queue/polldata")
	@ApiOperation("Get the last poll data for a given task type")
	@Consumes({MediaType.WILDCARD})
	public List<PollData> getPollData(@QueryParam("taskType") String taskType) throws Exception {
		return taskService.getPollData(taskType);
	}

	@GET
	@Path("/queue/polldata/all")
	@ApiOperation("Get the last poll data for all task types")
	@Consumes(MediaType.WILDCARD)
	public List<PollData> getAllPollData() {
		return taskService.getAllPollData();
	}

	@POST
	@Path("/queue/requeue")
	@ApiOperation("Requeue pending tasks for all the running workflows")
	@Consumes(MediaType.WILDCARD)
	@Produces({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON })
	public String requeue() throws Exception {
		return String.valueOf(taskService.requeuePendingTasks());
	}
	
	@POST
	@Path("/queue/requeue/{taskType}")
	@ApiOperation("Requeue pending tasks")
	@Consumes(MediaType.WILDCARD)
	@Produces({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON })
	public String requeue(@PathParam("taskType") String taskType) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "TaskType cannot be null or empty.");
		return String.valueOf(taskService.requeuePendingTasks(taskType));
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
		return taskService.getSearchTasks(query , freeText, start, size, sort);
	}

}
