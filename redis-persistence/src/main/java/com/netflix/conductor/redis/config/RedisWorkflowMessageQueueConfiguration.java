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
package com.netflix.conductor.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.redis.dao.RedisWorkflowMessageQueueDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registers the Redis-backed {@link WorkflowMessageQueueDAO} when both:
 *
 * <ol>
 *   <li>The WMQ feature is enabled ({@code conductor.workflow-message-queue.enabled=true})
 *   <li>A Redis data store is configured ({@code conductor.db.type} is a Redis variant)
 * </ol>
 *
 * <p>When active, this bean takes precedence over the in-memory fallback registered by {@code
 * WorkflowMessageQueueConfiguration} in the core module (via {@code @ConditionalOnMissingBean}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.workflow-message-queue.enabled", havingValue = "true")
@Conditional(AnyRedisCondition.class)
public class RedisWorkflowMessageQueueConfiguration {

    @Bean
    public WorkflowMessageQueueDAO redisWorkflowMessageQueueDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties,
            WorkflowMessageQueueProperties wmqProperties) {
        return new RedisWorkflowMessageQueueDAO(
                jedisProxy, objectMapper, conductorProperties, redisProperties, wmqProperties);
    }
}
