/*
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
package com.netflix.conductor.common.metadata.workflow;

import com.github.vmg.protogen.annotations.ProtoEnum;
import com.github.vmg.protogen.annotations.ProtoField;
import com.github.vmg.protogen.annotations.ProtoMessage;
import com.netflix.conductor.common.constraints.NoSemiColonConstraint;
import com.netflix.conductor.common.constraints.OwnerEmailMandatoryConstraint;
import com.netflix.conductor.common.constraints.TaskReferenceNameUniqueConstraint;
import com.netflix.conductor.common.metadata.Auditable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;


/**
 * @author Viren
 *
 */
@ProtoMessage
@TaskReferenceNameUniqueConstraint
public class WorkflowDef extends Auditable {

	@ProtoEnum
	public enum TimeoutPolicy {TIME_OUT_WF, ALERT_ONLY}

    @NotEmpty(message = "WorkflowDef name cannot be null or empty")
    @ProtoField(id = 1)
	@NoSemiColonConstraint(message = "Workflow name cannot contain the following set of characters: ':'")
	private String name;

	@ProtoField(id = 2)
	private String description;

	@ProtoField(id = 3)
	private int version = 1;

	@ProtoField(id = 4)
    @NotNull
    @NotEmpty(message = "WorkflowTask list cannot be empty")
	private List<@Valid WorkflowTask> tasks = new LinkedList<>();

	@ProtoField(id = 5)
	private List<String> inputParameters = new LinkedList<>();

	@ProtoField(id = 6)
	private Map<String, Object> outputParameters = new HashMap<>();

	@ProtoField(id = 7)
	private String failureWorkflow;

	@ProtoField(id = 8)
    @Min(value = 2, message = "workflowDef schemaVersion: {value} is only supported")
	@Max(value = 2, message = "workflowDef schemaVersion: {value} is only supported")
    private int schemaVersion = 2;

	//By default a workflow is restartable
	@ProtoField(id = 9)
	private boolean restartable = true;

	@ProtoField(id = 10)
	private boolean workflowStatusListenerEnabled = false;

	@ProtoField(id = 11)
	@OwnerEmailMandatoryConstraint
	@Email(message = "ownerEmail should be valid email address")
	private String ownerEmail;

	@ProtoField(id = 12)
	private TimeoutPolicy timeoutPolicy = TimeoutPolicy.ALERT_ONLY;

	@ProtoField(id = 13)
	@NotNull
	private long timeoutSeconds;

	@ProtoField(id = 14)
	private Map<String, Object> variables = new HashMap<>();

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
	 * @return the tasks
	 */
	public List<WorkflowTask> getTasks() {
		return tasks;
	}

	/**
	 * @param tasks the tasks to set
	 */
	public void setTasks(List<@Valid WorkflowTask> tasks) {
		this.tasks = tasks;
	}

	/**
	 * @return the inputParameters
	 */
	public List<String> getInputParameters() {
		return inputParameters;
	}

	/**
	 * @param inputParameters the inputParameters to set
	 */
	public void setInputParameters(List<String> inputParameters) {
		this.inputParameters = inputParameters;
	}

	/**
	 * @return the global workflow variables
	 */
	public Map<String, Object> getVariables() {
		return variables;
	}
	/**
	 * @param vars the set of global workflow variables to set
	 */
	public void setVariables(Map<String, Object> vars) {
		this.variables = vars;
	}

	/**
	 * @return the outputParameters
	 */
	public Map<String, Object> getOutputParameters() {
		return outputParameters;
	}

	/**
	 * @param outputParameters the outputParameters to set
	 */
	public void setOutputParameters(Map<String, Object> outputParameters) {
		this.outputParameters = outputParameters;
	}

	/**
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * @return the failureWorkflow
	 */
	public String getFailureWorkflow() {
		return failureWorkflow;
	}

	/**
	 * @param failureWorkflow the failureWorkflow to set
	 */
	public void setFailureWorkflow(String failureWorkflow) {
		this.failureWorkflow = failureWorkflow;
	}

	/**
	 * @param version the version to set
	 */
	public void setVersion(int version) {
		this.version = version;
	}

	/**
	 * This method determines if the workflow is restartable or not
	 *
	 * @return true: if the workflow is restartable
	 * false: if the workflow is non restartable
	 */
	public boolean isRestartable() {
		return restartable;
	}

	/**
	 * This method is called only when the workflow definition is created
	 *
	 * @param restartable true: if the workflow is restartable
	 *                    false: if the workflow is non restartable
	 */
	public void setRestartable(boolean restartable) {
		this.restartable = restartable;
	}

