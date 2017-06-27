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
package com.netflix.conductor.dao;

import java.util.List;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;

/**
 * 
 * @author Viren
 * DAO to index the workflow and task details for searching.
 */
public interface IndexDAO {

	/**
	 * 
	 * @param workflow Workflow to be indexed
	 */
	public void index(Workflow workflow);
	
	/**
	 * 
	 * @param task Task to be indexed
	 */
	public void index(Task task);

	/**
	 * 
	 * @param query SQL like query for workflow search parameters.
	 * @param freeText	Additional query in free text.  Lucene syntax
	 * @param start	start start index for pagination
	 * @param count	count # of workflow ids to be returned
	 * @param sort sort options
	 * @return List of workflow ids for the matching query
	 */
	public SearchResult<String> searchWorkflows(String query, String freeText, int start, int count, List<String> sort);
	
	
	/**
	 * 
	 * @param query SQL like query for task search parameters.
	 * @param freeText	Additional query in free text.  Lucene syntax
	 * @param start	start start index for pagination
	 * @param count	count # of task ids to be returned
	 * @param sort sort options
	 * @return List of workflow ids for the matching query
	 */
	public SearchResult<String> searchTasks(String query, String freeText, int start, int count, List<String> sort);

	/**
	 * Remove the workflow index
	 * @param workflowId workflow to be removed
	 */
	public void remove(String workflowId);

	/**
	 * Updates the index
	 * @param workflowInstanceId id of the workflow
	 * @param keys keys to be updated
	 * @param values values. Number of keys and values MUST match.
	 */
	public void update(String workflowInstanceId, String[] keys, Object[] values);
	
	/**
	 * Retrieves a specific field from the index 
	 * @param workflowInstanceId id of the workflow
	 * @param key field to be retrieved
	 * @return value of the field as string
	 */
	public String get(String workflowInstanceId, String key);

	/**
	 * 
	 * @param logs Task Execution logs to be indexed
	 */
	public void add(List<TaskExecLog> logs);
	
	/**
	 * 
	 * @param taskId Id of the task for which to fetch the execution logs
	 * @return Returns the task execution logs for given task id
	 */
	public List<TaskExecLog> getTaskLogs(String taskId);
	
	
	/**
	 * 
	 * @param ee Event Execution to be indexed
	 */
	public void add(EventExecution ee);

	/**
	 * Adds an incoming external message into the index
	 * @param queue Name of the registered queue
	 * @param msg Message
	 */
	public void addMessage(String queue, Message msg);

}