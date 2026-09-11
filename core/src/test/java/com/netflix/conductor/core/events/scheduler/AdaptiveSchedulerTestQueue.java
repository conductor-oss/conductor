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
package com.netflix.conductor.core.events.scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import rx.Observable;

/**
 * In-memory {@link ObservableQueue} that exposes a working {@link #poll(int, Duration)} backed by a
 * thread-safe deque. The scheduler tests use it because they need a queue that can be pre-loaded
 * with a known burst of messages and drained through the passive poll path.
 */
class AdaptiveSchedulerTestQueue implements ObservableQueue {

    private final String name;
    private final ConcurrentLinkedDeque<Message> deque = new ConcurrentLinkedDeque<>();
    private final List<String> acked = new ArrayList<>();

    AdaptiveSchedulerTestQueue(String name) {
        this.name = name;
    }

    void seed(int count) {
        for (int i = 0; i < count; i++) {
            deque.add(new Message(name + "-" + i, name + " payload", null));
        }
    }

    @Override
    public List<Message> poll(int batchSize, Duration timeout) {
        List<Message> out = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            Message m = deque.pollFirst();
            if (m == null) {
                break;
            }
            out.add(m);
        }
        return out;
    }

    @Override
    public Observable<Message> observe() {
        return Observable.empty();
    }

    @Override
    public String getType() {
        return "test";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getURI() {
        return name;
    }

    @Override
    public synchronized List<String> ack(List<Message> messages) {
        List<String> ids = new ArrayList<>();
        for (Message m : messages) {
            acked.add(m.getId());
            ids.add(m.getId());
        }
        return ids;
    }

    synchronized int ackedCount() {
        return acked.size();
    }

    @Override
    public void publish(List<Message> messages) {
        deque.addAll(messages);
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {}

    @Override
    public long size() {
        return deque.size();
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public boolean isRunning() {
        return true;
    }
}
