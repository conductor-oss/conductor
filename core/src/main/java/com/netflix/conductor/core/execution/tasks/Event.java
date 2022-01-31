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

import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_EVENT;

@Component(TASK_TYPE_EVENT)
public class Event extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Event.class);
    public static final String NAME = "EVENT";

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

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            Message message = new Message(task.getTaskId(), payloadJson, task.getTaskId());
            ObservableQueue queue = getQueue(workflow, task);
            queue.publish(List.of(message));
            LOGGER.debug("Published message:{} to queue:{}", message.getId(), queue.getName());
            task.getOutputData().putAll(payload);
            task.setStatus(
                    isAsyncComplete(task)
                            ? TaskModel.Status.IN_PROGRESS
                            : TaskModel.Status.COMPLETED);
        } catch (ApplicationException ae) {
            if (ae.isRetryable()) {
                LOGGER.info(
                        "A transient backend error happened when task {} tried to publish an event.",
                        task.getTaskId());
            } else {
                task.setStatus(TaskModel.Status.FAILED);
                task.setReasonForIncompletion(ae.getMessage());
                LOGGER.error(
                        "Error executing task: {}, workflow: {}",
                        task.getTaskId(),
                        workflow.getWorkflowId(),
                        ae);
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
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Message message = new Message(task.getTaskId(), null, task.getTaskId());
        ObservableQueue queue = getQueue(workflow, task);
        queue.ack(List.of(message));
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @VisibleForTesting
    ObservableQueue getQueue(WorkflowModel workflow, TaskModel task) {
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

        task.getOutputData().put("event_produced", queueName);

        try {
            return eventQueues.getQueue(queueName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Error loading queue for name:"
                            + queueName
                            + ", sink:"
                            + sinkValue
                            + ", error: "
                            + e.getMessage());
        }
    }
}
