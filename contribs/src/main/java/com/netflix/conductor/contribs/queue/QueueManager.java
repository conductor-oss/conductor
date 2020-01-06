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
package com.netflix.conductor.contribs.queue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.service.ExecutionService;

/**
 * @author Viren
 *
 */
@Singleton
public class QueueManager {

	private static Logger logger = LoggerFactory.getLogger(QueueManager.class);

	private Map<Task.Status, ObservableQueue> queues;

	private ExecutionService executionService;

	private static final TypeReference<Map<String, Object>> _mapType = new TypeReference<Map<String, Object>>() {};

	private ObjectMapper objectMapper;

	@Inject
	public QueueManager(Map<Task.Status, ObservableQueue> queues, ExecutionService executionService, ObjectMapper objectMapper) {
		this.queues = queues;
		this.executionService = executionService;
		this.objectMapper = objectMapper;
		queues.entrySet().forEach(e -> {
			Status status = e.getKey();
			ObservableQueue queue = e.getValue();
			startMonitor(status, queue);
		});
	}

	private void startMonitor(Status status, ObservableQueue queue) {

		queue.observe().subscribe((Message msg) -> {

			try {

				logger.debug("Got message {}", msg.getPayload());

				String payload = msg.getPayload();
				JsonNode payloadJSON = objectMapper.readTree(payload);
				String externalId = getValue("externalId", payloadJSON);
				if(externalId == null || "".equals(externalId)) {
					logger.error("No external Id found in the payload {}", payload);
					queue.ack(Collections.singletonList(msg));
					return;
				}

				JsonNode json = objectMapper.readTree(externalId);
				String workflowId = getValue("workflowId", json);
				String taskRefName = getValue("taskRefName", json);
				String taskId = getValue("taskId", json);
				if(workflowId == null || "".equals(workflowId)) {
					//This is a bad message, we cannot process it
					logger.error("No workflow id found in the message. {}", payload);
					queue.ack(Collections.singletonList(msg));
					return;
				}
				Workflow workflow = executionService.getExecutionStatus(workflowId, true);
				Optional<Task> taskOptional;
				if (StringUtils.isNotEmpty(taskId)) {
					taskOptional = workflow.getTasks().stream().filter(task -> !task.getStatus().isTerminal() && task.getTaskId().equals(taskId)).findFirst();
				} else if(StringUtils.isEmpty(taskRefName)) {
					logger.error("No taskRefName found in the message. If there is only one WAIT task, will mark it as completed. {}", payload);
					taskOptional = workflow.getTasks().stream().filter(task -> !task.getStatus().isTerminal() && task.getTaskType().equals(Wait.NAME)).findFirst();
				} else {
					taskOptional = workflow.getTasks().stream().filter(task -> !task.getStatus().isTerminal() && task.getReferenceTaskName().equals(taskRefName)).findFirst();
				}

				if(!taskOptional.isPresent()) {
					logger.error("No matching tasks found to be marked as completed for workflow {}, taskRefName {}, taskId {}", workflowId, taskRefName, taskId);
					queue.ack(Collections.singletonList(msg));
					return;
				}

				Task task = taskOptional.get();
				task.setStatus(status);
				task.getOutputData().putAll(objectMapper.convertValue(payloadJSON, _mapType));
				executionService.updateTask(task);

				List<String> failures = queue.ack(Collections.singletonList(msg));
				if(!failures.isEmpty()) {
					logger.error("Not able to ack the messages {}", failures.toString());
				}

			} catch(JsonParseException e) {
				logger.error("Bad message? : {} ", msg, e);
				queue.ack(Collections.singletonList(msg));

			} catch(ApplicationException e) {
				if(e.getCode().equals(Code.NOT_FOUND)) {
					logger.error("Workflow ID specified is not valid for this environment");
					queue.ack(Collections.singletonList(msg));
				}
				logger.error("Error processing message: {}", msg, e);
			} catch(Exception e) {
				logger.error("Error processing message: {}", msg, e);
			}

		}, (Throwable t) -> {
			logger.error(t.getMessage(), t);
		});
		logger.info("QueueListener::STARTED...listening for " + queue.getName());
	}

	private String getValue(String fieldName, JsonNode json) {
		JsonNode node = json.findValue(fieldName);
		if(node == null) {
			return null;
		}
		return node.textValue();
	}

	public Map<String, Long> size() {
		Map<String, Long> size = new HashMap<>();
		queues.entrySet().forEach(e -> {
			ObservableQueue queue = e.getValue();
			size.put(queue.getName(), queue.size());
		});
		return size;
	}

	public Map<Status, String> queues() {
		Map<Status, String> size = new HashMap<>();
		queues.entrySet().forEach(e -> {
			ObservableQueue queue = e.getValue();
			size.put(e.getKey(), queue.getURI());
		});
		return size;
	}

	public void updateByTaskRefName(String workflowId, String taskRefName, Map<String, Object> output, Status status) throws Exception {
		Map<String, Object> externalIdMap = new HashMap<>();
		externalIdMap.put("workflowId", workflowId);
		externalIdMap.put("taskRefName", taskRefName);

		update(externalIdMap, output, status);
	}

	public void updateByTaskId(String workflowId, String taskId, Map<String, Object> output, Status status) throws Exception {
		Map<String, Object> externalIdMap = new HashMap<>();
		externalIdMap.put("workflowId", workflowId);
		externalIdMap.put("taskId", taskId);

		update(externalIdMap, output, status);
	}

	private void update(Map<String, Object> externalIdMap, Map<String, Object> output, Status status) throws Exception {
		Map<String, Object> outputMap = new HashMap<>();

		outputMap.put("externalId", objectMapper.writeValueAsString(externalIdMap));
		outputMap.putAll(output);

		Message msg = new Message(UUID.randomUUID().toString(), objectMapper.writeValueAsString(outputMap), null);
		ObservableQueue queue = queues.get(status);
		if(queue == null) {
			throw new IllegalArgumentException("There is no queue for handling " + status.toString() + " status");
		}
		queue.publish(Arrays.asList(msg));
	}
}
