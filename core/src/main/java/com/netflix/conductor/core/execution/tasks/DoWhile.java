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
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Manan
 *
 */
public class DoWhile extends WorkflowSystemTask {

	public DoWhile() {
		super("DO_WHILE");
	}

	Logger logger = LoggerFactory.getLogger(DecisionTaskMapper.class);
	Map<String, Integer> iterations = new HashMap<>();

	@Override
	@SuppressWarnings("unchecked")
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {
		
		boolean allDone = true;
		boolean hasFailures = false;
		StringBuilder failureReason = new StringBuilder();
		List<String> loopOver = (List<String>) task.getInputData().get("loopOver");

		int iteration = iterations.get(task.getTaskId()) == null ? 0 : iterations.get(task.getTaskId());

		for(String loopOverRef : loopOver){
			String refName = loopOverRef;
			if (iteration > 0) {
				refName += "_" + (iteration);
			}
			Task forkedTask = workflow.getTaskByRefName(refName);
			if(forkedTask == null){
				//Task is not even scheduled yet
				allDone = false;
				break;
			}
			Status taskStatus = forkedTask.getStatus();
			hasFailures = !taskStatus.isSuccessful();
			if(hasFailures){
				failureReason.append(forkedTask.getReasonForIncompletion()).append(" ");
			}
			task.getOutputData().put(loopOverRef, forkedTask.getOutputData());
			allDone = taskStatus.isTerminal();
			if(!allDone || hasFailures){
				break;
			}
		}
		if (hasFailures) {
			return markLoopTaskFailed(task, failureReason.toString());
		} else if (!allDone) {
			return false;
		}
		boolean shouldContinue = getEvaluatedCondition(task, iteration, workflow);
		logger.debug("workflowid {} shouldcontinue {}",workflow.getWorkflowId(), shouldContinue);
		if (shouldContinue){
			return scheduleLoopTasks(task, iterations, workflow, provider);
		} else {
			return markLoopTaskSuccess(task, workflow, iterations);
		}
	}

	Map<String, Object> getConditionInput(Task task, int iteration, Workflow workflow) {
		Map<String, Object> map = new HashMap<>();
		List<String> loopOverTask = task.getWorkflowTask().getLoopOver();
		for (String taskName : loopOverTask) {
			String refName = taskName;
			if (iteration > 0) {
				refName += "_" + (iteration);
			}
			Task temp = workflow.getTaskByRefName(refName);
			map.put(taskName, temp.getOutputData());
			logger.debug("Taskname {} output {}", temp.getReferenceTaskName(), temp.getOutputData());
		}
		return map;
	}

	boolean scheduleLoopTasks(Task task, Map<String, Integer> iterations, Workflow workflow, WorkflowExecutor provider) {
		List<String> loopOver = (List<String>) task.getInputData().get("loopOver");
		List<Task> taskToBeScheduled = new ArrayList<>();
		int iteration = iterations.get(task.getTaskId()) == null ? 1 :  iterations.get(task.getTaskId()) + 1;
		for(String loopOverRef : loopOver){
			Task existingTask = workflow.getTaskByRefName(loopOverRef);
			existingTask.setRetried(true);
			Task newTask = provider.taskToBeRescheduled(existingTask);
			newTask.setSeq(existingTask.getSeq());
			newTask.setReferenceTaskName(existingTask.getReferenceTaskName() + "_" + iteration);
			newTask.setRetryCount(existingTask.getRetryCount());
			newTask.setScheduledTime(System.currentTimeMillis());
			taskToBeScheduled.add(newTask);
		}
		iterations.put(task.getTaskId(), iteration);
		provider.scheduleTask(workflow, taskToBeScheduled);
		return false;
	}

	boolean markLoopTaskFailed(Task task, String failureReason) {
		task.setReasonForIncompletion(failureReason);
		task.setStatus(Status.FAILED);
		logger.debug("taskid {} took {} iterations to failed",task.getTaskId(), iterations.get(task.getTaskId()));
		iterations.remove(task.getTaskId());
		return true;
	}

	boolean markLoopTaskSuccess(Task task, Workflow workflow, Map<String, Integer> iterations) {
		logger.debug("workflowid {} took {} iterations to complete",workflow.getWorkflowId(), iterations.get(task.getTaskId()));
		iterations.remove(task.getTaskId());
		task.setStatus(Status.COMPLETED);
		return true;
	}

	@VisibleForTesting
	boolean getEvaluatedCondition(Task task,int iteration, Workflow workflow) {
		Map<String, Object> taskInput = getConditionInput(task, iteration, workflow);
		String condition = task.getWorkflowTask().getLoopCondition();
		boolean shouldContinue = false;
		if (condition != null) {
			logger.debug("Case being evaluated using decision expression: {}", condition);
			try {
				//Evaluate the expression by using the Nashhorn based script evaluator
				shouldContinue = ScriptEvaluator.evalBool(condition, taskInput);
			} catch (ScriptException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException("Error while evaluating the script " + condition, e);
			}
		}
		return shouldContinue;
	}

}
