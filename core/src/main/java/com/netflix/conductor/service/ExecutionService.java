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
package com.netflix.conductor.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

/**
 * 
 * @author visingh
 * @author Viren
 *
 */
@Singleton
@Trace
public class ExecutionService {

	private static final Logger logger = LoggerFactory.getLogger(ExecutionService.class);

	private WorkflowExecutor executor;

	private ExecutionDAO edao;
	
	private IndexDAO indexer;

	private QueueDAO queue;

	private MetadataDAO metadata;
	
	private int taskRequeueTimeout;
	

	@Inject
	public ExecutionService(WorkflowExecutor wfProvider, ExecutionDAO edao, QueueDAO queue, MetadataDAO metadata, IndexDAO indexer, Configuration config) {
		this.executor = wfProvider;
		this.edao = edao;
		this.queue = queue;
		this.metadata = metadata;
		this.indexer = indexer;
		this.taskRequeueTimeout = config.getIntProperty("task.requeue.timeout", 60_000);
	}

	public Task poll(String taskType, String workerId) throws Exception {
		
		List<Task> tasks = poll(taskType, workerId, 1, 100);
		if(tasks.isEmpty()) { 
			return null;
		}
		return tasks.get(0);
	}
	
	public List<Task> poll(String taskType, String workerId, int count, int timeoutInMilliSecond) throws Exception {

		List<String> taskIds = queue.pop(taskType, count, timeoutInMilliSecond);
		List<Task> tasks = new LinkedList<>();
		for(String taskId : taskIds) {
			Task task = getTask(taskId);
			if(task == null) {
				continue;
			}

			if (!taskType.equals(task.getTaskType())) {
				// Try and remove it from the queue and add it back in -- in
				// hopes it wont be inserted into the wrong queue again.
				removeTaskfromQueue(task.getTaskType(), task.getTaskId());
				executor.addTaskToQueue(task);
				logger.error("Queue name '{}' did not match type of task retrieved '{}' for task id '{}'.", new Object[]{taskType, task.getTaskType(), task.getTaskId()});
				return Collections.emptyList();
			}

			task.setStatus(Status.IN_PROGRESS);
			if (task.getStartTime() == 0) {
				task.setStartTime(System.currentTimeMillis());
				Monitors.recordQueueWaitTime(task.getTaskDefName(), task.getQueueWaitTime());
			}
			task.setWorkerId(workerId);
			task.setPollCount(task.getPollCount() + 1);
			
			edao.updateTask(task);
			tasks.add(task);
		}
		Monitors.recordTaskPoll(taskType);
		return tasks;
	}

	//For backward compatibility - to be removed in the later versions
	public void updateTask(Task task) throws Exception {
		updateTask(new TaskResult(task));
	}
	
	public void updateTask(TaskResult task) throws Exception {
		executor.updateTask(task);
	}

	public List<Task> getTasks(String taskType, String startKey, int count) throws Exception {
		return executor.getTasks(taskType, startKey, count);
	}

	public Task getTask(String taskId) throws Exception {
		return edao.getTask(taskId);
	}

	public Task getPendingTaskForWorkflow(String taskReferenceName, String workflowId) {
		return executor.getPendingTaskByWorkflow(taskReferenceName, workflowId);
	}

	public boolean ackTaskRecieved(String taskId, String consumerId) throws Exception {
		Task task = getTask(taskId);
		if (task != null) {
			if(task.getResponseTimeoutSeconds() > 0) {
				logger.debug("Adding task " + task.getTaskType() + "/" + taskId + " to be requeued if no response received " + task.getResponseTimeoutSeconds());
				return queue.setUnackTimeout(task.getTaskType(), task.getTaskId(), 1000 * task.getResponseTimeoutSeconds());		//Value is in millisecond
			}else {
				return queue.ack(task.getTaskType(), taskId);
			}
		}
		return false;

	}

	public Map<String, Integer> getTaskQueueSizes(List<String> taskDefNames) {
		Map<String, Integer> sizes = new HashMap<String, Integer>();
		for (String taskDefName : taskDefNames) {
			sizes.put(taskDefName, queue.getSize(taskDefName));
		}
		return sizes;
	}

	public void removeTaskfromQueue(String taskType, String taskId) {
		queue.remove(taskType, taskId);
	}

