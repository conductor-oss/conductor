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
	 * Remove the workflow index
	 * @param workflowId workflow to be removed
	 */
	public void remove(String workflowId);

	/**
	 * Updates the index
	 * @param workflowInstanceId id of the workflow
	 * @param key key to be updated
	 * @param value value
	 */
	public void update(String workflowInstanceId, String key, Object value);

	/**
	 * 
	 * @param log Task Execution log to be indexed
	 */
	public void add(TaskExecLog log);
	
	
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