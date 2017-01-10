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
package com.netflix.conductor.common.metadata.workflow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.netflix.conductor.common.metadata.Auditable;

/**
 * @author Viren
 *
 */
public class WorkflowDef extends Auditable {

	private String name;
	
	private String description;
	
	private int version = 1;
	
	private LinkedList<WorkflowTask> tasks = new LinkedList<WorkflowTask>();
	
	private List<String> inputParameters = new LinkedList<String>();
	
	private Map<String, Object> outputParameters = new HashMap<>();

	private String failureWorkflow;
	
	private int schemaVersion = 1;
	
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
	public LinkedList<WorkflowTask> getTasks() {
		return tasks;
	}

	/**
	 * @param tasks the tasks to set
	 */
	public void setTasks(LinkedList<WorkflowTask> tasks) {
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
		Optional<WorkflowTask> found = all().stream().filter(wft -> wft.getTaskReferenceName().equals(taskReferenceName)).findFirst();
		if(found.isPresent()){
			return found.get();
		}
		return null;
	}
	
	public List<WorkflowTask> all(){
		List<WorkflowTask> all = new LinkedList<>();
		for(WorkflowTask wft : tasks){
			all.addAll(wft.all());
		}
		return all;
	}
}
