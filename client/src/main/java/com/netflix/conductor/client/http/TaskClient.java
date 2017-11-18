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
package com.netflix.conductor.client.http;

import java.util.List;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;

/**
 * 
 * @author visingh
 * @author Viren
 * Client for conductor task management including polling for task, updating task status etc.
 */
public class TaskClient extends ClientBase {

	private static GenericType<List<Task>> tasks = new GenericType<List<Task>>() {};
	
	private static GenericType<List<TaskDef>> task_def_types = new GenericType<List<TaskDef>>(){};

	/**
	 * Creates a default task client
	 */
	public TaskClient() {
		super();
	}
	
	/**
	 * 
	 * @param config REST Client configuration
	 */
	public TaskClient(ClientConfig config) {
		super(config);
	}
	
	/**
	 * 
	 * @param config REST Client configuration
	 * @param handler Jersey client handler.  Useful when plugging in various http client interaction modules (e.g. ribbon)
	 */
	public TaskClient(ClientConfig config, ClientHandler handler) {
		super(config, handler);
	}
	
	/**
	 * 
	 * @param config config REST Client configuration
	 * @param handler handler Jersey client handler.  Useful when plugging in various http client interaction modules (e.g. ribbon)
	 * @param filters Chain of client side filters to be applied per request
	 */
	public TaskClient(ClientConfig config, ClientHandler handler, ClientFilter...filters) {
		super(config, handler);
		for(ClientFilter filter : filters) {
			super.client.addFilter(filter);
		}
	}

	/**
	 * Poll for tasks
	 * @param taskType Type of task to poll for
	 * @param workerId Name of the client worker.  Used for logging.
	 * @param count maximum number of tasks to be returned.  Can be less.
	 * @param timeoutInMillisecond  Long poll wait timeout.
	 * @return List of tasks awaiting to be executed.
	 * 
	 */
	public List<Task> poll(String taskType, String workerId, int count, int timeoutInMillisecond) {
		Object[] params = new Object[]{"workerid", workerId, "count", count, "timeout", timeoutInMillisecond};
		return getForEntity("tasks/poll/batch/{taskType}", params, tasks, taskType);
	}

	/**
	 * Poll for tasks
	 * @param taskType Type of task to poll for
	 * @param domain The domain of the task type
	 * @param workerId Name of the client worker.  Used for logging.
	 * @param count maximum number of tasks to be returned.  Can be less.
	 * @param timeoutInMillisecond  Long poll wait timeout.
	 * @return List of tasks awaiting to be executed.
	 * 
	 */
	public List<Task> poll(String taskType, String domain, String workerId, int count, int timeoutInMillisecond) {
		Object[] params = new Object[]{"workerid", workerId, "count", count, "timeout", timeoutInMillisecond, "domain", domain};
		return getForEntity("tasks/poll/batch/{taskType}", params, tasks, taskType);
	}

	/**
	 * 
	 * @param taskId ID of the task
	 * @return Task details
	 */
	public Task get(String taskId) {
		Task task = getForEntity("tasks/{taskId}", null, Task.class, taskId);
		return task;
	}

	/**
	 *  
	 * @param taskType Type of task
	 * @param startKey id of the task from where to return the results.  NULL to start from the begining.
	 * @param count number of tasks to retrieve
	 * @return  Returns the list of PENDING tasks by type, starting with a given task Id.
	 */
	public List<Task> getTasks(String taskType, String startKey, Integer count) {
		Object[] params = new Object[]{"startKey", startKey, "count", count};
		 return getForEntity("tasks/in_progress/{taskType}", params, tasks, taskType);
	}
	
	/**
	 * 
	 * @param workflowId Workflow instance id
	 * @param taskReferenceName reference name of the task
	 * @return Returns the pending workflow task identified by the reference name
	 */
	public Task getPendingTaskForWorkflow(String workflowId, String taskReferenceName) {
		return getForEntity("tasks/in_progress/{workflowId}/{taskRefName}", null, Task.class, workflowId, taskReferenceName);
	}


	/**
	 * Updates the result of a task execution
	 * @param task TaskResults to be updated.
	 */
	public void updateTask(TaskResult task)  {
		postForEntity("tasks", task);
	}
	
	public void log(String taskId, String logMessage) {
		postForEntity("tasks/"  + taskId + "/log", logMessage);		
	}
	
	/**
	 * Ack for the task poll
	 * @param taskId Id of the task to be polled
	 * @param workerId user identified worker.
	 * @return true if the task was found with the given ID and acknowledged.  False otherwise.  If the server returns false, the client should NOT attempt to ack again.
	 */
	public Boolean ack(String taskId, String workerId) {
		Object[] params = new Object[]{"workerid", workerId};
		String response = postForEntity("tasks/{taskId}/ack", null, params, String.class, taskId);
		return Boolean.valueOf(response);
	}	
	
	
	/**
	 * 
	 * @return List of all the task definitions registered with the server
	 */
	public List<TaskDef> getTaskDef() {
		return getForEntity("metadata/taskdefs", null, task_def_types);	
	}
	
	/**
	 * 
	 * @param taskType type of task for which to retrieve the definition
	 * @return Task Definition for the given task type
	 */
	public TaskDef getTaskDef(String taskType) {
		 return getForEntity("metadata/taskdefs/{tasktype}", null, TaskDef.class, taskType);	
	}
	
	/**
	 * 
	 * @param taskType Task type to be unregistered.  Use with caution. 
	 */
	public void unregisterTaskDef(String taskType) {
		delete("metadata/taskdefs/{tasktype}", taskType);
	}

	/**
	 * Registers a set of task types with the conductor server
	 * @param taskDefs List of task types to be registered.
	 */
	public void registerTaskDefs(List<TaskDef> taskDefs) {
		postForEntity("metadata/taskdefs", taskDefs);	
	}
	
}
