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
package com.netflix.conductor.core.execution;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTask;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;

/**
 * @author Viren
 * @author Vikram
 *
 */
public class DeciderService {

	private static Logger logger = LoggerFactory.getLogger(DeciderService.class);
	
	private static final TypeReference<List<WorkflowTask>> ListOfWorkflowTasks = new TypeReference<List<WorkflowTask>>() {};
	
	private MetadataDAO metadata;
	
	private ExecutionDAO edao;
	
	private ObjectMapper om;
	
	private ParametersUtils pu = new ParametersUtils();
	
	@Inject
	public DeciderService(MetadataDAO metadata, ExecutionDAO edao, ObjectMapper om) {
		this.metadata = metadata;
		this.edao = edao;
		this.om = om;
	}


	@VisibleForTesting
	public DeciderService() {
	}
	
	@VisibleForTesting
	void setMetadata(MetadataDAO metadata) {
		this.metadata = metadata;
	}
	
	List<Task> startWorkflow(Workflow workflow, WorkflowDef def) throws Exception {

		logger.debug("Starting workflow " + def.getName() + "/" + workflow.getWorkflowId());
		
		List<Task> tasks = workflow.getTasks();
		// Check if the workflow is a re-run case
		if (workflow.getReRunFromWorkflowId() == null || tasks.isEmpty()) {
			if(def.getTasks().isEmpty()) {
				//There are no tasks in a workflow
				workflow.setStatus(WorkflowStatus.COMPLETED);
				return Collections.emptyList();
			}
			WorkflowTask taskToSchedule = def.getTasks().getFirst();		//Nothing is running yet - so schedule the first task
			while(isTaskSkipped(taskToSchedule, workflow)){
				taskToSchedule = def.getNextTask(taskToSchedule.getTaskReferenceName());
			}
			List<Task> toBeScheduled = getTasksToBeScheduled(def, workflow, taskToSchedule, 0, workflow.getStartTime());
			return toBeScheduled;
		} 

		// Get the first task to schedule
		Task rerunFromTask = null;
		for(Task t: tasks){
			if(t.getStatus().equals(Status.READY_FOR_RERUN)){
				rerunFromTask = t;
				break;
			}
		}
		if(rerunFromTask == null){
			String reason = String.format("The workflow %s is marked for re-run from %s but could not find the starting task", workflow.getWorkflowId(), workflow.getReRunFromWorkflowId());
			throw new TerminateWorkflow(reason);
		}
		rerunFromTask.setStatus(Status.SCHEDULED);
		rerunFromTask.setRetried(true);
		rerunFromTask.setRetryCount(0);		
		return Arrays.asList(rerunFromTask);
	
	}

	/**
	 * 
	 * @param workflowId id of the workflow
	 * @param executor Workflow executor
	 * @return true if the workflow status was terminal.  false otherwise.
	 * @throws Exception if there is an internal error
	 */
	public boolean decide(String workflowId, WorkflowExecutor executor) throws Exception {
		
		final Workflow workflow = edao.getWorkflow(workflowId, true);
		final WorkflowDef def = metadata.get(workflow.getWorkflowType(), workflow.getVersion());
		workflow.setSchemaVersion(def.getSchemaVersion());
		try {
			
			final List<Task> tasks = workflow.getTasks();
			List<Task> executedTasks = tasks.stream().filter(t -> !t.getStatus().equals(Status.SKIPPED) && !t.getStatus().equals(Status.READY_FOR_RERUN)).collect(Collectors.toList());		
			List<Task> tasksToBeScheduled = new LinkedList<>();
			//If the task list is empty, then 
			if(executedTasks.isEmpty()){
				tasksToBeScheduled = startWorkflow(workflow, def);
				if(workflow.getStatus().isTerminal()) {
					edao.updateWorkflow(workflow);
				}
				if(tasksToBeScheduled == null) tasksToBeScheduled = new LinkedList<>();
			}
			decide(def, workflow, tasksToBeScheduled, executor);
			
			if(workflow.getStatus().isTerminal()) {
				return true;
			}
			
			return false;
			
		} catch (TerminateWorkflow tw) {
			terminate(def, workflow, tw, executor);
			return true;
		}
		
	}
	
