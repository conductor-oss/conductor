/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.webhook.dao.memory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;

/**
 * In-memory {@link QueueDAO} for tests. Messages survive until explicitly acked; {@link #pop}
 * returns a snapshot without removing entries (mirrors real queue semantics where un-acked messages
 * are redelivered). {@link #ack} is the only way to remove a message.
 */
public class InMemoryQueueDAO implements QueueDAO {

    private final ConcurrentHashMap<String, LinkedHashSet<String>> queues =
            new ConcurrentHashMap<>();

    @Override
    public void push(String queueName, String id, long offsetTimeInSecond) {
        queues.computeIfAbsent(queueName, k -> new LinkedHashSet<>()).add(id);
    }

    @Override
    public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
        push(queueName, id, offsetTimeInSecond);
    }

    @Override
    public void push(String queueName, String id, int priority, Duration offsetTime) {
        push(queueName, id, offsetTime.getSeconds());
    }

    @Override
    public void push(String queueName, List<Message> messages) {
        messages.forEach(m -> push(queueName, m.getId(), 0L));
    }

    @Override
    public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
        LinkedHashSet<String> q = queues.computeIfAbsent(queueName, k -> new LinkedHashSet<>());
        return q.add(id);
    }

    @Override
    public boolean pushIfNotExists(
            String queueName, String id, int priority, long offsetTimeInSecond) {
        return pushIfNotExists(queueName, id, offsetTimeInSecond);
    }

    @Override
    public List<String> pop(String queueName, int count, int timeout) {
        LinkedHashSet<String> q = queues.getOrDefault(queueName, new LinkedHashSet<>());
        List<String> result = new ArrayList<>();
        for (String id : q) {
            if (result.size() >= count) break;
            result.add(id);
        }
        return result;
    }

    @Override
    public List<Message> pollMessages(String queueName, int count, int timeout) {
        return Collections.emptyList();
    }

    @Override
    public void remove(String queueName, String messageId) {
        LinkedHashSet<String> q = queues.get(queueName);
        if (q != null) q.remove(messageId);
    }

    @Override
    public int getSize(String queueName) {
        return queues.getOrDefault(queueName, new LinkedHashSet<>()).size();
    }

    @Override
    public boolean ack(String queueName, String messageId) {
        LinkedHashSet<String> q = queues.get(queueName);
        return q != null && q.remove(messageId);
    }

    @Override
    public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        return false;
    }

    @Override
    public void flush(String queueName) {
        queues.remove(queueName);
    }

    @Override
    public Map<String, Long> queuesDetail() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        return Collections.emptyMap();
    }

    @Override
    public boolean resetOffsetTime(String queueName, String id) {
        return false;
    }

    public boolean contains(String queueName, String id) {
        return queues.getOrDefault(queueName, new LinkedHashSet<>()).contains(id);
    }
}
