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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Viren
 *
 */
public class WorkflowTask {

	public static enum Type {
		SIMPLE, DYNAMIC, FORK_JOIN, FORK_JOIN_DYNAMIC, DECISION, JOIN, SUB_WORKFLOW, USER_DEFINED;
		
		private static Set<String> systemTasks = new HashSet<>();
		static {
			systemTasks.add(Type.SIMPLE.name());
			systemTasks.add(Type.DYNAMIC.name());
			systemTasks.add(Type.FORK_JOIN.name());
			systemTasks.add(Type.FORK_JOIN_DYNAMIC.name());
			systemTasks.add(Type.DECISION.name());
			systemTasks.add(Type.JOIN.name());
			systemTasks.add(Type.SUB_WORKFLOW.name());
			//Do NOT add USER_DEFINED here...
		}
		
		public static boolean is(String name) {
			return systemTasks.contains(name);
		}
	}
	
	private String name;
	
	private String taskReferenceName;
	
	//Key: Name of the input parameter.  MUST be one of the keys defined in TaskDef (e.g. fileName)
	//Value: mapping of the parameter from another task (e.g. task1.someOutputParameterAsFileName)
	private Map<String, Object> inputParameters = new HashMap<String, Object>();

	private String type = Type.SIMPLE.name();

	private String dynamicTaskNameParam;
	
	private String caseValueParam;
	
	//Populates for the tasks of the decision type
	private Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();
	
	@Deprecated
	private String dynamicForkJoinTasksParam;
	
	private String dynamicForkTasksParam;
	
	private String dynamicForkTasksInputParamName;
	
	private List<WorkflowTask> defaultCase = new LinkedList<>();
	
	private List<List<WorkflowTask>> forkTasks = new LinkedList<>();
	
	private int startDelay;		//No. of seconds (at-least) to wait before starting a task.

	private SubWorkflowParams subWorkflow;
	
	private List<String> joinOn = new LinkedList<>();
	
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
	 * @return the taskReferenceName
	 */
	public String getTaskReferenceName() {
		return taskReferenceName;
	}

	/**
	 * @param taskReferenceName the taskReferenceName to set
	 */
	public void setTaskReferenceName(String taskReferenceName) {
		this.taskReferenceName = taskReferenceName;
	}

	/**
	 * @return the inputParameters
	 */
	public Map<String, Object> getInputParameters() {
		return inputParameters;
	}

