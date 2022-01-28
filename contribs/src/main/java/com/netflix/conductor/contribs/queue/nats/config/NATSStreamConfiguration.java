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
package com.netflix.conductor.contribs.queue.nats.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.contribs.queue.nats.NATSStreamObservableQueue;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import rx.Scheduler;

@Configuration
@EnableConfigurationProperties(NATSStreamProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.nats-stream.enabled", havingValue = "true")
public class NATSStreamConfiguration {

    @Bean
    public EventQueueProvider natsEventQueueProvider(
            NATSStreamProperties properties, Scheduler scheduler) {
        return new NATSStreamEventQueueProvider(properties, scheduler);
    }

    @ConditionalOnProperty(name = "conductor.default-event-queue.type", havingValue = "nats_stream")
    @Bean
    public Map<Task.Status, ObservableQueue> getQueues(
            ConductorProperties conductorProperties,
            NATSStreamProperties properties,
            Scheduler scheduler) {
        String stack = "";
        if (conductorProperties.getStack() != null && conductorProperties.getStack().length() > 0) {
            stack = conductorProperties.getStack() + "_";
        }
        Task.Status[] statuses = new Task.Status[] {Task.Status.COMPLETED, Task.Status.FAILED};
        Map<Task.Status, ObservableQueue> queues = new HashMap<>();
        for (Task.Status status : statuses) {
            String queuePrefix =
                    StringUtils.isBlank(properties.getListenerQueuePrefix())
                            ? conductorProperties.getAppId() + "_nats_stream_notify_" + stack
                            : properties.getListenerQueuePrefix();

            String queueName = queuePrefix + status.name();

            ObservableQueue queue =
                    new NATSStreamObservableQueue(
                            properties.getClusterId(),
                            properties.getUrl(),
                            properties.getDurableName(),
                            queueName,
                            scheduler);
            queues.put(status, queue);
        }

        return queues;
    }
}
