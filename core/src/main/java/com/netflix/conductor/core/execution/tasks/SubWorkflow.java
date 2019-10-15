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
package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Viren
 *
 */
public class SubWorkflow extends WorkflowSystemTask {

	private static final Logger logger = LoggerFactory.getLogger(SubWorkflow.class);
	public static final String NAME = "SUB_WORKFLOW";
	public static final String SUB_WORKFLOW_ID = "subWorkflowId";

	public SubWorkflow() {
		super(NAME);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor provider) {

		Map<String, Object> input = task.getInputData();
		String name = input.get("subWorkflowName").toString();
		int version = (int) input.get("subWorkflowVersion");
		Map taskToDomain = workflow.getTaskToDomain();
		if (input.get("subWorkflowTaskToDomain") instanceof Map) {
			taskToDomain = (Map) input.get("subWorkflowTaskToDomain");
		}
		Map<String, Object> wfInput = (Map<String, Object>) input.get("workflowInput");
		if (wfInput == null || wfInput.isEmpty()) {
			wfInput = input;
		}
		String correlationId = workflow.getCorrelationId();

		try {
			String subWorkflowId = provider.startWorkflow(name, version, wfInput, null, correlationId, workflow.getWorkflowId(), task.getTaskId(), null, taskToDomain);
			task.getOutputData().put(SUB_WORKFLOW_ID, subWorkflowId);
			task.getInputData().put(SUB_WORKFLOW_ID, subWorkflowId);
			task.setStatus(Status.IN_PROGRESS);
		} catch (Exception e) {
			task.setStatus(Status.FAILED);
			task.setReasonForIncompletion(e.getMessage());
			logger.error("Error starting sub workflow: {} from workflow: {}", name, workflow.getWorkflowId(), e);
		}
	}

	@Override
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {
		String workflowId = getSubWorkflowId(task);
		if(StringUtils.isEmpty(workflowId)) {
			return false;
		}

		Workflow subWorkflow = provider.getWorkflow(workflowId, false);
		WorkflowStatus subWorkflowStatus = subWorkflow.getStatus();
		if(!subWorkflowStatus.isTerminal()){
			return false;
		}
		if (subWorkflow.getExternalOutputPayloadStoragePath() != null) {
			task.setExternalOutputPayloadStoragePath(subWorkflow.getExternalOutputPayloadStoragePath());
		} else {
			task.getOutputData().putAll(subWorkflow.getOutput());
		}
		if (subWorkflowStatus.isSuccessful()) {
			task.setStatus(Status.COMPLETED);
		} else {
			task.setReasonForIncompletion(subWorkflow.getReasonForIncompletion());
			task.setStatus(Status.FAILED);
		}
		return true;
	}

	@Override
	public void cancel(Workflow workflow, Task task, WorkflowExecutor provider) {
		String workflowId = getSubWorkflowId(task);
		if(StringUtils.isEmpty(workflowId)) {
			return;
		}
		Workflow subWorkflow = provider.getWorkflow(workflowId, true);
		subWorkflow.setStatus(WorkflowStatus.TERMINATED);
		provider.terminateWorkflow(subWorkflow, "Parent workflow has been terminated with status " + workflow.getStatus(), null);
	}

	@Override
	public boolean isAsync() {
		return false;
	}

	private String getSubWorkflowId(Task task) {
		String subWorkflowId = (String) task.getOutputData().get(SUB_WORKFLOW_ID);
		if (subWorkflowId == null) {
			subWorkflowId = Optional.ofNullable(task.getInputData())
				.map(data -> (String)data.get(SUB_WORKFLOW_ID)) //Backward compatibility
				.orElse("");
		}
		return subWorkflowId;
	}
}
