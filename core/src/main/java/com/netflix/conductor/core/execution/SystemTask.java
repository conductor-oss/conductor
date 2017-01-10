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
package com.netflix.conductor.core.execution;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;



/**
 * @author Viren
 *
 */
public class SystemTask extends Task {

	private SystemTask(){}

	public static Task decisionTask(String workflowId, String taskId, String correlationId, String refName, Map<String, Object> input, String caseValue, List<String> caseOuput){
		SystemTask st = new SystemTask();
		st.setTaskType(SystemTaskType.DECISION.name());
		st.setTaskDefName(SystemTaskType.DECISION.name());		
		st.setReferenceTaskName(refName);
		st.setWorkflowInstanceId(workflowId);
		st.setCorrelationId(correlationId);
		st.setScheduledTime(System.currentTimeMillis());
		st.setEndTime(System.currentTimeMillis());
		st.getInputData().put("case", caseValue);
		st.getOutputData().put("caseOutput", caseOuput);
		st.setTaskId(taskId);
		st.setStatus(Status.IN_PROGRESS);
		return st;
	}
	
	public static Task forkTask(String workflowId, String taskId, String correlationId, String refName, Map<String, Object> input){
		SystemTask st = new SystemTask();
		st.setTaskType(SystemTaskType.FORK.name());
		st.setTaskDefName(SystemTaskType.FORK.name());		
		st.setReferenceTaskName(refName);
		st.setWorkflowInstanceId(workflowId);
		st.setCorrelationId(correlationId);
		st.setScheduledTime(System.currentTimeMillis());
		st.setEndTime(System.currentTimeMillis());
		st.setInputData(input);;
		st.setTaskId(taskId);
		st.setStatus(Status.COMPLETED);
		return st;
	}	

	public static Task forkDynamicTask(String workflowId, String taskId, String correlationId, String refName, List<WorkflowTask> dynTaskList){
		SystemTask st = new SystemTask();
		st.setTaskType(SystemTaskType.FORK.name());
		st.setTaskDefName(SystemTaskType.FORK.name());		
		st.setReferenceTaskName(refName);
		st.setWorkflowInstanceId(workflowId);
		st.setCorrelationId(correlationId);
		st.setScheduledTime(System.currentTimeMillis());
		st.setEndTime(System.currentTimeMillis()); 
		List<String> forkedTasks = dynTaskList.stream().map(t -> t.getTaskReferenceName()).collect(Collectors.toList());
		st.getInputData().put("forkedTasks", forkedTasks);
		st.getInputData().put("forkedTaskDefs", dynTaskList);	//TODO: Remove this parameter in the later releases
		st.setTaskId(taskId);
		st.setStatus(Status.COMPLETED);
		return st;
	}	
	
	public static Task JoinTask(String workflowId, String taskId, String correlationId, String refName, Map<String, Object> input){
		SystemTask st = new SystemTask();
		st.setTaskType(SystemTaskType.JOIN.name());
		st.setTaskDefName(SystemTaskType.JOIN.name());
		st.setReferenceTaskName(refName);
		st.setWorkflowInstanceId(workflowId);
		st.setCorrelationId(correlationId);
		st.setScheduledTime(System.currentTimeMillis());
		st.setEndTime(System.currentTimeMillis());
		st.setInputData(input);
		st.setTaskId(taskId);
		st.setStatus(Status.IN_PROGRESS);
		return st;
	}	
	
	public static Task subWorkflowTask(String workflowId, String taskId, String correlationId, String refName, String subWorkflowName, Integer subWorkflowVersion, Map<String, Object> workflowInput){
		SystemTask st = new SystemTask();
		st.setTaskType(SystemTaskType.SUB_WORKFLOW.name());
		st.setTaskDefName(SystemTaskType.SUB_WORKFLOW.name());
		st.setReferenceTaskName(refName);
		st.setWorkflowInstanceId(workflowId);
		st.setCorrelationId(correlationId);
		st.setScheduledTime(System.currentTimeMillis());
		st.setEndTime(System.currentTimeMillis());
		st.getInputData().put("subWorkflowName", subWorkflowName);
		st.getInputData().put("subWorkflowVersion", subWorkflowVersion);
		st.getInputData().put("workflowInput", workflowInput);
		st.setTaskId(taskId);
		st.setStatus(Status.SCHEDULED);
		return st;
	}
	
	public static Task userDefined(Workflow workflow, WorkflowTask taskToSchedule, TaskDef taskDef, int retryCount, String taskId, Map<String, Object> input) {
		String taskType = taskToSchedule.getType();
		SystemTask st = new SystemTask();
		st.setTaskType(taskType);
		st.setTaskDefName(taskToSchedule.getName());
		st.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
		st.setWorkflowInstanceId(workflow.getWorkflowId());
		st.setCorrelationId(workflow.getCorrelationId());
		st.setScheduledTime(System.currentTimeMillis());
		st.setTaskId(taskId);
		st.setInputData(input);
		st.setStatus(Status.SCHEDULED);
	    st.setRetryCount(retryCount);
	    st.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
	    
		return st;
	}

}
