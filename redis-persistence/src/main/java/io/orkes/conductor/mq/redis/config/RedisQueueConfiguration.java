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
package io.orkes.conductor.mq.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisCommands;

import io.orkes.conductor.mq.dao.ClusteredRedisQueueDAO;
import io.orkes.conductor.mq.dao.RedisQueueDAO;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisQueueConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "conductor.queue.type", havingValue = "redis_standalone")
    public QueueDAO getQueueDAO(
            RedisProperties redisProperties,
            ConductorProperties properties,
            JedisCommands jedisCommands) {
        return new RedisQueueDAO(jedisCommands, redisProperties, properties);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "conductor.queue.type", havingValue = "redis_sentinel")
    public QueueDAO getSentinelQueueDAO(
            RedisProperties redisProperties,
            ConductorProperties properties,
            JedisCommands jedisCommands) {
        return new RedisQueueDAO(jedisCommands, redisProperties, properties);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "conductor.queue.type", havingValue = "redis_cluster")
    public QueueDAO getClusterQueueDAO(
            RedisProperties redisProperties,
            ConductorProperties properties,
            JedisCommands jedisCommands) {
        return new ClusteredRedisQueueDAO(jedisCommands, redisProperties, properties);
    }
}