	/**
	 * @param inputParameters the inputParameters to set
	 */
	public void setInputParameters(Map<String, Object> inputParameters) {
		this.inputParameters = inputParameters;
	}
	
	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type.name();
	}
	
	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the decisionCases
	 */
	public Map<String, List<WorkflowTask>> getDecisionCases() {
		return decisionCases;
	}

	/**
	 * @param decisionCases the decisionCases to set
	 */
	public void setDecisionCases(Map<String, List<WorkflowTask>> decisionCases) {
		this.decisionCases = decisionCases;
	}

	
	/**
	 * @return the defaultCase
	 */
	public List<WorkflowTask> getDefaultCase() {
		return defaultCase;
	}

	/**
	 * @param defaultCase the defaultCase to set
	 */
	public void setDefaultCase(List<WorkflowTask> defaultCase) {
		this.defaultCase = defaultCase;
	}

	/**
	 * @return the forkTasks
	 */
	public List<List<WorkflowTask>> getForkTasks() {
		return forkTasks;
	}

	/**
	 * @param forkTasks the forkTasks to set
	 */
	public void setForkTasks(List<List<WorkflowTask>> forkTasks) {
		this.forkTasks = forkTasks;
	}

	
	/**
	 * @return the startDelay in seconds
	 */
	public int getStartDelay() {
		return startDelay;
	}

	/**
	 * @param startDelay the startDelay to set
	 */
	public void setStartDelay(int startDelay) {
		this.startDelay = startDelay;
	}

	
	/**
	 * @return the dynamicTaskNameParam
	 */
	public String getDynamicTaskNameParam() {
		return dynamicTaskNameParam;
	}

	/**
	 * @param dynamicTaskNameParam the dynamicTaskNameParam to set to be used by DYNAMIC tasks
	 * 
	 */
	public void setDynamicTaskNameParam(String dynamicTaskNameParam) {
		this.dynamicTaskNameParam = dynamicTaskNameParam;
	}

	
	/**
	 * @return the caseValueParam
	 */
	public String getCaseValueParam() {
		return caseValueParam;
	}

	@Deprecated
	public String getDynamicForkJoinTasksParam() {
		return dynamicForkJoinTasksParam;
	}

	@Deprecated
	public void setDynamicForkJoinTasksParam(String dynamicForkJoinTasksParam) {
		this.dynamicForkJoinTasksParam = dynamicForkJoinTasksParam;
	}
	
	public String getDynamicForkTasksParam() {
		return dynamicForkTasksParam;
	}
	
	public void setDynamicForkTasksParam(String dynamicForkTasksParam) {
		this.dynamicForkTasksParam = dynamicForkTasksParam;
	}

	public String getDynamicForkTasksInputParamName() {
		return dynamicForkTasksInputParamName;
	}
	
	public void setDynamicForkTasksInputParamName(String dynamicForkTasksInputParamName) {
		this.dynamicForkTasksInputParamName = dynamicForkTasksInputParamName;
	}

	/**
	 * @param caseValueParam the caseValueParam to set
	 */
	public void setCaseValueParam(String caseValueParam) {
		this.caseValueParam = caseValueParam;
	}

	
	/**
	 * @return the subWorkflow
	 */
	public SubWorkflowParams getSubWorkflowParam() {
		return subWorkflow;
	}

	/**
	 * @param subWorkflow the subWorkflowParam to set
	 */
	public void setSubWorkflowParam(SubWorkflowParams subWorkflow) {
		this.subWorkflow = subWorkflow;
	}

	/**
	 * @return the joinOn
	 */
	public List<String> getJoinOn() {
		return joinOn;
	}

	/**
	 * @param joinOn the joinOn to set
	 */
	public void setJoinOn(List<String> joinOn) {
		this.joinOn = joinOn;
	}

	private Collection<List<WorkflowTask>> children(){
		Collection<List<WorkflowTask>> v1 = new LinkedList<>();
		Type tt = Type.USER_DEFINED;
		if(Type.is(type)) {
			tt = Type.valueOf(type);
		}
		
		switch(tt){
			case DECISION:
				v1.addAll(decisionCases.values());
				v1.add(defaultCase);
				break;
			case FORK_JOIN:
				v1.addAll(forkTasks);
				break;
			default:
				break;
		}
		return v1;
		
	}
	
	public List<WorkflowTask> all(){
		List<WorkflowTask> all = new LinkedList<>();
		all.add(this);
		for (List<WorkflowTask> wfts : children() ){
			for(WorkflowTask wft : wfts){
				all.addAll(wft.all());
			}
		}
		return all;
	}
	
	public WorkflowTask next(String taskReferenceName, WorkflowTask parent){
		Type tt = Type.USER_DEFINED;
		if(Type.is(type)) {
			tt = Type.valueOf(type);
		}
		
		switch(tt){
			case DECISION:
				for (List<WorkflowTask> wfts : children() ){
					Iterator<WorkflowTask> it = wfts.iterator();
					while(it.hasNext()){
						WorkflowTask task = it.next();
						if(task.getTaskReferenceName().equals(taskReferenceName)){
							break;
						}
						WorkflowTask nextTask = task.next(taskReferenceName, this);
						if(nextTask != null){
							return nextTask;
						}
						if(task.has(taskReferenceName)){
							break;
						}
					}
					if(it.hasNext()) { return it.next(); }
				}
				break;
			case FORK_JOIN:
				boolean found = false;
				for (List<WorkflowTask> wfts : children() ){
					Iterator<WorkflowTask> it = wfts.iterator();
					while(it.hasNext()){
						WorkflowTask task = it.next();
						if(task.getTaskReferenceName().equals(taskReferenceName)){
							found = true;
							break;
						}
						WorkflowTask nextTask = task.next(taskReferenceName, this);
						if(nextTask != null){
							return nextTask;
						}
					}
					if(it.hasNext()) { 
						return it.next(); 
					}
					if(found && parent != null){
						return parent.next(this.taskReferenceName, parent);		//we need to return join task... -- get my sibling from my parent..
					}
				}
				break;
			case DYNAMIC:
			case SIMPLE:
				return null;
			default:
				break;
		}
		return null;
	}
	
	public boolean has(String taskReferenceName){

		if(this.getTaskReferenceName().equals(taskReferenceName)){
			return true;
		}
		
		Type tt = Type.USER_DEFINED;
		if(Type.is(type)) {
			tt = Type.valueOf(type);
		}
		
		switch(tt){
			
			case DECISION:
			case FORK_JOIN:	
				for(List<WorkflowTask> childx : children()){
					for(WorkflowTask child : childx){
						if(child.has(taskReferenceName)){
							return true;
						}	
					}
				}
				break;
			default:
				break;
		}
		
		return false;
		
	}
	
	public boolean has2(String taskReferenceName){

		if(this.getTaskReferenceName().equals(taskReferenceName)){
			return true;
		}
		Type tt = Type.USER_DEFINED;
		if(Type.is(type)) {
			tt = Type.valueOf(type);
		}
		
		switch(tt){
			
			case DECISION:
			case FORK_JOIN:	
				for(List<WorkflowTask> childx : children()){
					for(WorkflowTask child : childx){
						if(child.getTaskReferenceName().equals(taskReferenceName)){
							return true;
						}
					}
				}
				break;
			default:
				break;
		}		
		return false;
		
	}
	
	public WorkflowTask get(String taskReferenceName){

		if(this.getTaskReferenceName().equals(taskReferenceName)){
			return this;
		}
		for(List<WorkflowTask> childx : children()){
			for(WorkflowTask child : childx){
				WorkflowTask found = child.get(taskReferenceName);
				if(found != null){
					return found;
				}
			}
		}
		return null;
		
	}
	
	@Override
	public String toString() {
		return name + "/" + taskReferenceName;
	}
}