	/**
	 * @return the schemaVersion
	 */
	public int getSchemaVersion() {
		return schemaVersion;
	}

	/**
	 * @param schemaVersion the schemaVersion to set
	 */
	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	/**
	 *
	 * @return true is workflow listener will be invoked when workflow gets into a terminal state
	 */
	public boolean isWorkflowStatusListenerEnabled() {
		return workflowStatusListenerEnabled;
	}

	/**
	 * Specify if workflow listener is enabled to invoke a callback for completed or terminated workflows
	 * @param workflowStatusListenerEnabled
	 */
	public void setWorkflowStatusListenerEnabled(boolean workflowStatusListenerEnabled) {
		this.workflowStatusListenerEnabled = workflowStatusListenerEnabled;
	}

	/**
	 * @return the email of the owner of this workflow definition
	 */
	public String getOwnerEmail() {
		return ownerEmail;
	}

	/**
	 * @param ownerEmail the owner email to set
	 */
	public void setOwnerEmail(String ownerEmail) {
		this.ownerEmail = ownerEmail;
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
	 * @return the time after which a workflow is deemed to have timed out
	 */
	public long getTimeoutSeconds() {
		return timeoutSeconds;
	}

	/**
	 * @param timeoutSeconds the timeout in seconds to set
	 */
	public void setTimeoutSeconds(long timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public String key(){
		return getKey(name, version);
	}

	public static String getKey(String name,  int version){
		return name + "." + version;
	}

	public WorkflowTask getNextTask(String taskReferenceName){
		Iterator<WorkflowTask> it = tasks.iterator();
		while(it.hasNext()){
			 WorkflowTask task = it.next();
			 WorkflowTask nextTask = task.next(taskReferenceName, null);
			 if(nextTask != null){
				 return nextTask;
			 } else if (TaskType.DO_WHILE.name().equals(task.getType()) && !task.getTaskReferenceName().equals(taskReferenceName) && task.has(taskReferenceName)) {
			 	// If the task is child of Loop Task and at last position, return null.
			 	return null;
			 }

			 if(task.getTaskReferenceName().equals(taskReferenceName) || task.has(taskReferenceName)){
				 break;
			 }
		}
		if(it.hasNext()){
			return it.next();
		}
		return null;
	}

	public WorkflowTask getTaskByRefName(String taskReferenceName){
		return collectTasks().stream()
				.filter(workflowTask -> workflowTask.getTaskReferenceName().equals(taskReferenceName))
				.findFirst()
				.orElse(null);
	}

	public List<WorkflowTask> collectTasks() {
		List<WorkflowTask> tasks = new LinkedList<>();
		for (WorkflowTask workflowTask : this.tasks) {
			tasks.addAll(workflowTask.collectTasks());
		}
		return tasks;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		WorkflowDef that = (WorkflowDef) o;
		return getVersion() == that.getVersion() &&
			getSchemaVersion() == that.getSchemaVersion() &&
			Objects.equals(getName(), that.getName()) &&
			Objects.equals(getDescription(), that.getDescription()) &&
			Objects.equals(getTasks(), that.getTasks()) &&
			Objects.equals(getInputParameters(), that.getInputParameters()) &&
			Objects.equals(getOutputParameters(), that.getOutputParameters()) &&
			Objects.equals(getVariables(), that.getVariables()) &&
			Objects.equals(getFailureWorkflow(), that.getFailureWorkflow()) &&
			Objects.equals(getOwnerEmail(), that.getOwnerEmail()) &&
			Objects.equals(getTimeoutSeconds(), that.getTimeoutSeconds());
	}

	@Override
	public int hashCode() {
		return Objects
			.hash(
				getName(),
				getDescription(),
				getVersion(),
				getTasks(),
				getInputParameters(),
				getOutputParameters(),
				getVariables(),
				getFailureWorkflow(),
				getSchemaVersion(),
				getOwnerEmail(),
				getTimeoutSeconds()
			);
	}

	@Override
	public String toString() {
		return "WorkflowDef{" +
			"name='" + name + '\'' +
			", description='" + description + '\'' +
			", version=" + version +
			", tasks=" + tasks +
			", inputParameters=" + inputParameters +
			", outputParameters=" + outputParameters +
			", variables=" + variables +
			", failureWorkflow='" + failureWorkflow + '\'' +
			", schemaVersion=" + schemaVersion +
			", restartable=" + restartable +
			", workflowStatusListenerEnabled=" + workflowStatusListenerEnabled +
			", timeoutSeconds=" + timeoutSeconds +
			'}';
	}
}
