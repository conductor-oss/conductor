/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manan
 *
 */
public class DoWhile extends WorkflowSystemTask {

	private ParametersUtils parametersUtils;

	public DoWhile() {
		super("DO_WHILE");
		this.parametersUtils = new ParametersUtils();
	}

	Logger logger = LoggerFactory.getLogger(DoWhile.class);

	@Override
	public void cancel(Workflow workflow, Task task, WorkflowExecutor executor) {
		task.setStatus(Status.CANCELED);
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) {

		boolean allDone = true;
		boolean hasFailures = false;
		StringBuilder failureReason = new StringBuilder();
		Map<String, Object> output = new HashMap<>();
		task.getOutputData().put("iteration", task.getIteration());

		/*
		 * Get the latest set of tasks (the ones that have the highest retry count). We don't want to evaluate any tasks
		 * that have already failed if there is a more current one (a later retry count).
		 */
		Map<String, Task> relevantTasks = new LinkedHashMap<String, Task>();
		Task relevantTask = null;
		for(Task t : workflow.getTasks()) {
			if(task.getWorkflowTask().has(TaskUtils.removeIterationFromTaskRefName(t.getReferenceTaskName()))
			&& !task.getReferenceTaskName().equals(t.getReferenceTaskName())) {
				relevantTask = relevantTasks.get(t.getReferenceTaskName());
				if(relevantTask == null || t.getRetryCount() > relevantTask.getRetryCount()) {
					relevantTasks.put(t.getReferenceTaskName(), t);
				}
			}
		}
		Collection<Task> loopOver = relevantTasks.values();

		for (Task loopOverTask : loopOver) {
			Status taskStatus = loopOverTask.getStatus();
			hasFailures = !taskStatus.isSuccessful();
			if (hasFailures) {
				failureReason.append(loopOverTask.getReasonForIncompletion()).append(" ");
			}
			output.put(TaskUtils.removeIterationFromTaskRefName(loopOverTask.getReferenceTaskName()), loopOverTask.getOutputData());
			allDone = taskStatus.isTerminal();
			if (!allDone || hasFailures) {
				break;
			}
		}
		task.getOutputData().put(String.valueOf(task.getIteration()), output);
		if (hasFailures) {
			logger.debug("taskid {} failed in {} iteration", task.getTaskId(), task.getIteration() + 1);
			return updateLoopTask(task, Status.FAILED, failureReason.toString());
		} else if (!allDone) {
			return false;
		}
		boolean shouldContinue;
		try {
			shouldContinue = getEvaluatedCondition(workflow, task, workflowExecutor);
			logger.debug("taskid {} condition evaluated to {}", task.getTaskId(), shouldContinue);
			if (shouldContinue) {
                task.setIteration(task.getIteration() + 1);
                return scheduleNextIteration(task, workflow, workflowExecutor);
			} else {
				logger.debug("taskid {} took {} iterations to complete", task.getTaskId(), task.getIteration() + 1);
				return markLoopTaskSuccess(task);
			}
		} catch (ScriptException e) {
			String message = String.format("Unable to evaluate condition %s , exception %s", task.getWorkflowTask().getLoopCondition(), e.getMessage());
			logger.error(message);
			logger.error("Marking task {} failed with error.", task.getTaskId());
			return updateLoopTask(task, Status.FAILED_WITH_TERMINAL_ERROR, message);
		}
	}

	boolean scheduleNextIteration(Task task, Workflow workflow, WorkflowExecutor workflowExecutor) {
		logger.debug("Scheduling loop tasks for taskid {} as condition {} evaluated to true",
				task.getTaskId(), task.getWorkflowTask().getLoopCondition());
		workflowExecutor.scheduleNextIteration(task, workflow);
		return true; // Return true even though status not changed. Iteration has to be updated in execution DAO.
	}

	boolean updateLoopTask(Task task,Status status, String failureReason) {
		task.setReasonForIncompletion(failureReason);
		task.setStatus(status);
		return true;
	}

	boolean markLoopTaskSuccess(Task task) {
		logger.debug("taskid {} took {} iterations to complete",task.getTaskId(), task.getIteration() + 1);
		task.setStatus(Status.COMPLETED);
		return true;
	}

	@VisibleForTesting
	boolean getEvaluatedCondition(Workflow workflow, Task task, WorkflowExecutor workflowExecutor) throws ScriptException {
		TaskDef taskDefinition = null;
		try {
			taskDefinition = workflowExecutor.getTaskDefinition(task);
		} catch(TerminateWorkflowException e) {
			// It is ok to not have a task definition for a DO_WHILE task
		}

		Map<String, Object> taskInput = parametersUtils.getTaskInputV2(task.getWorkflowTask().getInputParameters(), workflow, task.getTaskId(), taskDefinition);
		taskInput.put(task.getReferenceTaskName(), task.getOutputData());
		List<Task> loopOver = workflow.getTasks().stream().filter(t -> (task.getWorkflowTask().has(TaskUtils.removeIterationFromTaskRefName(t.getReferenceTaskName())) && !task.getReferenceTaskName().equals(t.getReferenceTaskName()))).collect(Collectors.toList());

		for (Task loopOverTask : loopOver) {
			taskInput.put(TaskUtils.removeIterationFromTaskRefName(loopOverTask.getReferenceTaskName()), loopOverTask.getOutputData());
		}
		String condition = task.getWorkflowTask().getLoopCondition();
		boolean shouldContinue = false;
		if (condition != null) {
			logger.debug("Condition: {} is being evaluated", condition);
			//Evaluate the expression by using the Nashhorn based script evaluator
			shouldContinue = ScriptEvaluator.evalBool(condition, taskInput);
		}
		return shouldContinue;
	}
}