	public int requeuePendingTasks() throws Exception {
		long threshold = System.currentTimeMillis() - taskRequeueTimeout;
		List<WorkflowDef> workflowDefs = metadata.getAll();
		int count = 0;
		for (WorkflowDef workflowDef : workflowDefs) {
			List<Workflow> workflows = executor.getRunningWorkflows(workflowDef.getName());
			for (Workflow workflow : workflows) {
				count += requeuePendingTasks(workflow, threshold);
			}
		}
		return count;
	}

	public int requeuePendingTasks(Workflow workflow, long threshold) {

		int count = 0;
		List<Task> tasks = workflow.getTasks();
		for (Task pending : tasks) {
			if (SystemTaskType.is(pending.getTaskType())) {
				continue;
			}
			if (pending.getStatus().isTerminal()) {
				continue;
			}
			if (pending.getUpdateTime() < threshold) {
				logger.info("Requeuing Task: workflowId=" + workflow.getWorkflowId() + ", taskType=" + pending.getTaskType() + ", taskId="
						+ pending.getTaskId());
				long callback = pending.getCallbackAfterSeconds();
				if (callback < 0) {
					callback = 0;
				}
				boolean pushed = queue.pushIfNotExists(pending.getTaskType(), pending.getTaskId(), callback);
				if (pushed) {
					count++;
				}
			}
		}
		return count;
	}
	
	public int requeuePendingTasks(String taskType) throws Exception {

		int count = 0;
		List<Task> tasks = getPendingTasksForTaskType(taskType);

		for (Task pending : tasks) {
			
			if (SystemTaskType.is(pending.getTaskType())) {
				continue;
			}
			if (pending.getStatus().isTerminal()) {
				continue;
			}

			logger.info("Requeuing Task: workflowId=" + pending.getWorkflowInstanceId() + ", taskType=" + pending.getTaskType() + ", taskId=" + pending.getTaskId());
			boolean pushed = requeue(pending);
			if (pushed) {
				count++;
			}

		}
		return count;
	}
	
	private boolean requeue(Task pending) throws Exception {
		long callback = pending.getCallbackAfterSeconds();
		if (callback < 0) {
			callback = 0;
		}
		queue.remove(pending.getTaskType(), pending.getTaskId());
		long now = System.currentTimeMillis();
		callback = callback - ((now - pending.getUpdateTime())/1000);
		if(callback < 0) {
			callback = 0;
		}
		return queue.pushIfNotExists(pending.getTaskType(), pending.getTaskId(), callback);
	}

	public List<Workflow> getWorkflowInstances(String workflowName, String correlationId, boolean includeClosed, boolean includeTasks)
			throws Exception {
		List<Workflow> workflows = executor.getStatusByCorrelationId(workflowName, correlationId, includeClosed);
		if (includeTasks) {
			workflows.forEach(wf -> {
				List<Task> tasks;
				try {
					tasks = edao.getTasksForWorkflow(wf.getWorkflowId());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				wf.setTasks(tasks);
			});
		}
		return workflows;
	}

	public Workflow getExecutionStatus(String workflowId, boolean includeTasks) throws Exception {
		return edao.getWorkflow(workflowId, includeTasks);
	}

	public List<String> getRunningWorkflows(String workflowName) {
		return edao.getRunningWorkflowIds(workflowName);
	}

	public void removeWorkflow(String workflowId) throws Exception {
		edao.removeWorkflow(workflowId);
	}

	public SearchResult<WorkflowSummary> search(String query, String freeText, int start, int size, List<String> sortOptions) {
		
		SearchResult<String> result = indexer.searchWorkflows(query, freeText, start, size, sortOptions);
		List<WorkflowSummary> workflows = result.getResults().stream().parallel().map(workflowId -> {
			try {
				
				WorkflowSummary summary = new WorkflowSummary(edao.getWorkflow(workflowId, false));
				return summary;
				
			} catch(Exception e) {
				logger.error(e.getMessage(), e);
				return null;
			}
		}).filter(summary -> summary != null).collect(Collectors.toList());
		int missing = result.getResults().size() - workflows.size();
		long totalHits = result.getTotalHits() - missing;
		SearchResult<WorkflowSummary> sr = new SearchResult<>(totalHits, workflows);
		
		return sr;
	}

	public List<Task> getPendingTasksForTaskType(String taskType) throws Exception {
		return edao.getPendingTasksForTaskType(taskType);
	}
	
	public boolean addEventExecution(EventExecution ee) {
		return edao.addEventExecution(ee);
	}
	
	public void updateEventExecution(EventExecution ee) {
		edao.updateEventExecution(ee);
	}

	public void addMessage(String name, Message msg) {	
		edao.addMessage(name, msg);
	}

}
