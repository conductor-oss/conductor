/*
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
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dyno.DynoProxy;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Trace
public class RedisExecutionDAO extends BaseDynoDAO implements ExecutionDAO {

	public static final Logger logger = LoggerFactory.getLogger(RedisExecutionDAO.class);

	// Keys Families
	private static final String TASK_LIMIT_BUCKET = "TASK_LIMIT_BUCKET";
	private static final String TASK_RATE_LIMIT_BUCKET = "TASK_RATE_LIMIT_BUCKET";
	private final static String IN_PROGRESS_TASKS = "IN_PROGRESS_TASKS";
	private final static String TASKS_IN_PROGRESS_STATUS = "TASKS_IN_PROGRESS_STATUS";	//Tasks which are in IN_PROGRESS status.
	private final static String WORKFLOW_TO_TASKS = "WORKFLOW_TO_TASKS";
	private final static String SCHEDULED_TASKS = "SCHEDULED_TASKS";
	private final static String TASK = "TASK";

	private final static String WORKFLOW = "WORKFLOW";
	private final static String PENDING_WORKFLOWS = "PENDING_WORKFLOWS";
	private final static String WORKFLOW_DEF_TO_WORKFLOWS = "WORKFLOW_DEF_TO_WORKFLOWS";
	private final static String CORR_ID_TO_WORKFLOWS = "CORR_ID_TO_WORKFLOWS";
	private final static String POLL_DATA = "POLL_DATA";

	private final static String EVENT_EXECUTION = "EVENT_EXECUTION";

	@Inject
	public RedisExecutionDAO(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
		super(dynoClient, objectMapper, config);
	}

	@Override
	public List<Task> getPendingTasksByWorkflow(String taskName, String workflowId) {
		List<Task> tasks = new LinkedList<>();

		List<Task> pendingTasks = getPendingTasksForTaskType(taskName);
		pendingTasks.forEach(pendingTask -> {
			if (pendingTask.getWorkflowInstanceId().equals(workflowId)) {
				tasks.add(pendingTask);
			}
		});

		return tasks;
	}

	@Override
	public List<Task> getTasks(String taskDefName, String startKey, int count) {
		List<Task> tasks = new LinkedList<>();

		List<Task> pendingTasks = getPendingTasksForTaskType(taskDefName);
		boolean startKeyFound = startKey == null;
		int foundcount = 0;
		for (Task pendingTask : pendingTasks) {
			if (!startKeyFound) {
				if (pendingTask.getTaskId().equals(startKey)) {
					startKeyFound = true;
					if (startKey != null) {
						continue;
					}
				}
			}
			if (startKeyFound && foundcount < count) {
				tasks.add(pendingTask);
				foundcount++;
			}
		}
		return tasks;
	}

	@Override
	public List<Task> createTasks(List<Task> tasks) {

		List<Task> tasksCreated = new LinkedList<>();

		for (Task task : tasks) {
		    validate(task);

			recordRedisDaoRequests("createTask", task.getTaskType(), task.getWorkflowType());

			task.setScheduledTime(System.currentTimeMillis());

			String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();
			Long added = dynoClient.hset(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey, task.getTaskId());
			if (added < 1) {
				logger.debug("Task already scheduled, skipping the run " + task.getTaskId() + ", ref=" + task.getReferenceTaskName() + ", key=" + taskKey);
				continue;
			}

			correlateTaskToWorkflowInDS(task.getTaskId(), task.getWorkflowInstanceId());
			logger.debug("Scheduled task added to WORKFLOW_TO_TASKS workflowId: {}, taskId: {}, taskType: {} during createTasks",
                    task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType());

			String inProgressTaskKey = nsKey(IN_PROGRESS_TASKS, task.getTaskDefName());
			dynoClient.sadd(inProgressTaskKey, task.getTaskId());
			logger.debug("Scheduled task added to IN_PROGRESS_TASKS with inProgressTaskKey: {}, workflowId: {}, taskId: {}, taskType: {} during createTasks",
                    inProgressTaskKey, task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType());

			updateTask(task);
			tasksCreated.add(task);
		}

		return tasksCreated;

	}

	@Override
	public void updateTasks(List<Task> tasks) {
		for (Task task : tasks) {
			updateTask(task);
		}
	}

	@Override
	public void updateTask(Task task) {
		task.setUpdateTime(System.currentTimeMillis());
		if (task.getStatus() != null && task.getStatus().isTerminal() && task.getEndTime() == 0) {
			task.setEndTime(System.currentTimeMillis());
		}

		Optional<TaskDef> taskDefinition = task.getTaskDefinition();

		if(taskDefinition.isPresent() && taskDefinition.get().concurrencyLimit() > 0) {

			if(task.getStatus() != null && task.getStatus().equals(Status.IN_PROGRESS)) {
				dynoClient.sadd(nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
				logger.debug("Workflow Task added to TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
						nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName(), task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType(), task.getStatus().name()));
			}else {
				dynoClient.srem(nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
				logger.debug("Workflow Task removed from TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
						nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName(), task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType(), task.getStatus().name()));
				String key = nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName());
				dynoClient.zrem(key, task.getTaskId());
				logger.debug("Workflow Task removed from TASK_LIMIT_BUCKET with taskLimitBucketKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
						key, task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType(), task.getStatus().name());
			}
		}

		String payload = toJson(task);
		recordRedisDaoPayloadSize("updateTask", payload.length(), taskDefinition
				.map(TaskDef::getName)
				.orElse("n/a"), task.getWorkflowType());

		recordRedisDaoRequests("updateTask", task.getTaskType(), task.getWorkflowType());
		dynoClient.set(nsKey(TASK, task.getTaskId()), payload);
		logger.debug("Workflow task payload saved to TASK with taskKey: {}, workflowId: {}, taskId: {}, taskType: {} during updateTask",
				nsKey(TASK, task.getTaskId()), task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType());
		if (task.getStatus() != null && task.getStatus().isTerminal()) {
			dynoClient.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
			logger.debug("Workflow Task removed from TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
					nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType(), task.getStatus().name());
		}

        Set<String> taskIds = dynoClient.smembers(nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()));
		if (!taskIds.contains(task.getTaskId())) {
		    correlateTaskToWorkflowInDS(task.getTaskId(), task.getWorkflowInstanceId());
        }
	}

	/**
	 * This method evaluates if the {@link Task} is rate limited or not based on {@link Task#getRateLimitPerFrequency()}
	 * and {@link Task#getRateLimitFrequencyInSeconds()}
	 *
	 * The rate limiting is implemented using the Redis constructs of sorted set and TTL of each element in the rate limited bucket.
	 * <ul>
	 *     <li>All the entries that are in the not in the frequency bucket are cleaned up by leveraging {@link DynoProxy#zremrangeByScore(String, String, String)},
	 *     this is done to make the next step of evaluation efficient</li>
	 *     <li>A current count(tasks executed within the frequency) is calculated based on the current time and the beginning of the rate limit frequency time(which is current time - {@link Task#getRateLimitFrequencyInSeconds()} in millis),
	 *     this is achieved by using {@link DynoProxy#zcount(String, double, double)} </li>
	 *     <li>Once the count is calculated then a evaluation is made to determine if it is within the bounds of {@link Task#getRateLimitPerFrequency()}, if so the count is increased and an expiry TTL is added to the entry</li>
	 * </ul>
	 *
	 * @param task: which needs to be evaluated whether it is rateLimited or not
	 * @return true: If the {@link Task} is rateLimited
	 * 		false: If the {@link Task} is not rateLimited
	 */
	@Override
	public boolean exceedsRateLimitPerFrequency(Task task) {
		int rateLimitPerFrequency = task.getRateLimitPerFrequency();
		int rateLimitFrequencyInSeconds = task.getRateLimitFrequencyInSeconds();
		if (rateLimitPerFrequency <= 0 || rateLimitFrequencyInSeconds <=0) {
			logger.debug("Rate limit not applied to the Task: {}  either rateLimitPerFrequency: {} or rateLimitFrequencyInSeconds: {} is 0 or less",
					task, rateLimitPerFrequency, rateLimitFrequencyInSeconds);
			return false;
		} else {
			logger.debug("Evaluating rate limiting for Task: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {}",
					task, rateLimitPerFrequency, rateLimitFrequencyInSeconds);
			long currentTimeEpochMillis = System.currentTimeMillis();
			long currentTimeEpochMinusRateLimitBucket = currentTimeEpochMillis - (rateLimitFrequencyInSeconds * 1000);
			String key = nsKey(TASK_RATE_LIMIT_BUCKET, task.getTaskDefName());
			dynoClient.zremrangeByScore(key, "-inf", String.valueOf(currentTimeEpochMinusRateLimitBucket));
			int currentBucketCount = Math.toIntExact(
					dynoClient.zcount(key,
							currentTimeEpochMinusRateLimitBucket,
							currentTimeEpochMillis));
			if (currentBucketCount < rateLimitPerFrequency) {
				dynoClient.zadd(key, currentTimeEpochMillis, String.valueOf(currentTimeEpochMillis));
				dynoClient.expire(key, rateLimitFrequencyInSeconds);
				logger.info("Task: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} within the rate limit with current count {}",
						task, rateLimitPerFrequency, rateLimitFrequencyInSeconds, ++currentBucketCount);
				Monitors.recordTaskRateLimited(task.getTaskDefName(), rateLimitPerFrequency);
				return false;
			} else {
				logger.info("Task: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} is out of bounds of rate limit with current count {}",
						task, rateLimitPerFrequency, rateLimitFrequencyInSeconds, currentBucketCount);
				return true;
			}
		}
	}


	@Override
	public boolean exceedsInProgressLimit(Task task) {
		Optional<TaskDef> taskDefinition = task.getTaskDefinition();
		if(!taskDefinition.isPresent()) {
			return false;
		}
		int limit = taskDefinition.get().concurrencyLimit();
		if(limit <= 0) {
			return false;
		}

		long current = getInProgressTaskCount(task.getTaskDefName());
		if(current >= limit) {
			logger.info("Task execution count limited. task - {}:{}, limit: {}, current: {}", task.getTaskId(), task.getTaskDefName(), limit, current);
			Monitors.recordTaskConcurrentExecutionLimited(task.getTaskDefName(), limit);
			return true;
		}

		String rateLimitKey = nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName());
		double score = System.currentTimeMillis();
		String taskId = task.getTaskId();
		dynoClient.zaddnx(rateLimitKey, score, taskId);
		recordRedisDaoRequests("checkTaskRateLimiting", task.getTaskType(), task.getWorkflowType());

		Set<String> ids = dynoClient.zrangeByScore(rateLimitKey, 0, score + 1, limit);
		boolean rateLimited = !ids.contains(taskId);
		if(rateLimited) {
			logger.info("Task execution count limited. task - {}:{}, limit: {}, current: {}", task.getTaskId(), task.getTaskDefName(), limit, current);
			String inProgressKey = nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName());
			//Cleanup any items that are still present in the rate limit bucket but not in progress anymore!
			ids.stream()
					.filter(id -> !dynoClient.sismember(inProgressKey, id))
					.forEach(id2 -> dynoClient.zrem(rateLimitKey, id2));
			Monitors.recordTaskRateLimited(task.getTaskDefName(), limit);
		}
		return rateLimited;
	}

	@Override
	public void removeTask(String taskId) {

		Task task = getTask(taskId);
		if(task == null) {
			logger.warn("No such Task by id {}", taskId);
			return;
		}
		String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();

		dynoClient.hdel(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey);
		dynoClient.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
		dynoClient.srem(nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()), task.getTaskId());
		dynoClient.srem(nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
		dynoClient.del(nsKey(TASK, task.getTaskId()));
		dynoClient.zrem(nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName()), task.getTaskId());
		recordRedisDaoRequests("removeTask", task.getTaskType(), task.getWorkflowType());
	}

    @Override
    public Task getTask(String taskId) {
        Preconditions.checkNotNull(taskId, "taskId cannot be null");
        return Optional.ofNullable(dynoClient.get(nsKey(TASK, taskId)))
                .map(json -> {
                    Task task = readValue(json, Task.class);
                    recordRedisDaoRequests("getTask", task.getTaskType(), task.getWorkflowType());
                    recordRedisDaoPayloadSize("getTask", toJson(task).length(), task.getTaskType(), task.getWorkflowType());
                    return task;
                })
                .orElse(null);
    }

	@Override
	public List<Task> getTasks(List<String> taskIds) {
		return taskIds.stream()
				.map(taskId -> nsKey(TASK, taskId))
				.map(dynoClient::get)
				.filter(Objects::nonNull)
				.map(jsonString -> {
					Task task = readValue(jsonString, Task.class);
					recordRedisDaoRequests("getTask", task.getTaskType(), task.getWorkflowType());
					recordRedisDaoPayloadSize("getTask", jsonString.length(), task.getTaskType(), task.getWorkflowType());
					return task;
				})
				.collect(Collectors.toList());
	}

	@Override
	public List<Task> getTasksForWorkflow(String workflowId) {
		Preconditions.checkNotNull(workflowId, "workflowId cannot be null");
		Set<String> taskIds = dynoClient.smembers(nsKey(WORKFLOW_TO_TASKS, workflowId));
		recordRedisDaoRequests("getTasksForWorkflow");
		return getTasks(new ArrayList<>(taskIds));
	}

	@Override
	public List<Task> getPendingTasksForTaskType(String taskName) {
		Preconditions.checkNotNull(taskName, "task name cannot be null");
		Set<String> taskIds = dynoClient.smembers(nsKey(IN_PROGRESS_TASKS, taskName));
		recordRedisDaoRequests("getPendingTasksForTaskType");
		return getTasks(new ArrayList<>(taskIds));
	}

	@Override
	public String createWorkflow(Workflow workflow) {
		workflow.setCreateTime(System.currentTimeMillis());
		return insertOrUpdateWorkflow(workflow, false);
	}

	@Override
	public String updateWorkflow(Workflow workflow) {
		workflow.setUpdateTime(System.currentTimeMillis());
		return insertOrUpdateWorkflow(workflow, true);
	}

	@Override
	public void removeWorkflow(String workflowId) {
			Workflow wf = getWorkflow(workflowId, true);
			recordRedisDaoRequests("removeWorkflow");

			// Remove from lists
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, wf.getWorkflowName(), dateStr(wf.getCreateTime()));
			dynoClient.srem(key, workflowId);
			dynoClient.srem(nsKey(CORR_ID_TO_WORKFLOWS, wf.getCorrelationId()), workflowId);
			dynoClient.srem(nsKey(PENDING_WORKFLOWS, wf.getWorkflowName()), workflowId);

			// Remove the object
			dynoClient.del(nsKey(WORKFLOW, workflowId));
			for(Task task : wf.getTasks()) {
				removeTask(task.getTaskId());
			}
	}

	@Override
	public void removeFromPendingWorkflow(String workflowType, String workflowId) {
		recordRedisDaoRequests("removePendingWorkflow");
		dynoClient.srem(nsKey(PENDING_WORKFLOWS, workflowType), workflowId);
	}

	@Override
	public Workflow getWorkflow(String workflowId) {
		return getWorkflow(workflowId, true);
	}

	@Override
	public Workflow getWorkflow(String workflowId, boolean includeTasks) {
		String json = dynoClient.get(nsKey(WORKFLOW, workflowId));
		Workflow workflow = null;

		if(json != null) {
			workflow = readValue(json, Workflow.class);
			recordRedisDaoRequests("getWorkflow", "n/a", workflow.getWorkflowName());
			recordRedisDaoPayloadSize("getWorkflow", json.length(),"n/a", workflow.getWorkflowName());
			if (includeTasks) {
				List<Task> tasks = getTasksForWorkflow(workflowId);
				tasks.sort(Comparator.comparingLong(Task::getScheduledTime).thenComparingInt(Task::getSeq));
				workflow.setTasks(tasks);
			}
		}
		return workflow;
	}

	@Override
	public List<String> getRunningWorkflowIds(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<String> workflowIds;
		recordRedisDaoRequests("getRunningWorkflowsByName");
		Set<String> pendingWorkflows = dynoClient.smembers(nsKey(PENDING_WORKFLOWS, workflowName));
		workflowIds = new LinkedList<>(pendingWorkflows);
		return workflowIds;
	}

	@Override
	public List<Workflow> getPendingWorkflowsByType(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<Workflow> workflows = new LinkedList<>();
		List<String> wfIds = getRunningWorkflowIds(workflowName);
		for(String wfId : wfIds) {
			workflows.add(getWorkflow(wfId));
		}
		return workflows;
	}

	@Override
	public List<Workflow> getWorkflowsByType(String workflowName, Long startTime, Long endTime) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		Preconditions.checkNotNull(startTime, "startTime cannot be null");
		Preconditions.checkNotNull(endTime, "endTime cannot be null");

		List<Workflow> workflows = new LinkedList<>();

		// Get all date strings between start and end
		List<String> dateStrs = dateStrBetweenDates(startTime, endTime);
		dateStrs.forEach(dateStr -> {
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflowName, dateStr);
			dynoClient.smembers(key).forEach(workflowId -> {
				try {
					Workflow workflow = getWorkflow(workflowId);
					if (workflow.getCreateTime() >= startTime && workflow.getCreateTime() <= endTime) {
						workflows.add(workflow);
					}
				}catch(Exception e) {
					logger.error("Failed to get workflow: {}", workflowId, e);
				}
			});
		});


		return workflows;
	}

	@Override
	public List<Workflow> getWorkflowsByCorrelationId(String correlationId, boolean includeTasks) {
		throw new UnsupportedOperationException("This method is not implemented in RedisExecutionDAO. Please use ExecutionDAOFacade instead.");
	}

	@Override
	public boolean canSearchAcrossWorkflows() {
		return false;
	}

	/**
	 * Inserts a new workflow/ updates an existing workflow in the datastore.
	 * Additionally, if a workflow is in terminal state, it is removed from the set of pending workflows.
	 *
	 * @param workflow the workflow instance
	 * @param update flag to identify if update or create operation
	 * @return the workflowId
	 */
	private String insertOrUpdateWorkflow(Workflow workflow, boolean update) {
		Preconditions.checkNotNull(workflow, "workflow object cannot be null");

		if (workflow.getStatus().isTerminal()) {
			workflow.setEndTime(System.currentTimeMillis());
		}
		List<Task> tasks = workflow.getTasks();
		workflow.setTasks(new LinkedList<>());

		String payload = toJson(workflow);
		// Store the workflow object
		dynoClient.set(nsKey(WORKFLOW, workflow.getWorkflowId()), payload);
		recordRedisDaoRequests("storeWorkflow", "n/a", workflow.getWorkflowName());
		recordRedisDaoPayloadSize("storeWorkflow", payload.length(), "n/a", workflow.getWorkflowName());
		if (!update) {
			// Add to list of workflows for a workflowdef
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflow.getWorkflowName(), dateStr(workflow.getCreateTime()));
			dynoClient.sadd(key, workflow.getWorkflowId());
			if (workflow.getCorrelationId() != null) {
				// Add to list of workflows for a correlationId
				dynoClient.sadd(nsKey(CORR_ID_TO_WORKFLOWS, workflow.getCorrelationId()), workflow.getWorkflowId());
			}
		}
		// Add or remove from the pending workflows
		if (workflow.getStatus().isTerminal()) {
			dynoClient.srem(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflow.getWorkflowId());
		} else {
			dynoClient.sadd(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflow.getWorkflowId());
		}

		workflow.setTasks(tasks);
		return workflow.getWorkflowId();
	}

	/**
	 * Stores the correlation of a task to the workflow instance in the datastore
     *
	 * @param taskId the taskId to be correlated
	 * @param workflowInstanceId the workflowId to which the tasks belongs to
	 */
	@VisibleForTesting
    void correlateTaskToWorkflowInDS(String taskId, String workflowInstanceId) {
        String workflowToTaskKey = nsKey(WORKFLOW_TO_TASKS, workflowInstanceId);
        dynoClient.sadd(workflowToTaskKey, taskId);
        logger.debug("Task mapped in WORKFLOW_TO_TASKS with workflowToTaskKey: {}, workflowId: {}, taskId: {}",
                workflowToTaskKey, workflowInstanceId, taskId);
    }

	private static String dateStr(Long timeInMs) {
		Date date = new Date(timeInMs);
		return dateStr(date);
	}

	private static String dateStr(Date date) {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
		return format.format(date);
	}

	private static List<String> dateStrBetweenDates(Long startdatems, Long enddatems) {
		List<String> dates = new ArrayList<String>();
		Calendar calendar = new GregorianCalendar();
		Date startdate = new Date(startdatems);
		Date enddate = new Date(enddatems);
		calendar.setTime(startdate);
		while (calendar.getTime().before(enddate) || calendar.getTime().equals(enddate)) {
			Date result = calendar.getTime();
			dates.add(dateStr(result));
			calendar.add(Calendar.DATE, 1);
		}
		return dates;
	}

	public long getPendingWorkflowCount(String workflowName) {
		String key = nsKey(PENDING_WORKFLOWS, workflowName);
		recordRedisDaoRequests("getPendingWorkflowCount");
		return dynoClient.scard(key);
	}

	@Override
	public long getInProgressTaskCount(String taskDefName) {
		String inProgressKey = nsKey(TASKS_IN_PROGRESS_STATUS, taskDefName);
		recordRedisDaoRequests("getInProgressTaskCount");
		return dynoClient.scard(inProgressKey);
	}

	@Override
	public boolean addEventExecution(EventExecution eventExecution) {
		try {
			String key = nsKey(EVENT_EXECUTION, eventExecution.getName(), eventExecution.getEvent(), eventExecution.getMessageId());
			String json = objectMapper.writeValueAsString(eventExecution);
			recordRedisDaoEventRequests("addEventExecution", eventExecution.getEvent());
			recordRedisDaoPayloadSize("addEventExecution", json.length(), eventExecution.getEvent(), "n/a");
            return dynoClient.hsetnx(key, eventExecution.getId(), json) == 1L;
		} catch (Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, "Unable to add event execution for " + eventExecution.getId(), e);
		}
	}

	@Override
	public void updateEventExecution(EventExecution eventExecution) {
		try {

			String key = nsKey(EVENT_EXECUTION, eventExecution.getName(), eventExecution.getEvent(), eventExecution.getMessageId());
			String json = objectMapper.writeValueAsString(eventExecution);
			logger.info("updating event execution {}", key);
			dynoClient.hset(key, eventExecution.getId(), json);
			recordRedisDaoEventRequests("updateEventExecution", eventExecution.getEvent());
			recordRedisDaoPayloadSize("updateEventExecution", json.length(),eventExecution.getEvent(), "n/a");
		} catch (Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, "Unable to update event execution for " + eventExecution.getId(), e);
		}
	}

	@Override
	public void removeEventExecution(EventExecution eventExecution) {
		try {
			String key = nsKey(EVENT_EXECUTION, eventExecution.getName(), eventExecution.getEvent(), eventExecution.getMessageId());
			logger.info("removing event execution {}", key);
			dynoClient.hdel(key, eventExecution.getId());
			recordRedisDaoEventRequests("removeEventExecution", eventExecution.getEvent());
		} catch	(Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, "Unable to remove event execution for " + eventExecution.getId(), e);
		}
	}

	@Override
	public List<EventExecution> getEventExecutions(String eventHandlerName, String eventName, String messageId, int max) {
		try {
			String key = nsKey(EVENT_EXECUTION, eventHandlerName, eventName, messageId);
			logger.info("getting event execution {}", key);
			List<EventExecution> executions = new LinkedList<>();
			for(int i = 0; i < max; i++) {
				String field = messageId + "_" + i;
				String value = dynoClient.hget(key, field);
				if(value == null) {
					break;
				}
				recordRedisDaoEventRequests("getEventExecution", eventHandlerName);
				recordRedisDaoPayloadSize("getEventExecution", value.length(),eventHandlerName, "n/a");
				EventExecution eventExecution = objectMapper.readValue(value, EventExecution.class);
				executions.add(eventExecution);
			}
			return executions;

		} catch (Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, "Unable to get event executions for " + eventHandlerName, e);
		}
	}

	@Override
	public void updateLastPoll(String taskDefName, String domain, String workerId) {
		Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
		PollData pollData = new PollData(taskDefName, domain, workerId, System.currentTimeMillis());

		String key = nsKey(POLL_DATA, pollData.getQueueName());
		String field = (domain == null)?"DEFAULT":domain;

		String payload = toJson(pollData);
		recordRedisDaoRequests("updatePollData");
		recordRedisDaoPayloadSize("updatePollData", payload.length(),"n/a","n/a");
		dynoClient.hset(key, field, payload);
	}

	@Override
	public PollData getPollData(String taskDefName, String domain) {
		Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

		String key = nsKey(POLL_DATA, taskDefName);
		String field = (domain == null)?"DEFAULT":domain;

		String pollDataJsonString = dynoClient.hget(key, field);
		recordRedisDaoRequests("getPollData");
		recordRedisDaoPayloadSize("getPollData", StringUtils.length(pollDataJsonString), "n/a", "n/a");

		PollData pollData = null;
		if (pollDataJsonString != null) {
			pollData = readValue(pollDataJsonString, PollData.class);
		}
		return pollData;
	}

	@Override
	public List<PollData> getPollData(String taskDefName) {
		Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

		String key = nsKey(POLL_DATA, taskDefName);

		Map<String, String> pMapdata = dynoClient.hgetAll(key);
		List<PollData> pollData = new ArrayList<PollData>();
		if(pMapdata != null){
			pMapdata.values().forEach(pollDataJsonString -> {
				pollData.add(readValue(pollDataJsonString, PollData.class));
				recordRedisDaoRequests("getPollData");
				recordRedisDaoPayloadSize("getPollData", pollDataJsonString.length(), "n/a", "n/a");
			});
		}
		return pollData;
	}

    /**
     *
     * @param task
     * @throws ApplicationException
     */
	private void validate(Task task) {
	    try {
            Preconditions.checkNotNull(task, "task object cannot be null");
            Preconditions.checkNotNull(task.getTaskId(), "Task id cannot be null");
            Preconditions.checkNotNull(task.getWorkflowInstanceId(), "Workflow instance id cannot be null");
            Preconditions.checkNotNull(task.getReferenceTaskName(), "Task reference name cannot be null");
        } catch (NullPointerException npe){
	        throw new ApplicationException(Code.INVALID_INPUT, npe.getMessage(), npe);
        }
    }
}