	void decide(final WorkflowDef def, final Workflow workflow, List<Task> preScheduledTasks, WorkflowExecutor workflowProvider) throws Exception {
		
		if (workflow.getStatus().equals(WorkflowStatus.PAUSED)) {
			logger.debug("Workflow " + workflow.getWorkflowId() + " is paused");
			return;
		}
		
		if (workflow.getStatus().isTerminal()) {
			// you cannot evaluate a terminal workflow
			logger.debug("Workflow " + workflow.getWorkflowId() + " is already finished.  status=" + workflow.getStatus() + ", reason=" + workflow.getReasonForIncompletion());
			workflowProvider.cleanupFromPending(workflow);
			return;
		}
		
		List<Task> pendingTasks = workflow.getTasks().stream().filter(t -> (!t.isRetried() && !t.getStatus().equals(Status.SKIPPED)) || SystemTaskType.isBuiltIn(t.getTaskType())).collect(Collectors.toList());
		boolean reeval = false;
		
		Set<String> executedTaskRefNames = workflow.getTasks().stream()
				.filter(t -> !t.getStatus().equals(Status.SKIPPED) && !t.getStatus().equals(Status.READY_FOR_RERUN))
				.map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
		
		List<Task> systemTasksExecuted = new LinkedList<Task>();
		Map<String, Task> tasksToBeScheduled = new LinkedHashMap<>();
		
		preScheduledTasks.forEach(pst -> {
				executedTaskRefNames.remove(pst.getReferenceTaskName());
				tasksToBeScheduled.put(pst.getReferenceTaskName(), pst);					
				if(SystemTaskType.is(pst.getTaskType())){
					systemTasksExecuted.add(pst);
				}
			});
		
		List<Task> update = new LinkedList<>();
		
		for (Task task : pendingTasks) {
			
			if (SystemTaskType.is(task.getTaskType()) && !task.getStatus().isTerminal()) {
				WorkflowSystemTask stt = WorkflowSystemTask.get(task.getTaskType());
				if (stt.execute(workflow, task, workflowProvider)) {
					update.add(task);
					reeval = true;
					systemTasksExecuted.add(task);
				}
			}
			TaskDef taskDef = metadata.getTaskDef(task.getTaskDefName());
			if(taskDef != null) {
				checkForTimeout(taskDef, task);
			}

			if (!task.getStatus().isSuccessful()) {
				List<Task> retryTasks = shouldTaskRetry(def, taskDef, workflow, task);
				retryTasks.forEach(rt -> {
						tasksToBeScheduled.put(rt.getReferenceTaskName(), rt);
						executedTaskRefNames.remove(rt.getReferenceTaskName());
					});
				workflow.getTasks().addAll(retryTasks);
				update.add(task);
			}

			if (!task.isRetried() && task.getStatus().isTerminal()) {
				task.setRetried(true);
				List<Task> nextTasks = getNextTask(def, workflow, task);
				nextTasks.forEach(rt -> tasksToBeScheduled.put(rt.getReferenceTaskName(), rt));
				update.add(task);
				logger.debug("Scheduling Tasks from " + task.getTaskDefName() + ", next = " + nextTasks.stream().map(t -> t.getTaskDefName()).collect(Collectors.toList()));				
			}
		}
		
		List<Task> unScheduledTasks = tasksToBeScheduled.values().stream().filter(tt -> !executedTaskRefNames.contains(tt.getReferenceTaskName())).collect(Collectors.toList());
		int pushedToQueue = -1;
		if (!unScheduledTasks.isEmpty()) {
			logger.debug("Scheduling Tasks " + unScheduledTasks.stream().map(t -> t.getTaskDefName()).collect(Collectors.toList()));
			pushedToQueue = workflowProvider.scheduleTask(unScheduledTasks);
			workflow.getTasks().addAll(unScheduledTasks);
		}
		
		systemTasksExecuted.forEach(t -> {update.add(t);});
		edao.updateTasks(update);
		
		if(checkForWorkflowCompletion(def, workflow)){
			logger.debug("Marking workflow as complete.  workflow=" + workflow.getWorkflowId() + ", tasks=" + workflow.getTasks());
			workflowProvider.completeWorkflow(workflow);
		}else{
			edao.updateWorkflow(workflow);
		}
		
		if (pushedToQueue == 0 || reeval) {
			//Nothing was pushed to queue - need to re-evaluate workflow
			decide(def, workflow, Collections.emptyList(), workflowProvider);
		}
	
	}

