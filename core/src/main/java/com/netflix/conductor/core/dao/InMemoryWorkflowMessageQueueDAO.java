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
package com.netflix.conductor.core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;

/**
 * In-memory implementation of {@link WorkflowMessageQueueDAO} backed by a {@link HashMap} of
 * bounded {@link LinkedList} queues, with all access serialized via {@code synchronized}.
 *
 * <p>Used as the default DAO when no Redis-backed implementation is available (e.g. when {@code
 * conductor.db.type} is not a Redis variant). Not durable across server restarts.
 */
public class InMemoryWorkflowMessageQueueDAO implements WorkflowMessageQueueDAO {

    private final Map<String, Queue<WorkflowMessage>> queues = new HashMap<>();

    private final int maxQueueSize;

    public InMemoryWorkflowMessageQueueDAO(WorkflowMessageQueueProperties properties) {
        this.maxQueueSize = properties.getMaxQueueSize();
    }

    @Override
    public synchronized void push(String workflowId, WorkflowMessage message) {
        Queue<WorkflowMessage> queue = queues.computeIfAbsent(workflowId, k -> new LinkedList<>());
        if (queue.size() >= maxQueueSize) {
            throw new IllegalStateException(
                    "Workflow message queue for workflowId="
                            + workflowId
                            + " has reached the maximum size of "
                            + maxQueueSize);
        }
        queue.add(message);
    }

    @Override
    public synchronized List<WorkflowMessage> pop(String workflowId, int maxCount) {
        Queue<WorkflowMessage> queue = queues.get(workflowId);
        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkflowMessage> result = new ArrayList<>(maxCount);
        for (int i = 0; i < maxCount && !queue.isEmpty(); i++) {
            result.add(queue.poll());
        }
        return result;
    }

    @Override
    public synchronized long size(String workflowId) {
        Queue<WorkflowMessage> queue = queues.get(workflowId);
        return queue == null ? 0 : queue.size();
    }

    @Override
    public synchronized void delete(String workflowId) {
        queues.remove(workflowId);
    }
}
