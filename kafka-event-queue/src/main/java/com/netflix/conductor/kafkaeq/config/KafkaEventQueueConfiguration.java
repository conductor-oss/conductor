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
package com.netflix.conductor.kafkaeq.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.kafkaeq.eventqueue.KafkaObservableQueue.Builder;
import com.netflix.conductor.model.TaskModel.Status;

@Configuration
@EnableConfigurationProperties(KafkaEventQueueProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.kafka.enabled", havingValue = "true")
public class KafkaEventQueueConfiguration {

    @Autowired private KafkaEventQueueProperties kafkaProperties;

    private static final Logger LOGGER =
            LoggerFactory.getLogger(KafkaEventQueueConfiguration.class);

    public KafkaEventQueueConfiguration(KafkaEventQueueProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public EventQueueProvider kafkaEventQueueProvider() {
        return new KafkaEventQueueProvider(kafkaProperties);
    }

    @ConditionalOnProperty(
            name = "conductor.default-event-queue.type",
            havingValue = "kafka",
            matchIfMissing = false)
    @Bean
    public Map<Status, ObservableQueue> getQueues(
            ConductorProperties conductorProperties, KafkaEventQueueProperties properties) {
        try {

            LOGGER.debug(
                    "Starting to create KafkaObservableQueues with properties: {}", properties);

            String stack =
                    Optional.ofNullable(conductorProperties.getStack())
                            .filter(stackName -> stackName.length() > 0)
                            .map(stackName -> stackName + "_")
                            .orElse("");

            LOGGER.debug("Using stack: {}", stack);

            Status[] statuses = new Status[] {Status.COMPLETED, Status.FAILED};
            Map<Status, ObservableQueue> queues = new HashMap<>();

            for (Status status : statuses) {
                // Log the status being processed
                LOGGER.debug("Processing status: {}", status);

                String queuePrefix =
                        StringUtils.isBlank(properties.getListenerQueuePrefix())
                                ? conductorProperties.getAppId() + "_kafka_notify_" + stack
                                : properties.getListenerQueuePrefix();

                LOGGER.debug("queuePrefix: {}", queuePrefix);

                String topicName = queuePrefix + status.name();

                LOGGER.debug("topicName: {}", topicName);

                final ObservableQueue queue = new Builder(properties).build(topicName);
                queues.put(status, queue);
            }

            LOGGER.debug("Successfully created queues: {}", queues);
            return queues;
        } catch (Exception e) {
            LOGGER.error("Failed to create KafkaObservableQueues", e);
            throw new RuntimeException("Failed to getQueues on KafkaEventQueueConfiguration", e);
        }
    }
}
