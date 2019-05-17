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

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.utils.IDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SCHEDULED;

/**
 * @author Viren
 *
 */
public class Join extends WorkflowSystemTask {

	public Join() {
		super("JOIN");
	}

	Logger logger = LoggerFactory.getLogger(DecisionTaskMapper.class);
	Map<String, Integer> iterations = new HashMap<>();
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {
		
		boolean allDone = true;
		boolean hasFailures = false;
		StringBuilder failureReason = new StringBuilder();
		List<String> joinOn = (List<String>) task.getInputData().get("joinOn");

		int iteration = iterations.get(task.getReferenceTaskName()) == null ? 0 : iterations.get(task.getReferenceTaskName());

		for(String joinOnRef : joinOn){
			if (iteration > 0) {
				joinOnRef += "_" + (iteration);
			}
			Task forkedTask = workflow.getTaskByRefName(joinOnRef);
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
			task.getOutputData().put(joinOnRef, forkedTask.getOutputData());
			allDone = taskStatus.isTerminal();
			if(!allDone || hasFailures){
				break;
			}
		}
		if (hasFailures) {
			task.setReasonForIncompletion(failureReason.toString());
			task.setStatus(Status.FAILED);
			task.setSeq(task.getSeq()+ iteration * joinOn.size());
			iterations.remove(task.getReferenceTaskName());
			return true;
		} else if (!allDone) {
			return false;
		}
		boolean shouldExit = getEvaluatedCaseValue(task.getWorkflowTask(), task.getInputData());
		if (!shouldExit){
			joinOn = (List<String>) task.getInputData().get("joinOn");
			List<Task> taskToBeScheduled = new ArrayList<>();
			iteration++;
			for(String joinOnRef : joinOn){
				Task existingTask = workflow.getTaskByRefName(joinOnRef);
				existingTask.setRetried(true);
				Task newTask = provider.taskToBeRescheduled(existingTask);
				newTask.setReferenceTaskName(existingTask.getReferenceTaskName() + "_" + iteration);
				newTask.setRetryCount(existingTask.getRetryCount());
				newTask.setScheduledTime(System.currentTimeMillis());
				taskToBeScheduled.add(newTask);
			}
			iterations.put(task.getReferenceTaskName(), iteration);
			provider.scheduleTask(workflow, taskToBeScheduled);
			return false;
		} else {
			task.setSeq(task.getSeq() + iteration * joinOn.size());
			iterations.remove(task.getReferenceTaskName());
			task.setStatus(Status.COMPLETED);
			return true;
		}

	}

	boolean getEvaluatedCaseValue(WorkflowTask taskToSchedule, Map<String, Object> taskInput) {
		String expression = taskToSchedule.getExpression();
		boolean caseValue = false;
//		if (expression != null) {
//			logger.debug("Case being evaluated using decision expression: {}", expression);
//			try {
//				//Evaluate the expression by using the Nashhorn based script evaluator
//				Object returnValue = ScriptEvaluator.eval(expression, taskInput);
//				caseValue = (returnValue == null) ? false : true;
//			} catch (ScriptException e) {
//				logger.error(e.getMessage(), e);
//				throw new RuntimeException("Error while evaluating the script " + expression, e);
//			}
//
//		}
		return caseValue;
	}

}