	boolean checkForWorkflowCompletion(final WorkflowDef def, final Workflow workflow) throws Exception {

		List<Task> allTasks = workflow.getTasks();
		if (allTasks.isEmpty()) {
			return false;
		}

		Task last = null;
		Map<String, Object> output = new HashMap<>();
		if (!allTasks.isEmpty()) {
			last = allTasks.get(allTasks.size() - 1);
			output = last.getOutputData();
		}
		if (!def.getOutputParameters().isEmpty()) {
			output = getTaskInput(def.getOutputParameters(), workflow, null);
		}
		workflow.setOutput(output);

		Map<String, Status> taskStatusMap = new HashMap<>();
		workflow.getTasks().forEach(task -> taskStatusMap.put(task.getReferenceTaskName(), task.getStatus()));

		LinkedList<WorkflowTask> wftasks = def.getTasks();
		boolean allCompletedSuccessfully = wftasks.stream().parallel().allMatch(wftask -> {
			Status status = taskStatusMap.get(wftask.getTaskReferenceName());
			return status != null && status.isSuccessful() && status.isTerminal();
		});

		boolean noPendingTasks = taskStatusMap.values().stream().allMatch(st -> st.isTerminal());
		
		boolean noPendingSchedule = workflow.getTasks().stream().parallel().filter(wftask -> {
			String next = getNextTasksToBeScheduled(def, workflow, wftask);
			return next != null && !taskStatusMap.containsKey(next);
		}).collect(Collectors.toList()).isEmpty();
		
		if (allCompletedSuccessfully && noPendingTasks && noPendingSchedule) {
			return true;
		}

		return false;
	}
	
	private void terminate(final WorkflowDef def, final Workflow workflow, TerminateWorkflow tw, WorkflowExecutor workflowProvider) throws Exception {
		
		if (!workflow.getStatus().isTerminal()) {
			workflow.setStatus(tw.workflowStatus);
		}

		String failureWorkflow = def.getFailureWorkflow();
		if (failureWorkflow != null) {
			if (failureWorkflow.startsWith("$")) {
				String[] paramPathComponents = failureWorkflow.split("\\.");
				String name = paramPathComponents[2]; // name of the input parameter
				failureWorkflow = (String) workflow.getInput().get(name);
			}
		}
		if(tw.task != null){
			edao.updateTask(tw.task);
		}
		workflowProvider.terminateWorkflow(workflow, tw.getMessage(), failureWorkflow);
	}
	
	List<Task> getNextTask(WorkflowDef def, Workflow workflow, Task task) {
		
		// Get the following task after the last completed task
		if(SystemTaskType.is(task.getTaskType()) && SystemTaskType.DECISION.name().equals(task.getTaskType())){
			if(task.getInputData().get("hasChildren") != null){
				return Collections.emptyList();
			}
		}
		String taskReferenceName = task.getReferenceTaskName();
		WorkflowTask taskToSchedule = def.getNextTask(taskReferenceName);
		while (isTaskSkipped(taskToSchedule, workflow)) {
			taskToSchedule = def.getNextTask(taskToSchedule.getTaskReferenceName());
		}
		if(taskToSchedule != null){
			return getTasksToBeScheduled(def, workflow, taskToSchedule, 0, task.getEndTime());
		}
		
		return Collections.emptyList();
		
	}
	
	String getNextTasksToBeScheduled(WorkflowDef def, Workflow workflow, Task task) {

		String taskReferenceName = task.getReferenceTaskName();
		WorkflowTask taskToSchedule = def.getNextTask(taskReferenceName);
		while (isTaskSkipped(taskToSchedule, workflow)) {
			taskToSchedule = def.getNextTask(taskToSchedule.getTaskReferenceName());
		}
		return taskToSchedule == null ? null : taskToSchedule.getTaskReferenceName();
		
		
	}
	
