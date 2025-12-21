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
package com.netflix.conductor.rediseq.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.rediseq.eventqueue.RedisObservableQueue;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

@Configuration
@EnableConfigurationProperties(RedisEventQueueProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.redis.enabled", havingValue = "true")
public class RedisEventQueueConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisEventQueueConfiguration.class);

    private final RedisEventQueueProperties properties;

    public RedisEventQueueConfiguration(RedisEventQueueProperties properties) {
        this.properties = properties;
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisEventQueueClient() {
        RedisURI.Builder uriBuilder =
                RedisURI.builder()
                        .withHost(properties.getHost())
                        .withPort(properties.getPort())
                        .withDatabase(properties.getDatabase())
                        .withTimeout(properties.getConnectionTimeout());

        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            uriBuilder.withPassword(properties.getPassword().toCharArray());
        }

        if (properties.isSsl()) {
            uriBuilder.withSsl(true);
        }

        RedisClient client = RedisClient.create(uriBuilder.build());
        client.setDefaultTimeout(properties.getCommandTimeout());

        LOGGER.info(
                "Created Redis client for event queue: {}:{}",
                properties.getHost(),
                properties.getPort());
        return client;
    }

    @Bean
    public EventQueueProvider redisEventQueueProvider(RedisClient redisEventQueueClient) {
        return new RedisEventQueueProvider(redisEventQueueClient, properties);
    }

    @ConditionalOnProperty(
            name = "conductor.default-event-queue.type",
            havingValue = "redis",
            matchIfMissing = false)
    @Bean
    public Map<Status, ObservableQueue> getQueues(
            ConductorProperties conductorProperties, RedisClient redisEventQueueClient) {
        try {
            LOGGER.debug(
                    "Starting to create RedisObservableQueues with properties: {}", properties);

            String stack =
                    Optional.ofNullable(conductorProperties.getStack())
                            .filter(stackName -> stackName.length() > 0)
                            .map(stackName -> stackName + "_")
                            .orElse("");

            LOGGER.debug("Using stack: {}", stack);

            Status[] statuses = new Status[] {Status.COMPLETED, Status.FAILED};
            Map<Status, ObservableQueue> queues = new HashMap<>();

            for (Status status : statuses) {
                LOGGER.debug("Processing status: {}", status);

                String queuePrefix =
                        StringUtils.isBlank(properties.getStreamPrefix())
                                ? conductorProperties.getAppId() + "_redis_notify_" + stack
                                : properties.getStreamPrefix();

                LOGGER.debug("queuePrefix: {}", queuePrefix);

                String streamName = queuePrefix + status.name();

                LOGGER.debug("streamName: {}", streamName);

                final ObservableQueue queue =
                        new RedisObservableQueue(streamName, redisEventQueueClient, properties);
                queues.put(status, queue);
            }

            LOGGER.debug("Successfully created queues: {}", queues);
            return queues;
        } catch (Exception e) {
            LOGGER.error("Failed to create RedisObservableQueues", e);
            throw new RuntimeException("Failed to getQueues on RedisEventQueueConfiguration", e);
        }
    }
}
