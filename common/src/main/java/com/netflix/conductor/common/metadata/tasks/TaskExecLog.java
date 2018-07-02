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

import com.github.vmg.protogen.annotations.*;

/**
 * @author Viren
 * Model that represents the task's execution log.
 */
@ProtoMessage
public class TaskExecLog {

	@ProtoField(id = 1)
	private String log;

	@ProtoField(id = 2)
	private String taskId;

	@ProtoField(id = 3)
	private long createdTime;
	
	public TaskExecLog() {}
	
	public TaskExecLog(String log) {
		this.log =log;
		this.createdTime = System.currentTimeMillis();
	}
	
	/**
	 * 
	 * @return Task Exec Log
	 */
	public String getLog() {
		return log;
	}
	
	/**
	 * 
	 * @param log The Log
	 */
	public void setLog(String log) {
		this.log = log;
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