	private List<Task> shouldTaskRetry(WorkflowDef def, TaskDef taskDef, Workflow workflow, Task task) throws Exception {

		int retryCount = task.getRetryCount();
		if (!task.getStatus().isRetriable() || SystemTaskType.isBuiltIn(task.getTaskType()) || taskDef.getRetryCount() <= retryCount) {
			WorkflowStatus status = task.getStatus().equals(Status.TIMED_OUT) ? WorkflowStatus.TIMED_OUT : WorkflowStatus.FAILED;
			task.setRetried(true);
			throw new TerminateWorkflow(task.getReasonForIncompletion(), status, task);
		}

		// retry... - but not immediately - put a delay...
		int startDelay = taskDef.getRetryDelaySeconds();
		switch (taskDef.getRetryLogic()) {
			case FIXED:
				startDelay = taskDef.getRetryDelaySeconds();
				break;
			case EXPONENTIAL_BACKOFF:
				startDelay = taskDef.getRetryDelaySeconds() * (1 + task.getRetryCount());
				break;
		}
		task.setRetried(true);
		
		WorkflowTask taskToSchedule = def.getTaskByRefName(task.getReferenceTaskName());
		if(taskToSchedule == null){			
			taskToSchedule = task.getDynamicWorkflowTask();
		}
		if(taskToSchedule == null){
			logger.warn("taskToSchedule is still null...." + task.getWorkflowInstanceId() + "/" + task.getTaskId());
			WorkflowStatus status = task.getStatus().equals(Status.TIMED_OUT) ? WorkflowStatus.TIMED_OUT : WorkflowStatus.FAILED;
			throw new TerminateWorkflow(task.getReasonForIncompletion(), status, task);
		}
		taskToSchedule.setStartDelay(startDelay);
		List<Task> tasksTobeScheduled = getTasksToBeScheduled(def, workflow, taskToSchedule, task.getRetryCount()+1, task.getEndTime(), null, task.getTaskId());
		tasksTobeScheduled.stream().filter(t -> t.getReferenceTaskName().equals(task.getReferenceTaskName())).forEach(tbs -> {
			tbs.setInputData(task.getInputData());
			tbs.setDynamicWorkflowTask(task.getDynamicWorkflowTask());
		});
		return tasksTobeScheduled;
		
	}
	
	private void checkForTimeout(TaskDef taskType, Task task) {
		if(taskType == null){
			logger.warn("missing task type " + task.getTaskDefName() + ", workflowId=" + task.getWorkflowInstanceId());
			return;
		}
		if (task.getStatus().isTerminal() || taskType.getTimeoutSeconds() <= 0 || !task.getStatus().equals(Status.IN_PROGRESS)) {
			return;
		}

		long timeout = 1000 * taskType.getTimeoutSeconds();
		long now = System.currentTimeMillis();
		long elapsedTime = now - (task.getStartTime() + (task.getStartDelayInSeconds()*1000));
		
		if (elapsedTime < timeout) {
			return;
		}

		String reason = "Task timed out after " + elapsedTime + " millisecond.  Timeout configured as " + timeout;
		
		switch (taskType.getTimeoutPolicy()) {
		case ALERT_ONLY:
			Monitors.recordTaskTimeout(task.getTaskDefName());			
			return;
		case RETRY:
			task.setStatus(Status.TIMED_OUT);
			task.setReasonForIncompletion(reason);
			Monitors.recordTaskTimeout(task.getTaskDefName());
			return;
		case TIME_OUT_WF:
			task.setStatus(Status.TIMED_OUT);
			task.setReasonForIncompletion(reason);
			throw new TerminateWorkflow(reason, WorkflowStatus.TIMED_OUT, task);
		}
		
		return;
	
		
	}

	private List<Task> getTasksToBeScheduled(WorkflowDef def, Workflow workflow, WorkflowTask taskToSchedule, int retryCount, long lastEventTime)  {
		return getTasksToBeScheduled(def, workflow, taskToSchedule, retryCount, lastEventTime, null, null);
	}
	
