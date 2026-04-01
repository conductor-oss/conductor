/*
 * Copyright 2024 Conductor Authors.
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

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * System task that dequeues messages from the workflow's message queue.
 *
 * <p>The task stays {@code IN_PROGRESS} until at least one message is available, then atomically
 * pops up to {@code batchSize} messages and completes with the messages in the output.
 *
 * <p>Input parameters:
 *
 * <ul>
 *   <li>{@code batchSize} (int, default 1) — maximum number of messages to pull per invocation
 *   <li>{@code blocking} (boolean, default true) — when {@code false}, completes immediately with
 *       an empty list if no messages are available instead of waiting
 * </ul>
 *
 * <p>Output:
 *
 * <ul>
 *   <li>{@code messages} — list of {@link WorkflowMessage} objects
 *   <li>{@code count} — number of messages actually returned
 * </ul>
 */
@Component(PullWorkflowMessages.TASK_TYPE)
@ConditionalOnProperty(name = "conductor.workflow-message-queue.enabled", havingValue = "true")
public class PullWorkflowMessages extends WorkflowSystemTask {

    public static final String TASK_TYPE = "PULL_WORKFLOW_MESSAGES";
    static final String INPUT_BATCH_SIZE = "batchSize";
    static final String INPUT_BLOCKING = "blocking";
    static final String OUTPUT_MESSAGES = "messages";
    static final String OUTPUT_COUNT = "count";

    private final WorkflowMessageQueueDAO dao;
    private final WorkflowMessageQueueProperties properties;

    public PullWorkflowMessages(
            WorkflowMessageQueueDAO dao, WorkflowMessageQueueProperties properties) {
        super(TASK_TYPE);
        this.dao = dao;
        this.properties = properties;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.IN_PROGRESS);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        int batchSize = getBatchSize(task);
        List<WorkflowMessage> messages = dao.pop(workflow.getWorkflowId(), batchSize);
        if (messages.isEmpty()) {
            if (isBlocking(task)) {
                // No messages yet — stay IN_PROGRESS; SystemTaskWorker will re-poll
                return false;
            }
            // Non-blocking: complete immediately with empty output
        }
        task.addOutput(OUTPUT_MESSAGES, messages);
        task.addOutput(OUTPUT_COUNT, messages.size());
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        // Poll every 1 second while waiting for messages.
        return Optional.of(1L);
    }

    private boolean isBlocking(TaskModel task) {
        Object raw = task.getInputData().get(INPUT_BLOCKING);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return true; // default: blocking
    }

    private int getBatchSize(TaskModel task) {
        Object raw = task.getInputData().get(INPUT_BATCH_SIZE);
        int requested = 1;
        if (raw instanceof Number) {
            requested = ((Number) raw).intValue();
        }
        if (requested < 1) {
            requested = 1;
        }
        return Math.min(requested, properties.getMaxBatchSize());
    }
}
