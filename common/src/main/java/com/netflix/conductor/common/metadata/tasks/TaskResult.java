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
import java.util.Map;

/**
 * @author Viren
 * Result of the task execution.
 * 
 */
public class TaskResult {

	public enum Status {

		IN_PROGRESS, FAILED, COMPLETED, SCHEDULED;		//SCHEDULED is added for the backward compatibility and should NOT be used when updating the task result
	};

	private String workflowInstanceId;
	
	private String taskId;
	
	private String reasonForIncompletion;
	
	private long callbackAfterSeconds;
	
	private String workerId;
	
	private Status status;

	private Map<String, Object> outputData = new HashMap<>();
	
	private TaskExecLog log = new TaskExecLog();

	public TaskResult(Task task) {
		this.workflowInstanceId = task.getWorkflowInstanceId();
		this.taskId = task.getTaskId();
		this.reasonForIncompletion = task.getReasonForIncompletion();
		this.callbackAfterSeconds = task.getCallbackAfterSeconds();
		this.status = Status.valueOf(task.getStatus().name());
		this.workerId = task.getWorkerId();
		this.outputData = task.getOutputData();
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

	/**
	 * When set to non-zero values, the task remains in the queue for the specified seconds before sent back to the worker when polled. 
	 * Useful for the long running task, where the task is updated as IN_PROGRESS and should not be polled out of the queue for a specified amount of time.  (delayed queue implementation)
	 * @param callbackAfterSeconds.   Amount of time in seconds the task should be held in the queue before giving it to a polling worker.
	 */
	public void setCallbackAfterSeconds(long callbackAfterSeconds) {
		this.callbackAfterSeconds = callbackAfterSeconds;
	}

	public String getWorkerId() {
		return workerId;
	}

	/**
	 * 
	 * @param workerId a free form string identifying the worker host.  
	 * Could be hostname, IP Address or any other meaningful identifier that can help identify the host/process which executed the task, in case of troubleshooting.
	 */
	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}
	
	/**
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * 
	 * @param status Status of the task
	 * <p>
	 * <b>IN_PROGRESS</b>: Use this for long running tasks, indicating the task is still in progress and should be checked again at a later time.  e.g. the worker checks the status of the job in the DB, while the job is being executed by another process.
	 * </p><p>
	 * <b>FAILED, COMPLETED</b>: Terminal statuses for the task.
	 * </p>
	 * 
	 * @see #setCallbackAfterSeconds(long)
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

	public Map<String, Object> getOutputData() {
		return outputData;
	}

	/**
	 * 
	 * @param outputData output data to be set for the task execution result
	 */
	public void setOutputData(Map<String, Object> outputData) {
		this.outputData = outputData;
	}

	/**
	 * @return the task execution log
	 */
	public TaskExecLog getLog() {
		return log;
	}

	/**
	 * @param log task execution log
	 * 
	 */
	public void setLog(TaskExecLog log) {
		this.log = log;
	}

	@Override
	public String toString() {
		return "TaskResult [workflowInstanceId=" + workflowInstanceId + ", taskId=" + taskId + ", status=" + status + "]";
	}
	
}
