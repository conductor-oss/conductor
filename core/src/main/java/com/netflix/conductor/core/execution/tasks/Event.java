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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.conductor.core.execution.ApplicationException.Code.INTERNAL_ERROR;

/**
 * @author Viren
 *
 */
public class Event extends WorkflowSystemTask {

	private static final Logger logger = LoggerFactory.getLogger(Event.class);
	public static final String NAME = "EVENT";

	private final ObjectMapper objectMapper;
	private final ParametersUtils parametersUtils;
	private final EventQueues eventQueues;
	private boolean isAsync=false;

	@Inject
	public Event(EventQueues eventQueues, ParametersUtils parametersUtils, ObjectMapper objectMapper) {
		super(NAME);
		this.parametersUtils = parametersUtils;
		this.eventQueues = eventQueues;
		this.objectMapper = objectMapper;
	}

	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor provider) {

		Map<String, Object> payload = new HashMap<>(task.getInputData());
		payload.put("workflowInstanceId", workflow.getWorkflowId());
		payload.put("workflowType", workflow.getWorkflowName());
		payload.put("workflowVersion", workflow.getWorkflowVersion());
		payload.put("correlationId", workflow.getCorrelationId());

		String payloadJson;
		try {
			payloadJson = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			String msg = String.format("Error serializing JSON payload for task: %s, workflow: %s", task.getTaskId(), workflow.getWorkflowId());
			throw new ApplicationException(INTERNAL_ERROR, msg);
		}
		Message message = new Message(task.getTaskId(), payloadJson, task.getTaskId());
		ObservableQueue queue = getQueue(workflow, task);
		if(queue != null) {
			queue.publish(Collections.singletonList(message));
			logger.debug("Published message:{} to queue:{}", message.getId(), queue.getName());
			task.getOutputData().putAll(payload);
			if (isAsyncComplete(task)) {
				task.setStatus(Status.IN_PROGRESS);
			} else {
				task.setStatus(Status.COMPLETED);
			}
		} else {
			task.setReasonForIncompletion("No queue found to publish.");
			task.setStatus(Status.FAILED);
		}
	}

	@Override
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {
		return false;
	}

	@Override
	public void cancel(Workflow workflow, Task task, WorkflowExecutor provider) {
		Message message = new Message(task.getTaskId(), null, task.getTaskId());
		getQueue(workflow, task).ack(Collections.singletonList(message));
	}

	@VisibleForTesting
	ObservableQueue getQueue(Workflow workflow, Task task) {
		if (task.getInputData().get("sink") == null) {
			task.setStatus(Status.FAILED);
			task.setReasonForIncompletion("No sink specified in task");
			return null;
		}

		String sinkValueRaw = (String)task.getInputData().get("sink");
		Map<String, Object> input = new HashMap<>();
		input.put("sink", sinkValueRaw);
		Map<String, Object> replaced = parametersUtils.getTaskInputV2(input, workflow, task.getTaskId(), null);
		String sinkValue = (String)replaced.get("sink");
		String queueName = sinkValue;

		if(sinkValue.startsWith("conductor")) {
			if("conductor".equals(sinkValue)) {
				queueName = sinkValue + ":" + workflow.getWorkflowName() + ":" + task.getReferenceTaskName();
			} else if(sinkValue.startsWith("conductor:")) {
				queueName = sinkValue.replaceAll("conductor:", "");
				queueName = "conductor:" + workflow.getWorkflowName() + ":" + queueName;
			} else {
				task.setStatus(Status.FAILED);
				task.setReasonForIncompletion("Invalid / Unsupported sink specified: " + sinkValue);
				return null;
			}
		}
		task.getOutputData().put("event_produced", queueName);

		try {
			return eventQueues.getQueue(queueName);
		} catch(IllegalArgumentException e) {
			logger.error("Error setting up queue: {} for task:{}, workflow:{}", queueName, task.getTaskId(), workflow.getWorkflowId(), e);
			task.setStatus(Status.FAILED);
			task.setReasonForIncompletion("Error when trying to access the specified queue/topic: " + sinkValue + ", error: " + e.getMessage());
			return null;
		}
	}

	@Override
	public boolean isAsync() {
		return this.isAsync;
	}
}
