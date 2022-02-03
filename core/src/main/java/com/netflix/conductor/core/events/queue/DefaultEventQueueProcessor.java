/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.events.queue;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ModelMapper;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.exception.ApplicationException.Code;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

/**
 * Monitors and processes messages on the default event queues that Conductor listens on.
 *
 * <p>The default event queue type is controlled using the property: <code>
 * conductor.default-event-queue.type</code>
 */
@Component
@ConditionalOnProperty(
        name = "conductor.default-event-queue-processor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultEventQueueProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEventQueueProcessor.class);
    private final Map<Status, ObservableQueue> queues;
    private final WorkflowExecutor workflowExecutor;
    private final ModelMapper modelMapper;
    private static final TypeReference<Map<String, Object>> _mapType = new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    public DefaultEventQueueProcessor(
            Map<Status, ObservableQueue> queues,
            WorkflowExecutor workflowExecutor,
            ModelMapper modelMapper,
            ObjectMapper objectMapper) {
        this.queues = queues;
        this.workflowExecutor = workflowExecutor;
        this.modelMapper = modelMapper;
        this.objectMapper = objectMapper;
        queues.forEach(this::startMonitor);
        LOGGER.info(
                "DefaultEventQueueProcessor initialized with {} queues", queues.entrySet().size());
    }

    private void startMonitor(Status status, ObservableQueue queue) {

        queue.observe()
                .subscribe(
                        (Message msg) -> {
                            try {
                                LOGGER.debug("Got message {}", msg.getPayload());
                                String payload = msg.getPayload();
                                JsonNode payloadJSON = objectMapper.readTree(payload);
                                String externalId = getValue("externalId", payloadJSON);
                                if (externalId == null || "".equals(externalId)) {
                                    LOGGER.error("No external Id found in the payload {}", payload);
                                    queue.ack(Collections.singletonList(msg));
                                    return;
                                }

                                JsonNode json = objectMapper.readTree(externalId);
                                String workflowId = getValue("workflowId", json);
                                String taskRefName = getValue("taskRefName", json);
                                String taskId = getValue("taskId", json);
                                if (workflowId == null || "".equals(workflowId)) {
                                    // This is a bad message, we cannot process it
                                    LOGGER.error(
                                            "No workflow id found in the message. {}", payload);
                                    queue.ack(Collections.singletonList(msg));
                                    return;
                                }
                                WorkflowModel workflow =
                                        workflowExecutor.getWorkflow(workflowId, true);
                                Optional<TaskModel> taskOptional;
                                if (StringUtils.isNotEmpty(taskId)) {
                                    taskOptional =
                                            workflow.getTasks().stream()
                                                    .filter(
                                                            task ->
                                                                    !task.getStatus().isTerminal()
                                                                            && task.getTaskId()
                                                                                    .equals(taskId))
                                                    .findFirst();
                                } else if (StringUtils.isEmpty(taskRefName)) {
                                    LOGGER.error(
                                            "No taskRefName found in the message. If there is only one WAIT task, will mark it as completed. {}",
                                            payload);
                                    taskOptional =
                                            workflow.getTasks().stream()
                                                    .filter(
                                                            task ->
                                                                    !task.getStatus().isTerminal()
                                                                            && task.getTaskType()
                                                                                    .equals(
                                                                                            TASK_TYPE_WAIT))
                                                    .findFirst();
                                } else {
                                    taskOptional =
                                            workflow.getTasks().stream()
                                                    .filter(
                                                            task ->
                                                                    !task.getStatus().isTerminal()
                                                                            && task.getReferenceTaskName()
                                                                                    .equals(
                                                                                            taskRefName))
                                                    .findFirst();
                                }

                                if (taskOptional.isEmpty()) {
                                    LOGGER.error(
                                            "No matching tasks found to be marked as completed for workflow {}, taskRefName {}, taskId {}",
                                            workflowId,
                                            taskRefName,
                                            taskId);
                                    queue.ack(Collections.singletonList(msg));
                                    return;
                                }

                                Task task = modelMapper.getTask(taskOptional.get());
                                task.setStatus(modelMapper.mapToTaskStatus(status));
                                task.getOutputData()
                                        .putAll(objectMapper.convertValue(payloadJSON, _mapType));
                                workflowExecutor.updateTask(new TaskResult(task));

                                List<String> failures = queue.ack(Collections.singletonList(msg));
                                if (!failures.isEmpty()) {
                                    LOGGER.error("Not able to ack the messages {}", failures);
                                }
                            } catch (JsonParseException e) {
                                LOGGER.error("Bad message? : {} ", msg, e);
                                queue.ack(Collections.singletonList(msg));

                            } catch (ApplicationException e) {
                                if (e.getCode().equals(Code.NOT_FOUND)) {
                                    LOGGER.error(
                                            "Workflow ID specified is not valid for this environment");
                                    queue.ack(Collections.singletonList(msg));
                                }
                                LOGGER.error("Error processing message: {}", msg, e);
                            } catch (Exception e) {
                                LOGGER.error("Error processing message: {}", msg, e);
                            }
                        },
                        (Throwable t) -> LOGGER.error(t.getMessage(), t));
        LOGGER.info("QueueListener::STARTED...listening for " + queue.getName());
    }

    private String getValue(String fieldName, JsonNode json) {
        JsonNode node = json.findValue(fieldName);
        if (node == null) {
            return null;
        }
        return node.textValue();
    }

    public Map<String, Long> size() {
        Map<String, Long> size = new HashMap<>();
        queues.forEach((key, queue) -> size.put(queue.getName(), queue.size()));
        return size;
    }

    public Map<Status, String> queues() {
        Map<Status, String> size = new HashMap<>();
        queues.forEach((key, queue) -> size.put(key, queue.getURI()));
        return size;
    }

    public void updateByTaskRefName(
            String workflowId, String taskRefName, Map<String, Object> output, Status status)
            throws Exception {
        Map<String, Object> externalIdMap = new HashMap<>();
        externalIdMap.put("workflowId", workflowId);
        externalIdMap.put("taskRefName", taskRefName);

        update(externalIdMap, output, status);
    }

    public void updateByTaskId(
            String workflowId, String taskId, Map<String, Object> output, Status status)
            throws Exception {
        Map<String, Object> externalIdMap = new HashMap<>();
        externalIdMap.put("workflowId", workflowId);
        externalIdMap.put("taskId", taskId);

        update(externalIdMap, output, status);
    }

    private void update(
            Map<String, Object> externalIdMap, Map<String, Object> output, Status status)
            throws Exception {
        Map<String, Object> outputMap = new HashMap<>();

        outputMap.put("externalId", objectMapper.writeValueAsString(externalIdMap));
        outputMap.putAll(output);

        Message msg =
                new Message(
                        UUID.randomUUID().toString(),
                        objectMapper.writeValueAsString(outputMap),
                        null);
        ObservableQueue queue = queues.get(status);
        if (queue == null) {
            throw new IllegalArgumentException(
                    "There is no queue for handling " + status.toString() + " status");
        }
        queue.publish(Collections.singletonList(msg));
    }
}