	private List<Task> getTasksToBeScheduled(WorkflowDef def, Workflow workflow, WorkflowTask taskToSchedule, int retryCount, long lastEventTime, String taskId, String retriedTaskId) {

		List<Task> tasks = new LinkedList<>();
		
		Task task = null;
		Map<String, Object> input = getTaskInput(taskToSchedule.getInputParameters(), workflow, null);
		Type tt = Type.USER_DEFINED;
		String type = taskToSchedule.getType();
		if(Type.is(type)) {
			tt = Type.valueOf(type);
		}
		
		switch(tt){
		
			case DECISION:
				String paramName = taskToSchedule.getCaseValueParam();
				String caseValue = "" + input.get(paramName);
				
				Task st = SystemTask.decisionTask(workflow.getWorkflowId(), IDGenerator.generate(), 
						workflow.getCorrelationId(), taskToSchedule.getTaskReferenceName(), input, caseValue, Arrays.asList(caseValue));
				tasks.add(st);
				List<WorkflowTask> selectedTasks = taskToSchedule.getDecisionCases().get(caseValue);
				if(selectedTasks == null || selectedTasks.isEmpty()){
					selectedTasks = taskToSchedule.getDefaultCase();
				}
				if(selectedTasks != null && !selectedTasks.isEmpty()){
					WorkflowTask selectedTask = selectedTasks.get(0);		//Schedule the first task to be executed...
					if(taskId == null) taskId = IDGenerator.generate();
					List<Task> caseTasks = getTasksToBeScheduled(def, workflow, selectedTask, retryCount, lastEventTime, taskId, retriedTaskId);
					tasks.addAll(caseTasks);
					st.getInputData().put("hasChildren", "true");
				}
				break;
				
			case DYNAMIC:
				paramName = taskToSchedule.getDynamicTaskNameParam();
				String taskName = (String) input.get(paramName);
				if(taskName == null){
					//Workflow should be terminated here...
					throw new TerminateWorkflow("Cannot map a dynamic task based on the parameter and input.  Parameter= " + paramName + ", input=" + input);
				}
				taskToSchedule.setName(taskName);
				if(taskId == null) taskId = IDGenerator.generate();
				task = createTaskToSchedule(workflow, taskToSchedule, retryCount, taskId);
				task.setTaskType(taskName);
				task.setRetriedTaskId(retriedTaskId);
				tasks.add(task);
				break;
			case FORK_JOIN:
				// Create Fork Task
				st = SystemTask.forkTask(workflow.getWorkflowId(), IDGenerator.generate(),
						workflow.getCorrelationId(), taskToSchedule.getTaskReferenceName(), input);
				tasks.add(st);
				// Create tasks
				List<List<WorkflowTask>> forkTasks = taskToSchedule.getForkTasks();
				for(List<WorkflowTask> wfts : forkTasks){
					WorkflowTask wft = wfts.get(0);
					List<Task> tasks2 = getTasksToBeScheduled(def, workflow, wft, retryCount, lastEventTime);
					tasks.addAll(tasks2);
				}
				
				WorkflowTask joinWorkflowTask = def.getNextTask(taskToSchedule.getTaskReferenceName());
				if(joinWorkflowTask == null || !joinWorkflowTask.getType().equals(Type.JOIN.name())){
					throw new TerminateWorkflow("Dynamic join definition is not followed by a join task.  Check the blueprint");
				}
				break;
			case JOIN:
				Map<String, Object> joinInput = new HashMap<String, Object>();
				joinInput.put("joinOn", taskToSchedule.getJoinOn());
				Task joinTask = SystemTask.JoinTask(workflow.getWorkflowId(), IDGenerator.generate(), 
						workflow.getCorrelationId(), taskToSchedule.getTaskReferenceName(), joinInput);
				tasks.add(joinTask);
				break;
			case FORK_JOIN_DYNAMIC:
				joinTask = getDynamicTasks(def, workflow, taskToSchedule, retryCount, lastEventTime, tasks);
				tasks.add(joinTask);
				break;
			case USER_DEFINED:
				task = createSystemTask(taskToSchedule, workflow, retryCount);
				tasks.add(task);
				break;				
			case SIMPLE:
				if(taskId == null) taskId = IDGenerator.generate();
				task = createTaskToSchedule(workflow, taskToSchedule, retryCount, taskId);
				task.setRetriedTaskId(retriedTaskId);
				tasks.add(task);
				break;
			case SUB_WORKFLOW:
				SubWorkflowParams subWorkflowParams = taskToSchedule.getSubWorkflowParam();
				if(subWorkflowParams == null){
					throw new TerminateWorkflow("Task " + taskToSchedule.getName() + " is defined as sub-workflow and is missing subWorkflowParams.  Please check the blueprint");
				}
				String name = subWorkflowParams.getName();
				Object version = subWorkflowParams.getVersion();
				Map<String, Object> params = new HashMap<>();
				params.put("name", name);
				if(version != null){
					params.put("version", version.toString());	
				}
				Map<String, Object> resolvedParams = pu.getTaskInputV2(params, workflow, null, null);
				String workflowName = resolvedParams.get("name").toString();
				version = resolvedParams.get("version");
				int workflowVersion;
				if(version == null){
					try {
						workflowVersion = metadata.getLatest(workflowName).getVersion();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}else{
					workflowVersion = Integer.parseInt(version.toString());
				}
				task = SystemTask.subWorkflowTask(
						workflow.getWorkflowId(), IDGenerator.generate(), workflow.getCorrelationId(), taskToSchedule.getTaskReferenceName(), 
						workflowName, workflowVersion, input);
				tasks.add(task);
				break;
			default:
				break;
		}
		return tasks;
	}

	private Task createSystemTask(WorkflowTask taskToSchedule, Workflow workflow, int retryCount)  {
		TaskDef taskDef = metadata.getTaskDef(taskToSchedule.getName());
	    if(taskDef == null){
	    	String reason = "Invalid task specified.  Cannot find task by name " + taskToSchedule.getName() + " in the task definitions";
	    	throw new TerminateWorkflow(reason);
	    }
		String taskId = IDGenerator.generate();
		Map<String, Object> input = pu.getTaskInputV2(taskToSchedule.getInputParameters(), workflow, taskId, taskDef);
		Task task = SystemTask.userDefined(workflow, taskToSchedule, taskDef, retryCount, taskId, input);
		return task;
	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	private Task getDynamicTasks(WorkflowDef def, Workflow workflow, WorkflowTask taskToSchedule, int retryCount, long lastEventTime, List<Task> tasks) {
		
		List<WorkflowTask> dynForkTasks = new LinkedList<>();
		Map<String, Map<String, Object>> tasksInput = new HashMap<>();
		
		String paramName = taskToSchedule.getDynamicForkTasksParam();
		
		if(paramName != null){
			
			Map<String, Object> input = getTaskInput(taskToSchedule.getInputParameters(), workflow, null);
			Object paramValue = input.get(paramName);
			dynForkTasks = om.convertValue(paramValue, ListOfWorkflowTasks);
			Object tasksInputO = input.get(taskToSchedule.getDynamicForkTasksInputParamName());
			if(! (tasksInputO instanceof Map) ){
				throw new TerminateWorkflow("Input to the dynamically forked tasks is not a map -> expecting a map of K,V  but found " + tasksInputO);
			}
			tasksInput = (Map<String, Map<String, Object>>) tasksInputO;
			
		}else {
			paramName = taskToSchedule.getDynamicForkJoinTasksParam();
			Map<String, Object> input = getTaskInput(taskToSchedule.getInputParameters(), workflow, null);
			Object paramValue = input.get(paramName);
			DynamicForkJoinTaskList dynForkTasks0 = om.convertValue(paramValue, DynamicForkJoinTaskList.class);
			for( DynamicForkJoinTask dt : dynForkTasks0.getDynamicTasks()) {
				WorkflowTask wft = new WorkflowTask();
				wft.setTaskReferenceName(dt.getReferenceName());
				wft.setName(dt.getTaskName());
				wft.setType(dt.getType());
				tasksInput.put(dt.getReferenceName(), dt.getInput());
				dynForkTasks.add(wft);
			}
		}
		
		// Create Fork Task
		Task st = SystemTask.forkDynamicTask(workflow.getWorkflowId(), IDGenerator.generate(),
				workflow.getCorrelationId(), taskToSchedule.getTaskReferenceName(), dynForkTasks);

		tasks.add(st);
		List<String> joinOnTaskRefs = new LinkedList<>();
		// Create Dynamic tasks
		for (WorkflowTask wft : dynForkTasks) {
			List<Task> forkedTasks = getTasksToBeScheduled(def, workflow, wft, retryCount, lastEventTime);
			tasks.addAll(forkedTasks);
			Task last = forkedTasks.get(forkedTasks.size()-1);
			joinOnTaskRefs.add(last.getReferenceTaskName());
			for(Task ft : forkedTasks){
				Map<String, Object> forkedTaskInput = tasksInput.get(ft.getReferenceTaskName());
				if( forkedTaskInput != null && (!(forkedTaskInput instanceof Map)) ){
					throw new TerminateWorkflow("Input to the dynamically forked task " + ft.getReferenceTaskName() + " is not a map, this is what I got " + forkedTaskInput);
				}
				ft.setDynamicWorkflowTask(wft);
				ft.getInputData().putAll(forkedTaskInput);
			}
		}
		
		WorkflowTask joinWorkflowTask = def.getNextTask(taskToSchedule.getTaskReferenceName());
		if(joinWorkflowTask == null || !joinWorkflowTask.getType().equals(Type.JOIN.name())){
			throw new TerminateWorkflow("Dynamic join definition is not followed by a join task.  Check the blueprint");
		}
		// Create Join task				
		HashMap<String, Object> joinInput = new HashMap<String, Object>();
		joinInput.put("joinOn", joinOnTaskRefs);
		Task joinTask = SystemTask.JoinTask(workflow.getWorkflowId(), IDGenerator.generate(), 
							workflow.getCorrelationId(), joinWorkflowTask.getTaskReferenceName(), joinInput);
		return joinTask;
	}
	
	private Task createTaskToSchedule(Workflow workflow, WorkflowTask taskToSchedule, int retryCount, String taskId) {
		TaskDef taskDef = metadata.getTaskDef(taskToSchedule.getName());
		
		if (taskDef == null) {
	    	String reason = "Invalid task specified.  Cannot find task by name " + taskToSchedule.getName() + " in the task definitions";
	    	throw new TerminateWorkflow(reason);
	    }
	    
		Task theTask = new Task();
		theTask.setStartDelayInSeconds(taskToSchedule.getStartDelay());
		if(taskId == null){
			taskId = IDGenerator.generate();
		}
	    theTask.setTaskId(taskId);
	    theTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
	    theTask.setInputData(getTaskInput(taskToSchedule.getInputParameters(), workflow, taskDef));
	    theTask.setWorkflowInstanceId(workflow.getWorkflowId());
	    theTask.setStatus(Status.SCHEDULED);
	    theTask.setTaskType(taskToSchedule.getName());
	    theTask.setTaskDefName(taskToSchedule.getName());
	    theTask.setCorrelationId(workflow.getCorrelationId());
	    theTask.setScheduledTime(System.currentTimeMillis());
	    theTask.setRetryCount(retryCount);
	    theTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
	    theTask.setResponseTimeoutSeconds(taskDef.getResponseTimeoutSeconds());
	    
		return theTask;
	}
	
	@VisibleForTesting
	Map<String, Object> getTaskInput(Map<String, Object> inputParams, Workflow workflow, TaskDef taskDef)  {
		if(workflow.getSchemaVersion() > 1){
			return pu.getTaskInputV2(inputParams, workflow, null, taskDef);
		}
		return getTaskInputV1(workflow, inputParams);
	}

	@Deprecated
	//Workflow schema version 1 is deprecated and new workflows should be using version 2
	private Map<String, Object> getTaskInputV1(Workflow workflow, Map<String, Object> inputParams) {
		Map<String, Object> input = new HashMap<>();
		if(inputParams == null){
			return input;
		}
		Map<String, Object> workflowInput = workflow.getInput();
		inputParams.entrySet().forEach(e -> {
			
			String paramName = e.getKey();
			String paramPath = ""+e.getValue();
			String[] paramPathComponents = paramPath.split("\\.");
			Preconditions.checkArgument(paramPathComponents.length == 3, "Invalid input expression for " + paramName + ", paramPathComponents.size=" + paramPathComponents.length + ", expression=" + paramPath);
			
			String source = paramPathComponents[0];	//workflow, or task reference name
			String type = paramPathComponents[1];	//input/output
			String name = paramPathComponents[2];	//name of the parameter
			if("workflow".equals(source)){
				input.put(paramName, workflowInput.get(name));
			}else{
				Task task = workflow.getTaskByRefName(source);
				if(task != null){
					if("input".equals(type)){
						input.put(paramName, task.getInputData().get(name));
					}else{
						input.put(paramName, task.getOutputData().get(name));
					}
				}
			}
		});
		return input;
	}
	
	private boolean isTaskSkipped(WorkflowTask taskToSchedule, Workflow workflow) {
		try {
			boolean retval = false;
			if (taskToSchedule != null) {
				Task t = workflow.getTaskByRefName(taskToSchedule.getTaskReferenceName());
				if (t == null) {
					retval = false;
				} else if (t.getStatus().equals(Status.SKIPPED)) {
					retval = true;
				}
			}
			return retval;
		} catch (Exception e) {
			throw new TerminateWorkflow(e.getMessage());
		}

	}
}
