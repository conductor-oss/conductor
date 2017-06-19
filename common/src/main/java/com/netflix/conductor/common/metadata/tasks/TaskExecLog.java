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

import java.util.LinkedList;
import java.util.List;

/**
 * @author Viren
 * Model that represents the task's execution log.
 */
public class TaskExecLog {
	
	private List<String> logs = new LinkedList<>();
	
	private String taskId;
	
	private long createdTime;
	
	public TaskExecLog() {}
	
	public TaskExecLog(String log) {
		this.logs.add(log);
		this.createdTime = System.currentTimeMillis();
	}
	/**
	 * 
	 * @return Task Execution Logs
	 */
	public List<String> getLogs() {
		return logs;
	}
	
	/**
	 * 
	 * @param logs Task Execution Logs
	 */
	public void setLogs(List<String> logs) {
		this.logs = logs;
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
	 * @return the createdTime
	 */
	public long getCreatedTime() {
		return createdTime;
	}

	/**
	 * @param createdTime the createdTime to set
	 * 
	 */
	public void setCreatedTime(long createdTime) {
		this.createdTime = createdTime;
	}
	
	
}
