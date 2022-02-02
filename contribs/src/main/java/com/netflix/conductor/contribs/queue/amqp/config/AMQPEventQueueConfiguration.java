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
package com.netflix.conductor.contribs.queue.amqp.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.contribs.queue.amqp.AMQPObservableQueue.Builder;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.model.TaskModel.Status;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AMQPEventQueueProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.amqp.enabled", havingValue = "true")
public class AMQPEventQueueConfiguration {

    private enum QUEUE_TYPE {
        AMQP_QUEUE("amqp_queue"),
        AMQP_EXCHANGE("amqp_exchange");

        private final String type;

        QUEUE_TYPE(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    @Bean
    public EventQueueProvider amqpEventQueueProvider(AMQPEventQueueProperties properties) {
        return new AMQPEventQueueProvider(properties, QUEUE_TYPE.AMQP_QUEUE.getType(), false);
    }

    @Bean
    public EventQueueProvider amqpExchangeEventQueueProvider(AMQPEventQueueProperties properties) {
        return new AMQPEventQueueProvider(properties, QUEUE_TYPE.AMQP_EXCHANGE.getType(), true);
    }

    @ConditionalOnProperty(name = "conductor.default-event-queue.type", havingValue = "amqp")
    @Bean
    public Map<Status, ObservableQueue> getQueues(
            ConductorProperties conductorProperties, AMQPEventQueueProperties properties) {
        String stack = "";
        if (conductorProperties.getStack() != null && conductorProperties.getStack().length() > 0) {
            stack = conductorProperties.getStack() + "_";
        }
        final boolean useExchange = properties.isUseExchange();

        Status[] statuses = new Status[] {Status.COMPLETED, Status.FAILED};
        Map<Status, ObservableQueue> queues = new HashMap<>();
        for (Status status : statuses) {
            String queuePrefix =
                    StringUtils.isBlank(properties.getListenerQueuePrefix())
                            ? conductorProperties.getAppId() + "_amqp_notify_" + stack
                            : properties.getListenerQueuePrefix();

            String queueName = queuePrefix + status.name();

            final ObservableQueue queue = new Builder(properties).build(useExchange, queueName);
            queues.put(status, queue);
        }

        return queues;
    }
}
