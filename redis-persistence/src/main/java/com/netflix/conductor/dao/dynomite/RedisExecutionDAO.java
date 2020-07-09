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
	private final static String IN_PROGRESS_TASKS = "IN_PROGRESS_TASKS";
	private final static String TASKS_IN_PROGRESS_STATUS = "TASKS_IN_PROGRESS_STATUS";	//Tasks which are in IN_PROGRESS status.
	private final static String WORKFLOW_TO_TASKS = "WORKFLOW_TO_TASKS";
	private final static String SCHEDULED_TASKS = "SCHEDULED_TASKS";
	private final static String TASK = "TASK";
	private final static String WORKFLOW = "WORKFLOW";
	private final static String PENDING_WORKFLOWS = "PENDING_WORKFLOWS";
	private final static String WORKFLOW_DEF_TO_WORKFLOWS = "WORKFLOW_DEF_TO_WORKFLOWS";
	private final static String CORR_ID_TO_WORKFLOWS = "CORR_ID_TO_WORKFLOWS";

	private final int ttlEventExecutionSeconds;

	private final static String EVENT_EXECUTION = "EVENT_EXECUTION";

	@Inject
	public RedisExecutionDAO(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
		super(dynoClient, objectMapper, config);

		ttlEventExecutionSeconds = config.getEventExecutionPersistenceTTL();
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

			String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();
			Long added = dynoClient.hset(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey, task.getTaskId());
			if (added < 1) {
				logger.debug("Task already scheduled, skipping the run " + task.getTaskId() + ", ref=" + task.getReferenceTaskName() + ", key=" + taskKey);
				continue;
			}

			if(task.getStatus() != null && !task.getStatus().isTerminal() && task.getScheduledTime() == 0){
				task.setScheduledTime(System.currentTimeMillis());
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
	public void updateTask(Task task) {
		Optional<TaskDef> taskDefinition = task.getTaskDefinition();

		if(taskDefinition.isPresent() && taskDefinition.get().concurrencyLimit() > 0) {

			if(task.getStatus() != null && task.getStatus().equals(Status.IN_PROGRESS)) {
				dynoClient.sadd(nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
				logger.debug("Workflow Task added to TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
						nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName(), task.getTaskId()), task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType(), task.getStatus().name());
			}else {
				dynoClient.srem(nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
				logger.debug("Workflow Task removed from TASKS_IN_PROGRESS_STATUS with tasksInProgressKey: {}, workflowId: {}, taskId: {}, taskType: {}, taskStatus: {} during updateTask",
						nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName(), task.getTaskId()), task.getWorkflowInstanceId(), task.getTaskId(), task.getTaskType(), task.getStatus().name());
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

	private void removeTaskMappings(Task task)
	{
		String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();

		dynoClient.hdel(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey);
		dynoClient.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
		dynoClient.srem(nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()), task.getTaskId());
		dynoClient.srem(nsKey(TASKS_IN_PROGRESS_STATUS, task.getTaskDefName()), task.getTaskId());
		dynoClient.zrem(nsKey(TASK_LIMIT_BUCKET, task.getTaskDefName()), task.getTaskId());
	}

	@Override
	public boolean removeTask(String taskId) {
		Task task = getTask(taskId);
		if(task == null) {
			logger.warn("No such task found by id {}", taskId);
			return false;
		}
		removeTaskMappings(task);

		dynoClient.del(nsKey(TASK, task.getTaskId()));
		recordRedisDaoRequests("removeTask", task.getTaskType(), task.getWorkflowType());
		return true;
	}

	private boolean removeTaskWithExpiry(String taskId, int ttlSeconds) {
		Task task = getTask(taskId);
		if(task == null) {
			logger.warn("No such task found by id {}", taskId);
			return false;
		}
		removeTaskMappings(task);

		dynoClient.expire(nsKey(TASK, task.getTaskId()), ttlSeconds);
		recordRedisDaoRequests("removeTask", task.getTaskType(), task.getWorkflowType());
		return true;
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
		return insertOrUpdateWorkflow(workflow, false);
	}

	@Override
	public String updateWorkflow(Workflow workflow) {
		return insertOrUpdateWorkflow(workflow, true);
	}

	@Override
	public boolean removeWorkflow(String workflowId) {
		Workflow workflow = getWorkflow(workflowId, true);
		if (workflow != null) {
			recordRedisDaoRequests("removeWorkflow");

			// Remove from lists
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflow.getWorkflowName(), dateStr(workflow.getCreateTime()));
			dynoClient.srem(key, workflowId);
			dynoClient.srem(nsKey(CORR_ID_TO_WORKFLOWS, workflow.getCorrelationId()), workflowId);
			dynoClient.srem(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflowId);

			// Remove the object
			dynoClient.del(nsKey(WORKFLOW, workflowId));
			for (Task task : workflow.getTasks()) {
				removeTask(task.getTaskId());
			}
			return true;
		}
		return false;
	}

	public boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds)
	{
		Workflow workflow = getWorkflow(workflowId, true);
		if (workflow != null) {
			recordRedisDaoRequests("removeWorkflow");

			// Remove from lists
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflow.getWorkflowName(), dateStr(workflow.getCreateTime()));
			dynoClient.srem(key, workflowId);
			dynoClient.srem(nsKey(CORR_ID_TO_WORKFLOWS, workflow.getCorrelationId()), workflowId);
			dynoClient.srem(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowName()), workflowId);

			// Remove the object
			dynoClient.expire(nsKey(WORKFLOW, workflowId), ttlSeconds);
			for (Task task : workflow.getTasks()) {
				removeTaskWithExpiry(task.getTaskId(), ttlSeconds);
			}
			return true;
		}
		return false;
	}


	@Override
	public void removeFromPendingWorkflow(String workflowType, String workflowId) {
		recordRedisDaoRequests("removePendingWorkflow");
		dynoClient.del(nsKey(SCHEDULED_TASKS, workflowId));
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

	/**
	 * @param workflowName name of the workflow
	 * @param version the workflow version
	 * @return list of workflow ids that are in RUNNING state
	 * <em>returns workflows of all versions for the given workflow name</em>
	 */
	@Override
	public List<String> getRunningWorkflowIds(String workflowName, int version) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<String> workflowIds;
		recordRedisDaoRequests("getRunningWorkflowsByName");
		Set<String> pendingWorkflows = dynoClient.smembers(nsKey(PENDING_WORKFLOWS, workflowName));
		workflowIds = new LinkedList<>(pendingWorkflows);
		return workflowIds;
	}

	/**
	 * @param workflowName name of the workflow
	 * @param version the workflow version
	 * @return list of workflows that are in RUNNING state
	 */
	@Override
	public List<Workflow> getPendingWorkflowsByType(String workflowName, int version) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<String> workflowIds = getRunningWorkflowIds(workflowName, version);
		return workflowIds.stream()
				.map(this::getWorkflow)
				.filter(workflow -> workflow.getWorkflowVersion() == version)
				.collect(Collectors.toList());
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
	public List<Workflow> getWorkflowsByCorrelationId(String workflowName, String correlationId, boolean includeTasks) {
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
            boolean added =  dynoClient.hsetnx(key, eventExecution.getId(), json) == 1L;

			if (ttlEventExecutionSeconds > 0) {
				dynoClient.expire(key, ttlEventExecutionSeconds);
			}

            return added;
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
