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
import com.netflix.conductor.dao.QueueDAO;

/**
 * @author Viren
 *
 */
public class Event extends WorkflowSystemTask {

	private QueueDAO queues;
	
	public Event() {
		super("EVENT");
	}
	
	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor provider) throws Exception {

		String eventName = workflow.getWorkflowType() + "." + task.getReferenceTaskName();
		queues.push(eventName, task.getTaskId(), 0);
		task.setStatus(Status.COMPLETED);
	}
	
	@Override
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) throws Exception {
		
		if (task.getStatus().equals(Status.SCHEDULED)) {
			long timeSince = System.currentTimeMillis() - task.getScheduledTime();
			if(timeSince > 600_000) {
				start(workflow, task, provider);
				return true;	
			}else {
				return false;
			}				
		}

		return false;
	}
	
	@Override
	public void cancel(Workflow workflow, Task task, WorkflowExecutor provider) throws Exception {
		String queueName = workflow.getWorkflowType() + "." + task.getReferenceTaskName();
		queues.remove(queueName, task.getTaskId());
	}

}
