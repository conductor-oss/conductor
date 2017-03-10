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
package com.netflix.conductor.dao.dynomite;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;

@Singleton
@Trace
public class RedisExecutionDAO extends BaseDynoDAO implements ExecutionDAO {

	// Keys Families
	private final static String IN_PROGRESS_TASKS = "IN_PROGRESS_TASKS";
	private final static String WORKFLOW_TO_TASKS = "WORKFLOW_TO_TASKS";
	private final static String SCHEDULED_TASKS = "SCHEDULED_TASKS";
	private final static String TASK = "TASK";

	private final static String WORKFLOW = "WORKFLOW";
	private final static String PENDING_WORKFLOWS = "PENDING_WORKFLOWS";
	private final static String WORKFLOW_DEF_TO_WORKFLOWS = "WORKFLOW_DEF_TO_WORKFLOWS";
	private final static String CORR_ID_TO_WORKFLOWS = "CORR_ID_TO_WORKFLOWS";
	
	private final static String EVENT_EXECUTION = "EVENT_EXECUTION";

	private IndexDAO indexer;

	@Inject
	public RedisExecutionDAO(DynoProxy dynoClient, ObjectMapper om, IndexDAO indexer, Configuration config) {
		super(dynoClient, om, config);
		this.indexer = indexer;
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
		boolean startKeyFound = (startKey == null) ? true : false;
		int foundcount = 0;
		for (int i = 0; i < pendingTasks.size(); i++) {
			if (!startKeyFound) {
				if (pendingTasks.get(i).getTaskId().equals(startKey)) {
					startKeyFound = true;
					if (startKey != null) {
						continue;
					}
				}
			}
			if (startKeyFound && foundcount < count) {
				tasks.add(pendingTasks.get(i));
				foundcount++;
			}
		}
		return tasks;
	}

	@Override
	public List<Task> createTasks(List<Task> tasks) {

		List<Task> created = new LinkedList<Task>();

		for (Task task : tasks) {

			Preconditions.checkNotNull(task, "task object cannot be null");
			Preconditions.checkNotNull(task.getTaskId(), "Task id cannot be null");
			Preconditions.checkNotNull(task.getWorkflowInstanceId(), "Workflow instance id cannot be null");
			Preconditions.checkNotNull(task.getReferenceTaskName(), "Task reference name cannot be null");

			task.setScheduledTime(System.currentTimeMillis());

			String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();
			Long added = dynoClient.hset(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey, task.getTaskId());
			if (added < 1) {
				logger.debug(
						"Task already scheduled, skipping the run " + task.getTaskId() + ", ref=" + task.getReferenceTaskName() + ", key=" + taskKey);
				continue;
			}

			dynoClient.sadd(nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()), task.getTaskId());
			dynoClient.sadd(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
			updateTask(task);
			created.add(task);
		}

		return created;

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
		if (task.getStatus() != null && task.getStatus().isTerminal()) {
			task.setEndTime(System.currentTimeMillis());
		}

		dynoClient.set(nsKey(TASK, task.getTaskId()), toJson(task));
		if (task.getStatus() != null && task.getStatus().isTerminal()) {
			dynoClient.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId()); 
		}
		
