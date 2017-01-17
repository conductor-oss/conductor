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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Viren
 * Result of the task execution.
 * 
 */
public class TaskResult {

	public enum Status {

		IN_PROGRESS, FAILED, COMPLETED;
	};

	private String workflowInstanceId;
	
	private String taskId;
	
	private String reasonForIncompletion;
	
	private long callbackAfterSeconds;
	
	private String workerId;
	
	private Status status;

	private Map<String, Object> outputData = new HashMap<>();

	public TaskResult(Task task) {
		this.workflowInstanceId = task.getWorkflowInstanceId();
		this.taskId = task.getTaskId();
	}

	public TaskResult(String workflowInstanceId, String taskId) {
		this.workflowInstanceId = workflowInstanceId;
		this.taskId = taskId;
	}

	public TaskResult() {
		
	}

	/**
	 * 
	 * @return Workflow instance id for which the task result is produced
	 */
	public String getWorkflowInstanceId() {
		return workflowInstanceId;
	}

	public void setWorkflowInstanceId(String workflowInstanceId) {
		this.workflowInstanceId = workflowInstanceId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getReasonForIncompletion() {
		return reasonForIncompletion;
	}

	public void setReasonForIncompletion(String reasonForIncompletion) {
		this.reasonForIncompletion = reasonForIncompletion;
	}

	public long getCallbackAfterSeconds() {
		return callbackAfterSeconds;
	}

	public void setCallbackAfterSeconds(long callbackAfterSeconds) {
		this.callbackAfterSeconds = callbackAfterSeconds;
	}

	public String getWorkerId() {
		return workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public Status getTaskStatus() {
		return status;
	}
	
	public void setTaskStatus(Status status) {
		this.status = status;
	}

	/**
	 * 
	 * @param status DO NOT use from the client.  Internal use only!
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

	public Map<String, Object> getOutputData() {
		return outputData;
	}

	public void setOutputData(Map<String, Object> outputData) {
		this.outputData = outputData;
	}

	private static final Set<String> statuses = new HashSet<>();
	static {
		for(Status status : Status.values()) {
			statuses.add(status.name());
		}
	}
	
	public static boolean isValidStatus(String status) {
		return statuses.contains(status);
	}
	
	@Override
	public String toString() {
		return "TaskResult [workflowInstanceId=" + workflowInstanceId + ", taskId=" + taskId + ", status=" + status + "]";
	}
	
}
