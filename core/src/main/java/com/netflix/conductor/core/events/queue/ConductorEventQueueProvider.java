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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.dao.QueueDAO;

import rx.Scheduler;

/**
 * Default provider for {@link com.netflix.conductor.core.events.queue.ObservableQueue} that listens
 * on the <i>conductor</i> queue prefix.
 *
 * <p><code>Set conductor.event-queues.default.enabled=false</code> to disable the default queue.
 *
 * @see ConductorObservableQueue
 */
@Component
@ConditionalOnProperty(
        name = "conductor.event-queues.default.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ConductorEventQueueProvider implements EventQueueProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConductorEventQueueProvider.class);
    private final Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
    private final QueueDAO queueDAO;
    private final ConductorProperties properties;
    private final Scheduler scheduler;

    public ConductorEventQueueProvider(
            QueueDAO queueDAO, ConductorProperties properties, Scheduler scheduler) {
        this.queueDAO = queueDAO;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public String getQueueType() {
        return "conductor";
    }

    @Override
    @NonNull
    public ObservableQueue getQueue(String queueURI) {
        return queues.computeIfAbsent(
                queueURI,
                q -> new ConductorObservableQueue(queueURI, queueDAO, properties, scheduler));
    }
}
