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
package com.netflix.conductor.core.execution.tasks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_EVENT;

@Component(TASK_TYPE_EVENT)
public class Event extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Event.class);
    public static final String NAME = "EVENT";

    private static final String EVENT_PRODUCED = "event_produced";

    private final ObjectMapper objectMapper;
    private final ParametersUtils parametersUtils;
    private final EventQueues eventQueues;

    public Event(
            EventQueues eventQueues, ParametersUtils parametersUtils, ObjectMapper objectMapper) {
        super(TASK_TYPE_EVENT);
        this.parametersUtils = parametersUtils;
        this.eventQueues = eventQueues;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> payload = new HashMap<>(task.getInputData());
        payload.put("workflowInstanceId", workflow.getWorkflowId());
        payload.put("workflowType", workflow.getWorkflowName());
        payload.put("workflowVersion", workflow.getWorkflowVersion());
        payload.put("correlationId", workflow.getCorrelationId());

        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.addOutput(payload);

        try {
            task.addOutput(EVENT_PRODUCED, computeQueueName(workflow, task));
        } catch (Exception e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            LOGGER.error(
                    "Error executing task: {}, workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
        }
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        try {
            String queueName = (String) task.getOutputData().get(EVENT_PRODUCED);
            ObservableQueue queue = getQueue(queueName, task.getTaskId());
            Message message = getPopulatedMessage(task);
            queue.publish(List.of(message));
            LOGGER.debug("Published message:{} to queue:{}", message.getId(), queue.getName());
            if (!isAsyncComplete(task)) {
                task.setStatus(TaskModel.Status.COMPLETED);
                return true;
            }
        } catch (JsonProcessingException jpe) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion("Error serializing JSON payload: " + jpe.getMessage());
            LOGGER.error(
                    "Error serializing JSON payload for task: {}, workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId());
        } catch (Exception e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            LOGGER.error(
                    "Error executing task: {}, workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
        }
        return false;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Message message = new Message(task.getTaskId(), null, task.getTaskId());
        String queueName = computeQueueName(workflow, task);
        ObservableQueue queue = getQueue(queueName, task.getTaskId());
        queue.ack(List.of(message));
    }

    @VisibleForTesting
    String computeQueueName(WorkflowModel workflow, TaskModel task) {
        String sinkValueRaw = (String) task.getInputData().get("sink");
        Map<String, Object> input = new HashMap<>();
        input.put("sink", sinkValueRaw);
        Map<String, Object> replaced =
                parametersUtils.getTaskInputV2(input, workflow, task.getTaskId(), null);
        String sinkValue = (String) replaced.get("sink");
        String queueName = sinkValue;

        if (sinkValue.startsWith("conductor")) {
            if ("conductor".equals(sinkValue)) {
                queueName =
                        sinkValue
                                + ":"
                                + workflow.getWorkflowName()
                                + ":"
                                + task.getReferenceTaskName();
            } else if (sinkValue.startsWith("conductor:")) {
                queueName =
                        "conductor:"
                                + workflow.getWorkflowName()
                                + ":"
                                + sinkValue.replaceAll("conductor:", "");
            } else {
                throw new IllegalStateException(
                        "Invalid / Unsupported sink specified: " + sinkValue);
            }
        }
        return queueName;
    }

    @VisibleForTesting
    ObservableQueue getQueue(String queueName, String taskId) {
        try {
            return eventQueues.getQueue(queueName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Error loading queue:"
                            + queueName
                            + ", for task:"
                            + taskId
                            + ", error: "
                            + e.getMessage());
        } catch (Exception e) {
            throw new NonTransientException("Unable to find queue name for task " + taskId);
        }
    }

    Message getPopulatedMessage(TaskModel task) throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(task.getOutputData());
        return new Message(task.getTaskId(), payloadJson, task.getTaskId());
    }
}
