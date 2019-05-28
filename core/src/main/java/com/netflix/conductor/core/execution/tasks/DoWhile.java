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
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
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
			task.setReasonForIncompletion(failureReason.toString());
			task.setStatus(Status.FAILED);
			logger.debug("taskid {} took {} iterations to failed",task.getTaskId(), iterations.get(task.getTaskId()));
			iterations.remove(task.getTaskId());
			return true;
		} else if (!allDone) {
			return false;
		}
		List<String> loopOverTask = task.getWorkflowTask().getLoopOver();
		Map<String, Object> taskInput = new HashMap<>();
		for (String taskName : loopOverTask) {
			String refName = taskName;
			if (iteration > 0) {
				refName += "_" + (iteration);
			}
			Task temp = workflow.getTaskByRefName(refName);
			taskInput.put(taskName, temp.getOutputData().get("body"));
			logger.debug("Taskname {} output {}", temp.getReferenceTaskName(), temp.getOutputData());
		}
		boolean shouldContinue = getEvaluatedCondition(task.getWorkflowTask(), taskInput);
		logger.debug("workflowid {} shouldcontinue {}",workflow.getWorkflowId(), shouldContinue);
		if (shouldContinue){
			loopOver = (List<String>) task.getInputData().get("loopOver");
			List<Task> taskToBeScheduled = new ArrayList<>();
			iteration++;
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
		} else {
			logger.debug("workflowid {} took {} iterations to complete",workflow.getWorkflowId(), iterations.get(task.getTaskId()));
			iterations.remove(task.getTaskId());
			task.setStatus(Status.COMPLETED);
			return true;
		}

	}

	@VisibleForTesting
	boolean getEvaluatedCondition(WorkflowTask taskToSchedule, Map<String, Object> taskInput) {
		String condition = taskToSchedule.getLoopCondition();
		boolean caseValue = false;
		if (condition != null) {
			logger.debug("Case being evaluated using decision expression: {}", condition);
			try {
				//Evaluate the expression by using the Nashhorn based script evaluator
				caseValue = ScriptEvaluator.evalBool(condition, taskInput);
			} catch (ScriptException e) {
				logger.error(e.getMessage(), e);
				throw new RuntimeException("Error while evaluating the script " + condition, e);
			}
		}
		int rand = (int)(Math.random() * 10);
		return rand %2 == 00 ;
	}

}
