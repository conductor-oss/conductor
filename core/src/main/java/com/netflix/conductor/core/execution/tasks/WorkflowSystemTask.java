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

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;

/**
 * @author Viren
 *
 */
public class WorkflowSystemTask {

	private static Map<String, WorkflowSystemTask> registry = new HashMap<>();
	
	private String name;
	
	public WorkflowSystemTask(String name) {
		this.name = name;
		registry.put(name, this);
	}

	/**
	 * Start the task execution
	 * @param workflow Workflow for which the task is being started
	 * @param task Instance of the Task
	 * @param executor Workflow Executor
	 * @throws Exception If there is an error when starting the task
	 */
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		//Do nothing unless overridden by the task implementation
	}
	
	/**
	 * 
	 * @param workflow Workflow for which the task is being started
	 * @param task Instance of the Task
	 * @param executor Workflow Executor
	 * @return true, if the execution has changed the task status.  return false otherwise.
	 * @throws Exception If there is an error when starting the task
	 */
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		return false;
	}
	
	/**
	 * Cancel task execution
	 * @param workflow Workflow for which the task is being started
	 * @param task Instance of the Task
	 * @param executor Workflow Executor
	 * @throws Exception If there is an error when starting the task
	 */
	public void cancel(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
	}
	
	/**
	 * 
	 * @return name of the system task
	 */
	public String getName(){
		return name;
	}
	
	@Override
	public String toString(){
		return name;
	}
	
	
	public static boolean is(String type){
		return registry.containsKey(type);
	}
	
	public static WorkflowSystemTask get(String type) {
		return registry.get(type);
	}

	
}
