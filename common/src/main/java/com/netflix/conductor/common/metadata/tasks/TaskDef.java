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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.annotations.ProtoEnum;
import com.netflix.conductor.common.annotations.ProtoField;
import com.netflix.conductor.common.annotations.ProtoMessage;
import com.netflix.conductor.common.metadata.Auditable;

/**
 * @author Viren
 * Defines a workflow task definition 
 */
@ProtoMessage
public class TaskDef extends Auditable {

	@ProtoEnum
	public static enum TimeoutPolicy {RETRY, TIME_OUT_WF, ALERT_ONLY}

	@ProtoEnum
	public static enum RetryLogic {FIXED, EXPONENTIAL_BACKOFF}
	
	private static final int ONE_HOUR = 60 * 60;
	
	/**
	 * Unique name identifying the task.  The name is unique across 
	 */
	@ProtoField(id = 1)
	private String name;

	@ProtoField(id = 2)
	private String description;

	@ProtoField(id = 3)
	private int retryCount = 3; // Default

	@ProtoField(id = 4)
	private long timeoutSeconds;

	@ProtoField(id = 5)
	private List<String> inputKeys = new ArrayList<String>();

	@ProtoField(id = 6)
	private List<String> outputKeys = new ArrayList<String>();

	@ProtoField(id = 7)
	private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;

	@ProtoField(id = 8)
	private RetryLogic retryLogic = RetryLogic.FIXED;

	@ProtoField(id = 9)
	private int retryDelaySeconds = 60;

	@ProtoField(id = 10)
	private int responseTimeoutSeconds = ONE_HOUR;

	@ProtoField(id = 11)
	private Integer concurrentExecLimit;

	@ProtoField(id = 12)
	private Map<String, Object> inputTemplate = new HashMap<>();
		
	public TaskDef() {
	}
	
	public TaskDef(String name) {
		this.name = name;
	}

	public TaskDef(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public TaskDef(String name, String description, int retryCount, int timeout) {
		this.name = name;
		this.description = description;
		this.retryCount = retryCount;
		this.timeoutSeconds = timeout;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the retryCount
	 */
	public int getRetryCount() {
		return retryCount;
	}

	/**
	 * @param retryCount the retryCount to set
	 */
	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	/**
	 * @return the timeoutSeconds
	 */
	public long getTimeoutSeconds() {
		return timeoutSeconds;
	}

	/**
	 * @param timeoutSeconds the timeoutSeconds to set
	 */
	public void setTimeoutSeconds(long timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	/**
	 * 
	 * @return Returns the input keys
	 */
	public List<String> getInputKeys() {
		return inputKeys;
	}

	/**
	 * @param inputKeys Set of keys that the task accepts in the input map
	 */
	public void setInputKeys(List<String> inputKeys) {
		this.inputKeys = inputKeys;
	}

	/**
	 * @return Returns the output keys for the task when executed
	 */
	public List<String> getOutputKeys() {
		return outputKeys;
	}

	/**
	 * @param outputKeys Sets the output keys
	 */
	public void setOutputKeys(List<String> outputKeys) {
		this.outputKeys = outputKeys;
	}

	
	/**
	 * @return the timeoutPolicy
	 */
	public TimeoutPolicy getTimeoutPolicy() {
		return timeoutPolicy;
	}

	/**
	 * @param timeoutPolicy the timeoutPolicy to set
	 */
	public void setTimeoutPolicy(TimeoutPolicy timeoutPolicy) {
		this.timeoutPolicy = timeoutPolicy;
	}

	/**
	 * @return the retryLogic
	 */
	public RetryLogic getRetryLogic() {
		return retryLogic;
	}

	/**
	 * @param retryLogic the retryLogic to set
	 */
	public void setRetryLogic(RetryLogic retryLogic) {
		this.retryLogic = retryLogic;
	}

	/**
	 * @return the retryDelaySeconds
	 */
	public int getRetryDelaySeconds() {
		return retryDelaySeconds;
	}
	
	/**
	 * 
	 * @return the timeout for task to send response.  After this timeout, the task will be re-queued
	 */
	public int getResponseTimeoutSeconds() {
		return responseTimeoutSeconds;
	}
	
	/**
	 * 
	 * @param responseTimeoutSeconds - timeout for task to send response.  After this timeout, the task will be re-queued
	 */
	public void setResponseTimeoutSeconds(int responseTimeoutSeconds) {
		this.responseTimeoutSeconds = responseTimeoutSeconds;
	}

	/**
	 * @param retryDelaySeconds the retryDelaySeconds to set
	 */
	public void setRetryDelaySeconds(int retryDelaySeconds) {
		this.retryDelaySeconds = retryDelaySeconds;
	}
	
	/**
	 * @return the inputTemplate
	 */
	public Map<String, Object> getInputTemplate() {
		return inputTemplate;
	}

	/**
	 * 
	 * @param concurrentExecLimit Limit of number of concurrent task that can be  IN_PROGRESS at a given time.  Seting the value to 0 removes the limit.
	 */
	public void setConcurrentExecLimit(Integer concurrentExecLimit) {
		this.concurrentExecLimit = concurrentExecLimit;
	}
	
	/**
	 * 
	 * @return Limit of number of concurrent task that can be  IN_PROGRESS at a given time
	 */
	public Integer getConcurrentExecLimit() {
		return concurrentExecLimit;
	}
	/**
	 * 
	 * @return concurrency limit
	 */
	public int concurrencyLimit() {
		return concurrentExecLimit == null ? 0 : concurrentExecLimit.intValue();
	}
	
	/**
	 * @param inputTemplate the inputTemplate to set
	 * 
	 */
	public void setInputTemplate(Map<String, Object> inputTemplate) {
		this.inputTemplate = inputTemplate;
	}

	@Override
	public String toString(){
		return name;
	}
}
