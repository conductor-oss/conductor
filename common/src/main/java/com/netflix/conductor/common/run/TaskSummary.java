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
package com.netflix.conductor.common.run;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.github.vmg.protogen.annotations.*;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;

/**
 * @author Viren
 *
 */
@ProtoMessage(fromProto = false)
public class TaskSummary {

	/**
	 * The time should be stored as GMT
	 */
	private static final TimeZone gmt = TimeZone.getTimeZone("GMT");

	@ProtoField(id = 1)
	private String workflowId;

	@ProtoField(id = 2)
	private String workflowType;

	@ProtoField(id = 3)
	private String correlationId;

	@ProtoField(id = 4)
	private String scheduledTime;

	@ProtoField(id = 5)
	private String startTime;

	@ProtoField(id = 6)
	private String updateTime;

	@ProtoField(id = 7)
	private String endTime;

	@ProtoField(id = 8)
	private Status status;

	@ProtoField(id = 9)
	private String reasonForIncompletion;

	@ProtoField(id = 10)
	private long executionTime;

	@ProtoField(id = 11)
	private long queueWaitTime;

	@ProtoField(id = 12)
	private String taskDefName;

	@ProtoField(id = 13)
	private String taskType;

	@ProtoField(id = 14)
	private String input;

	@ProtoField(id = 15)
	private String output;

	@ProtoField(id = 16)
	private String taskId;

    public TaskSummary() {
    }

	public TaskSummary(Task task) {
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    	sdf.setTimeZone(gmt);
    	
    	this.taskId = task.getTaskId();
    	this.taskDefName = task.getTaskDefName();
    	this.taskType = task.getTaskType();
		this.workflowId = task.getWorkflowInstanceId();
		this.workflowType = task.getWorkflowType();
		this.correlationId = task.getCorrelationId();
		this.scheduledTime = sdf.format(new Date(task.getScheduledTime()));
		this.startTime = sdf.format(new Date(task.getStartTime()));
		this.updateTime = sdf.format(new Date(task.getUpdateTime()));
		this.endTime = sdf.format(new Date(task.getEndTime()));
		this.status = task.getStatus();
		this.reasonForIncompletion = task.getReasonForIncompletion();
		this.queueWaitTime = task.getQueueWaitTime();
		if (task.getInputData() != null) {
			this.input = task.getInputData().toString();
		}
		
		if (task.getOutputData() != null) {
			this.output = task.getOutputData().toString();
		}
		
		
		if(task.getEndTime() > 0){
			this.executionTime = task.getEndTime() - task.getStartTime();
		}
	}

	/**
	 * @return the workflowId
	 */
	public String getWorkflowId() {
		return workflowId;
	}

	public String getWorkflowType() {
		return workflowType;
	}

	/**
	 * @param workflowId the workflowId to set
	 */
	public void setWorkflowId(String workflowId) {
		this.workflowId = workflowId;
	}

	/**
	 * @return the correlationId
	 */
	public String getCorrelationId() {
		return correlationId;
	}

	/**
	 * @param correlationId the correlationId to set
	 */
	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}

	/**
	 * @return the scheduledTime
	 */
	public String getScheduledTime() {
		return scheduledTime;
	}

	/**
	 * @param scheduledTime the scheduledTime to set
	 */
	public void setScheduledTime(String scheduledTime) {
		this.scheduledTime = scheduledTime;
	}

	/**
	 * @return the startTime
	 */
	public String getStartTime() {
		return startTime;
	}

	/**
	 * @param startTime the startTime to set
	 * 
	 */
	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	/**
	 * @return the updateTime
	 */
	public String getUpdateTime() {
		return updateTime;
	}

	/**
	 * @param updateTime the updateTime to set
	 * 
	 */
	public void setUpdateTime(String updateTime) {
		this.updateTime = updateTime;
	}

	/**
	 * @return the endTime
	 */
	public String getEndTime() {
		return endTime;
	}

	/**
	 * @param endTime the endTime to set
	 * 
	 */
	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}

	/**
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 * 
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

	/**
	 * @return the reasonForIncompletion
	 */
	public String getReasonForIncompletion() {
		return reasonForIncompletion;
	}

	/**
	 * @param reasonForIncompletion the reasonForIncompletion to set
	 * 
	 */
	public void setReasonForIncompletion(String reasonForIncompletion) {
		this.reasonForIncompletion = reasonForIncompletion;
	}

	/**
	 * @return the executionTime
	 */
	public long getExecutionTime() {
		return executionTime;
	}

	/**
	 * @param executionTime the executionTime to set
	 * 
	 */
	public void setExecutionTime(long executionTime) {
		this.executionTime = executionTime;
	}

	/**
	 * @return the queueWaitTime
	 */
	public long getQueueWaitTime() {
		return queueWaitTime;
	}

	/**
	 * @param queueWaitTime the queueWaitTime to set
	 * 
	 */
	public void setQueueWaitTime(long queueWaitTime) {
		this.queueWaitTime = queueWaitTime;
	}

	/**
	 * @return the taskDefName
	 */
	public String getTaskDefName() {
		return taskDefName;
	}

	/**
	 * @param taskDefName the taskDefName to set
	 * 
	 */
	public void setTaskDefName(String taskDefName) {
		this.taskDefName = taskDefName;
	}

	/**
	 * @return the taskType
	 */
	public String getTaskType() {
		return taskType;
	}

	/**
	 * @param taskType the taskType to set
	 * 
	 */
	public void setTaskType(String taskType) {
		this.taskType = taskType;
	}

	/**
	 * 
	 * @return input to the task
	 */
	public String getInput() {
		return input;
	}
	
	/**
	 * 
	 * @param input input to the task
	 */
	public void setInput(String input) {
		this.input = input;
	}
	
	/**
	 * 
	 * @return output of the task
	 */
	public String getOutput() {
		return output;
	}
	
	/**
	 * 
	 * @param output Task output
	 */
	public void setOutput(String output) {
		this.output = output;
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
	
	
}
