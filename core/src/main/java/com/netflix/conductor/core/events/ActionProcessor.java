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
package com.netflix.conductor.core.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.events.EventHandler.TaskDetails;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Viren
 * Action Processor subscribes to the Event Actions queue and processes the actions (e.g. start workflow etc)
 * <p><b>Warning:</b> This is a work in progress and may be changed in future.  Not ready for production yet.
 */
@Singleton
public class ActionProcessor {

	private static Logger logger = LoggerFactory.getLogger(ActionProcessor.class);

	private WorkflowExecutor executor;

	private MetadataService metadata;
	
	private static final ObjectMapper om = new ObjectMapper();
	
	private ParametersUtils pu = new ParametersUtils();
	
	@Inject
	public ActionProcessor(WorkflowExecutor executor, MetadataService metadata) {
		this.executor = executor;
		this.metadata = metadata;
	}

	public Map<String, Object> execute(Action action, String payload, String event, String messageId) throws Exception {

		logger.debug("Executing {}", action.getAction());
		Object jsonObj = om.readValue(payload, Object.class);
		if(action.isExpandInlineJSON()) {
			jsonObj = expand(jsonObj);
		}

		switch (action.getAction()) {
			case start_workflow:
				Map<String, Object> op = startWorkflow(action, jsonObj, event, messageId);
				return op;
			case complete_task:
				op = completeTask(action, jsonObj, action.getComplete_task(), Status.COMPLETED, event, messageId);
				return op;
			case fail_task:
				op = completeTask(action, jsonObj, action.getFail_task(), Status.FAILED, event, messageId);
				return op;
			default:
				break;
		}
		throw new UnsupportedOperationException("Action not supported " + action.getAction());

	}

	private Map<String, Object> completeTask(Action action, Object payload, TaskDetails taskDetails, Status status, String event, String messageId) {
		
		Map<String, Object> input = new HashMap<>();
		input.put("workflowId", taskDetails.getWorkflowId());
		input.put("taskRefName", taskDetails.getTaskRefName());
		input.putAll(taskDetails.getOutput());
		
		Map<String, Object> replaced = pu.replace(input, payload);
		String workflowId = "" + replaced.get("workflowId");
		String taskRefName = "" + replaced.get("taskRefName");
		Workflow found = executor.getWorkflow(workflowId, true);
		if(found == null) {
			replaced.put("error", "No workflow found with ID: " + workflowId);
			return replaced;
		}
		Task task = found.getTaskByRefName(taskRefName);
		if(task == null) {
			replaced.put("error", "No task found with reference name: " + taskRefName + ", workflowId: " + workflowId);
			return replaced;
		}
		
		task.setStatus(status);
		task.setOutputData(replaced);
		task.getOutputData().put("conductor.event.messageId", messageId);
		task.getOutputData().put("conductor.event.name", event);
		
		try {
			executor.updateTask(new TaskResult(task));
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			replaced.put("error", e.getMessage());
		}
		return replaced;
	}

	private Map<String, Object> startWorkflow(Action action, Object payload, String event, String messageId) throws Exception {
		StartWorkflow params = action.getStart_workflow();
		Map<String, Object> op = new HashMap<>();
		try {
			
			WorkflowDef def = metadata.getWorkflowDef(params.getName(), params.getVersion());
			Map<String, Object> inputParams = params.getInput();
			Map<String, Object> workflowInput = pu.replace(inputParams, payload);
			workflowInput.put("conductor.event.messageId", messageId);
			workflowInput.put("conductor.event.name", event);
			
			String id = executor.startWorkflow(def.getName(), def.getVersion(), params.getCorrelationId(), workflowInput, event);
			op.put("workflowId", id);
			
			
		}catch(Exception e) {
			logger.error(e.getMessage(), e);
			op.put("error", e.getMessage());
		}
		
		return op;
	}
	
	private Object getJson(String jsonAsString) {
		try {
			Object value =  om.readValue(jsonAsString, Object.class);
			return value;
		} catch (Exception e) {
			return jsonAsString;
		}
	}
	
	@SuppressWarnings("unchecked")
	@VisibleForTesting
	Object expand(Object input) {
		if(input instanceof List) {
			expandList((List<Object>)input);
			return input;
		}else if(input instanceof Map) {
			expandMap((Map<String, Object>)input);
			return input;
		}else if(input instanceof String) {
			return getJson((String)input);
		}else {
			return input;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void expandList(List<Object> input) {
		for(Object value : input) {
			if(value instanceof String) {
				value = getJson(value.toString());				
			} else if (value instanceof Map) {
				expandMap((Map<String, Object>) value);
			} else if (value instanceof List) {
				expandList((List<Object>)value); 
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void expandMap(Map<String, Object> input) {
		for(Map.Entry<String, Object> e: input.entrySet()) {
			Object value = e.getValue();
			if(value instanceof String) {
				value = getJson(value.toString());
				e.setValue(value);
			} else if (value instanceof Map) {
				expandMap((Map<String, Object>) value);
			} else if (value instanceof List) {
				expandList((List<Object>)value); 
			}
		}
	}

	

}
