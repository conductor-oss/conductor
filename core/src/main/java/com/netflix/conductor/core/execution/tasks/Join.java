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
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.IDGenerator;

import java.util.ArrayList;
import java.util.List;

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

	private boolean done = false;
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {
		
		boolean allDone = true;
		boolean hasFailures = false;
		StringBuilder failureReason = new StringBuilder();
		List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
		for(String joinOnRef : joinOn){
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
		if(allDone || hasFailures) {
			if (hasFailures) {
				task.setReasonForIncompletion(failureReason.toString());
				task.setStatus(Status.FAILED);
			} else {
				task.setStatus(Status.COMPLETED);
			}
			return true;
		}
		 else {
//			task.setStatus(Status.COMPLETED);
			joinOn = (List<String>) task.getInputData().get("joinOn");
			List<Task> taskToBeScheduled = new ArrayList<>();
			for(String joinOnRef : joinOn){
				Task existingTask = workflow.getTaskByRefName(joinOnRef);
				Task newTask = provider.taskToBeRescheduled(existingTask);
				newTask.setReferenceTaskName(existingTask.getReferenceTaskName() + "done");
				newTask.setWorkflowInstanceId(workflow.getWorkflowId());
				newTask.setCorrelationId(workflow.getCorrelationId());
				newTask.setWorkflowType(workflow.getWorkflowName());
				newTask.setScheduledTime(System.currentTimeMillis());
				newTask.setTaskId(IDGenerator.generate());
				newTask.setRetriedTaskId(existingTask.getTaskId());
				newTask.setStatus(SCHEDULED);
				newTask.setRetryCount(existingTask.getRetryCount() + 1);
				newTask.setRetried(false);
				newTask.setPollCount(0);
				newTask.setCallbackAfterSeconds(0);
				taskToBeScheduled.add(newTask);
			}
			provider.scheduleTask(workflow, taskToBeScheduled);
			done = true;
		}
		return true;
	}

}
