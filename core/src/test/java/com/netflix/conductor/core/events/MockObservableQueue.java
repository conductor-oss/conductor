/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.core.events;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import rx.Observable;

public class MockObservableQueue implements ObservableQueue {

    private final String uri;
    private final String name;
    private final String type;
    private final Set<Message> messages = new TreeSet<>(Comparator.comparing(Message::getId));

    public MockObservableQueue(String uri, String name, String type) {
        this.uri = uri;
        this.name = name;
        this.type = type;
    }

    @Override
    public Observable<Message> observe() {
        return Observable.from(messages);
    }

    public String getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getURI() {
        return uri;
    }

    @Override
    public List<String> ack(List<Message> msgs) {
        messages.removeAll(msgs);
        return msgs.stream().map(Message::getId).collect(Collectors.toList());
    }

    @Override
    public void publish(List<Message> messages) {
        this.messages.addAll(messages);
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {}

    @Override
    public long size() {
        return messages.size();
    }

    @Override
    public String toString() {
        return "MockObservableQueue [uri=" + uri + ", name=" + name + ", type=" + type + "]";
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public boolean isRunning() {
        return false;
    }
}
