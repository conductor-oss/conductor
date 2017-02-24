/**
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Viren
 * Model that represents the task's execution log.
 */
public class TaskExecLog {

	private Map<String, Object> environment = new HashMap<>();
	
	private Map<String, Object> data = new HashMap<>();
	
	private List<String> errorTrace = new LinkedList<>();
	
	private String error;
	
	private String taskId;
	
	private String createdTime;
	
	public TaskExecLog() {
		
	}

	/**
	 * @return the environment
	 */
	public Map<String, Object> getEnvironment() {
		return environment;
	}

	/**
	 * @param environment the environment to set
	 * 
	 */
	public void setEnvironment(Map<String, Object> environment) {
		this.environment = environment;
	}

	/**
	 * @return the data
	 */
	public Map<String, Object> getData() {
		return data;
	}

	/**
	 * @param data the data to set
	 * 
	 */
	public void setData(Map<String, Object> data) {
		this.data = data;
	}

	/**
	 * @return the errorTrace
	 */
	public List<String> getErrorTrace() {
		return errorTrace;
	}

	/**
	 * @param errorTrace the errorTrace to set
	 * 
	 */
	public void setErrorTrace(List<String> errorTrace) {
		this.errorTrace = errorTrace;
	}

	/**
	 * @return the error
	 */
	public String getError() {
		return error;
	}

	/**
	 * @param error the error to set
	 * 
	 */
	public void setError(String error) {
		this.error = error;
	}

	/**
	 * @return the taskId
	 */
	public String getTaskId() {
		return taskId;
	}

	/**
	 * @param taskId the taskId to set
	 * 
	 */
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	/**
	 * 
	 * @return Creation time (server side) when the log was added
	 */
	public String getCreatedTime() {
		return createdTime;
	}
	
	/**
	 * 
	 * @param createdTime creation time to set
	 */
	public void setCreatedTime(String createdTime) {
		this.createdTime = createdTime;
	}
	
	
	
}
