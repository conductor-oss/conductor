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
package io.orkes.conductor.scheduler.service;

import java.time.Duration;
import java.util.*;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;

public class MockQueueDao implements QueueDAO {

    Map<String, Map<String, Map<String, Object>>> dummyQueues = new HashMap<>();
    Map<String, Integer> counters = new HashMap<>();

    public void clearCounter() {
        counters.clear();
    }

    public int getCounter(String apiName) {
        if (counters.containsKey(apiName)) {
            return counters.get(apiName);
        }
        return 0;
    }

    @Override
    public void push(String queueName, String id, long offsetTimeInSecond) {
        dummyQueues.putIfAbsent(queueName, new HashMap<>());
        dummyQueues.get(queueName).putIfAbsent(id, new HashMap<>());
        dummyQueues.get(queueName).get(id).put("offsetTimeInSecond", offsetTimeInSecond);
        incrementCounter("push");
    }

    private void incrementCounter(String apiName) {
        counters.put(apiName, counters.getOrDefault(apiName, 0) + 1);
    }

    @Override
    public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
        dummyQueues.putIfAbsent(queueName, new HashMap<>());
        dummyQueues.get(queueName).putIfAbsent(id, new HashMap<>());
        dummyQueues.get(queueName).get(id).put("priority", priority);
        dummyQueues.get(queueName).get(id).put("offsetTimeInSecond", offsetTimeInSecond);
        incrementCounter("pushWithPriority");
    }

    @Override
    public void push(String queueName, String id, int priority, Duration offsetTime) {
        dummyQueues.putIfAbsent(queueName, new HashMap<>());
        dummyQueues.get(queueName).putIfAbsent(id, new HashMap<>());
        dummyQueues.get(queueName).get(id).put("priority", priority);
        dummyQueues.get(queueName).get(id).put("offsetTimeInSecond", offsetTime.getSeconds());
        dummyQueues.get(queueName).get(id).put("offsetTimeInMs", offsetTime.toMillis());
        incrementCounter("pushWithPriority");
    }

    @Override
    public void push(String queueName, List<Message> messages) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
        incrementCounter("pushIfNotExists");
        dummyQueues.putIfAbsent(queueName, new HashMap<>());
        if (!dummyQueues.get(queueName).containsKey(id)) {
            dummyQueues.get(queueName).putIfAbsent(id, new HashMap<>());
            dummyQueues.get(queueName).get(id).put("offsetTimeInSecond", offsetTimeInSecond);
            return true;
        }
        return false;
    }

    @Override
    public boolean pushIfNotExists(
            String queueName, String id, int priority, long offsetTimeInSecond) {
        incrementCounter("pushIfNotExistsWithPriority");
        dummyQueues.putIfAbsent(queueName, new HashMap<>());
        if (!dummyQueues.get(queueName).containsKey(id)) {
            dummyQueues.get(queueName).putIfAbsent(id, new HashMap<>());
            dummyQueues.get(queueName).get(id).put("priority", priority);
            dummyQueues.get(queueName).get(id).put("offsetTimeInSecond", offsetTimeInSecond);
            return true;
        }
        return false;
    }

    @Override
    public List<String> pop(String queueName, int count, int timeout) {
        incrementCounter("pop-" + queueName);
        List<String> messages = new ArrayList<>();
        Set<String> keys = dummyQueues.getOrDefault(queueName, Collections.emptyMap()).keySet();
        if (keys.size() > 0) {
            messages.add(keys.iterator().next());
        }
        return messages;
    }

    @Override
    public List<Message> pollMessages(String queueName, int count, int timeout) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void remove(String queueName, String messageId) {
        Map<String, Map<String, Object>> q = dummyQueues.get(queueName);
        if (q == null) {
            return;
        }
        q.remove(messageId);
    }

    @Override
    public int getSize(String queueName) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean ack(String queueName, String messageId) {
        incrementCounter("ack");
        dummyQueues.putIfAbsent(queueName, new HashMap<>());
        if (dummyQueues.get(queueName).containsKey(messageId)) {
            dummyQueues.get(queueName).remove(messageId);
            return true;
        }
        return false;
    }

    @Override
    public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void flush(String queueName) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, Long> queuesDetail() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean resetOffsetTime(String queueName, String id) {
        throw new UnsupportedOperationException("not implemented");
    }

    public void clearData() {
        dummyQueues.clear();
    }
}
