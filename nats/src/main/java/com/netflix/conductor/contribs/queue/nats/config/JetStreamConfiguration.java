/*
 * Copyright 2023 Conductor authors.
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

import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.model.TaskModel;

import rx.Scheduler;

/**
 * @author andrey.stelmashenko@gmail.com
 */
@Configuration
@EnableConfigurationProperties(JetStreamProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.jsm.enabled", havingValue = "true")
public class JetStreamConfiguration {
    @Bean
    public EventQueueProvider jsmEventQueueProvider(
            JetStreamProperties properties, Scheduler scheduler) {
        return new JetStreamEventQueueProvider(properties, scheduler);
    }

    @ConditionalOnProperty(name = "conductor.default-event-queue.type", havingValue = "jsm")
    @Bean
    public Map<TaskModel.Status, ObservableQueue> getQueues(
            JetStreamEventQueueProvider provider,
            ConductorProperties conductorProperties,
            JetStreamProperties properties) {
        String stack = "";
        if (conductorProperties.getStack() != null && conductorProperties.getStack().length() > 0) {
            stack = conductorProperties.getStack() + "_";
        }
        TaskModel.Status[] statuses =
                new TaskModel.Status[] {TaskModel.Status.COMPLETED, TaskModel.Status.FAILED};
        Map<TaskModel.Status, ObservableQueue> queues = new EnumMap<>(TaskModel.Status.class);
        for (TaskModel.Status status : statuses) {
            String queuePrefix =
                    StringUtils.isBlank(properties.getListenerQueuePrefix())
                            ? conductorProperties.getAppId() + "_jsm_notify_" + stack
                            : properties.getListenerQueuePrefix();

            String queueName = queuePrefix + status.name() + getQueueGroup(properties);

            ObservableQueue queue = provider.getQueue(queueName);
            queues.put(status, queue);
        }

        return queues;
    }

    private String getQueueGroup(final JetStreamProperties properties) {
        if (properties.getDefaultQueueGroup() == null
                || properties.getDefaultQueueGroup().isBlank()) {
            return "";
        }
        return ":" + properties.getDefaultQueueGroup();
    }
}