		indexer.index(task);
	}

	@Override
	public void addTaskExecLog(TaskExecLog log) {
		indexer.add(log);		
	}
	
	@Override
	public void removeTask(String taskId) {

		Task task = getTask(taskId);
		String taskKey = task.getReferenceTaskName() + "" + task.getRetryCount();

		dynoClient.hdel(nsKey(SCHEDULED_TASKS, task.getWorkflowInstanceId()), taskKey);
		dynoClient.srem(nsKey(IN_PROGRESS_TASKS, task.getTaskDefName()), task.getTaskId());
		dynoClient.srem(nsKey(WORKFLOW_TO_TASKS, task.getWorkflowInstanceId()), task.getTaskId());
		dynoClient.del(nsKey(TASK, task.getTaskId()));

	}

	@Override
	public Task getTask(String taskId) {
		Preconditions.checkNotNull(taskId, "taskId name cannot be null");
		Task task = null;


		String taskJsonStr = dynoClient.get(nsKey(TASK, taskId));
		if (taskJsonStr != null) {
			task = readValue(taskJsonStr, Task.class);
		}

	
		return task;
	}

	@Override
	public List<Task> getTasks(List<String> taskIds) {
		List<Task> tasks = new LinkedList<Task>();

		List<String> nsKeys = new ArrayList<String>();
		taskIds.forEach(taskId -> nsKeys.add(nsKey(TASK, taskId)));
		for (String key : nsKeys) {
			String json = dynoClient.get(key);
			if (json != null) {
				tasks.add(readValue(json, Task.class));
			}
		}
		return tasks;

	}

	@Override
	public List<Task> getTasksForWorkflow(String workflowId) {
		Preconditions.checkNotNull(workflowId, "workflowId cannot be null");
		List<Task> tasks = new LinkedList<Task>();
		Set<String> taskIds = dynoClient.smembers(nsKey(WORKFLOW_TO_TASKS, workflowId));
		tasks = getTasks(new ArrayList<String>(taskIds));
		return tasks;
	}

	@Override
	public List<Task> getPendingTasksForTaskType(String taskName) {
		Preconditions.checkNotNull(taskName, "task name cannot be null");
		List<Task> tasks = new LinkedList<Task>();
		Set<String> taskIds = dynoClient.smembers(nsKey(IN_PROGRESS_TASKS, taskName));
		tasks = getTasks(new ArrayList<String>(taskIds));
		return tasks;
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

		
		Workflow wf = getWorkflow(workflowId, false);
		// Remove from lists
		String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, wf.getWorkflowType(), dateStr(wf.getCreateTime()));
		dynoClient.srem(key, workflowId);
		dynoClient.srem(nsKey(CORR_ID_TO_WORKFLOWS, wf.getCorrelationId()), workflowId);
		dynoClient.srem(nsKey(PENDING_WORKFLOWS, wf.getWorkflowType()), workflowId);

		// Remove the object
		dynoClient.del(nsKey(WORKFLOW, workflowId));
		for(Task task : wf.getTasks()) {
			removeTask(task.getTaskId());
		}
		indexer.remove(workflowId);
	
	}
	
	@Override
	public void removeFromPendingWorkflow(String workflowType, String workflowId) {
		dynoClient.srem(nsKey(PENDING_WORKFLOWS, workflowType), workflowId);
	}

	@Override
	public Workflow getWorkflow(String workflowId) {
		return getWorkflow(workflowId, true);
	}

	@Override
	public Workflow getWorkflow(String workflowId, boolean includeTasks) {
		Preconditions.checkNotNull(workflowId, "workflowId name cannot be null");


		String json = dynoClient.get(nsKey(WORKFLOW, workflowId));
		if (json == null) {
			throw new ApplicationException(Code.NOT_FOUND, "No such workflow found by id: " + workflowId);
		}
		Workflow workflow = readValue(json, Workflow.class);
		if (includeTasks) {
			List<Task> tasks = getTasksForWorkflow(workflowId);
			tasks.sort(Comparator.comparingLong(Task::getScheduledTime).thenComparingInt(Task::getSeq));
			workflow.setTasks(tasks);
		}
		return workflow;

	
	}

	@Override
	public List<String> getRunningWorkflowIds(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<String> wfIds = new LinkedList<String>();
		Set<String> pendingWfs = dynoClient.smembers(nsKey(PENDING_WORKFLOWS, workflowName));
		wfIds = new LinkedList<String>(pendingWfs);
		return wfIds;
	}

	@Override
	public List<Workflow> getPendingWorkflowsByType(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<Workflow> workflows = new LinkedList<Workflow>();
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

		List<Workflow> workflows = new LinkedList<Workflow>();

		// Get all date strings between start and end
		List<String> dateStrs = dateStrBetweenDates(startTime, endTime);
		dateStrs.forEach(dateStr -> {
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflowName, dateStr);
			dynoClient.smembers(key).forEach(wfId -> {
				
				try {
					
					Workflow wf = getWorkflow(wfId);
					if (wf.getCreateTime().longValue() >= startTime.longValue() && wf.getCreateTime().longValue() <= endTime.longValue()) {
						workflows.add(wf);
					}
					
				}catch(Exception e) {
					logger.error(e.getMessage(), e);
				}
			});
		});

	
		return workflows;
	}

	@Override
	public List<Workflow> getWorkflowsByCorrelationId(String correlationId) {
		
		Preconditions.checkNotNull(correlationId, "correlationId cannot be null");
		List<Workflow> workflows = new LinkedList<Workflow>();

		Set<String> workflowIds = dynoClient.smembers(nsKey(CORR_ID_TO_WORKFLOWS, correlationId));
		for(String wfId : workflowIds) {
			workflows.add(getWorkflow(wfId));
		}
	
		return workflows;
	}

	private String insertOrUpdateWorkflow(Workflow workflow, boolean update) {
		Preconditions.checkNotNull(workflow, "workflow object cannot be null");

		if (workflow.getStatus().isTerminal()) {
			workflow.setEndTime(System.currentTimeMillis());
		}
		List<Task> tasks = workflow.getTasks();
		workflow.setTasks(new LinkedList<>());

		// Store the workflow object
		dynoClient.set(nsKey(WORKFLOW, workflow.getWorkflowId()), toJson(workflow));
		if (!update) {
			// Add to list of workflows for a workflowdef
			String key = nsKey(WORKFLOW_DEF_TO_WORKFLOWS, workflow.getWorkflowType(), dateStr(workflow.getCreateTime()));
			dynoClient.sadd(key, workflow.getWorkflowId());
			if (workflow.getCorrelationId() != null) {
				// Add to list of workflows for a correlationId
				dynoClient.sadd(nsKey(CORR_ID_TO_WORKFLOWS, workflow.getCorrelationId()), workflow.getWorkflowId());
			}
		}
		// Add or remove from the pending workflows
		if (workflow.getStatus().isTerminal()) {
			dynoClient.srem(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowType()), workflow.getWorkflowId());
		} else {
			dynoClient.sadd(nsKey(PENDING_WORKFLOWS, workflow.getWorkflowType()), workflow.getWorkflowId());
		}

		workflow.setTasks(tasks);
		indexer.index(workflow);

		return workflow.getWorkflowId();

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
		return dynoClient.scard(key);
	}


	@Override
	public boolean addEventExecution(EventExecution ee) {
		try {
			
			String key = nsKey(EVENT_EXECUTION, ee.getName(), ee.getEvent(), ee.getMessageId());
			String json = om.writeValueAsString(ee);
			logger.info("adding event execution {}", key);
			if(dynoClient.hsetnx(key, ee.getId(), json) == 1L) {
				indexer.add(ee);
				return true;
			}
			return false;
			
		} catch (Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public void updateEventExecution(EventExecution ee) {
		try {
			
			String key = nsKey(EVENT_EXECUTION, ee.getName(), ee.getEvent(), ee.getMessageId());
			String json = om.writeValueAsString(ee);
			logger.info("updating event execution {}", key);
			dynoClient.hset(key, ee.getId(), json);
			indexer.add(ee);
			
		} catch (Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, e.getMessage(), e);
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
				EventExecution ee = om.readValue(value, EventExecution.class);
				executions.add(ee);	
				
			}
			return executions;
			
		} catch (Exception e) {
			throw new ApplicationException(Code.BACKEND_ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public void addMessage(String queue, Message msg) {
		indexer.addMessage(queue, msg);		
	}
	
}
