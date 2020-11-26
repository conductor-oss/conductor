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
package com.netflix.conductor.redis.config.inmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.redis.config.utils.DynoShardSupplierProvider;
import com.netflix.conductor.redis.config.utils.JedisProxy;
import com.netflix.conductor.redis.config.utils.RedisProperties;
import com.netflix.conductor.redis.config.utils.RedisQueuesProvider;
import com.netflix.conductor.redis.config.utils.RedisQueuesShardingStrategyProvider;
import com.netflix.conductor.redis.dao.DynoQueueDAO;
import com.netflix.conductor.redis.dao.RedisEventHandlerDAO;
import com.netflix.conductor.redis.dao.RedisExecutionDAO;
import com.netflix.conductor.redis.dao.RedisMetadataDAO;
import com.netflix.conductor.redis.dao.RedisPollDataDAO;
import com.netflix.conductor.redis.dao.RedisRateLimitingDAO;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.LocalHostSupplierProvider;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.redis.sharding.ShardingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.commands.JedisCommands;

import static com.netflix.conductor.redis.config.utils.RedisQueuesProvider.DEFAULT_CLIENT_INJECTION_NAME;
import static com.netflix.conductor.redis.config.utils.RedisQueuesProvider.READ_CLIENT_INJECTION_NAME;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "db", havingValue = "memory", matchIfMissing = true)
public class InMemoryRedisConfiguration {

    @Bean
    public HostSupplier hostSupplier(RedisProperties properties) {
        return new LocalHostSupplierProvider(properties).get();
    }

    @Bean
    public ShardSupplier shardSupplier(HostSupplier hostSupplier, RedisProperties properties) {
        return new DynoShardSupplierProvider(hostSupplier, properties).get();
    }

    @Bean
    public JedisMock jedisMock() {
        return new JedisMock();
    }

    @Bean(name = DEFAULT_CLIENT_INJECTION_NAME)
    public JedisCommands jedisCommands(JedisMock jedisMock) {
        return jedisMock;
    }

    @Bean(name = READ_CLIENT_INJECTION_NAME)
    public JedisCommands readJedisCommands(JedisMock jedisMock) {
        return jedisMock;
    }

    @Bean
    public ShardingStrategy shardingStrategy(ShardSupplier shardSupplier, RedisProperties properties) {
        return new RedisQueuesShardingStrategyProvider(shardSupplier, properties).get();
    }

    @Bean
    public RedisQueues redisQueues(@Qualifier(DEFAULT_CLIENT_INJECTION_NAME) JedisCommands dynoClient,
        @Qualifier(READ_CLIENT_INJECTION_NAME) JedisCommands dynoClientRead,
        ShardSupplier shardSupplier, RedisProperties properties, ShardingStrategy shardingStrategy) {
        return new RedisQueuesProvider(dynoClient, dynoClientRead, shardSupplier, properties, shardingStrategy).get();
    }

    @Bean
    public MetadataDAO redisMetadataDAO(JedisProxy jedisProxy, ObjectMapper objectMapper, RedisProperties properties) {
        return new RedisMetadataDAO(jedisProxy, objectMapper, properties);
    }

    @Bean
    public ExecutionDAO redisExecutionDAO(JedisProxy jedisProxy, ObjectMapper objectMapper,
                                          RedisProperties properties) {
        return new RedisExecutionDAO(jedisProxy, objectMapper, properties);
    }

    @Bean
    public EventHandlerDAO eventHandlerDAO(JedisProxy jedisProxy, ObjectMapper objectMapper,
                                           RedisProperties properties) {
        return new RedisEventHandlerDAO(jedisProxy, objectMapper, properties);
    }

    @Bean
    public RateLimitingDAO rateLimitingDAO(JedisProxy jedisProxy, ObjectMapper objectMapper,
                                           RedisProperties properties) {
        return new RedisRateLimitingDAO(jedisProxy, objectMapper, properties);
    }

    @Bean
    public PollDataDAO pollDataDAO(JedisProxy jedisProxy, ObjectMapper objectMapper, RedisProperties properties) {
        return new RedisPollDataDAO(jedisProxy, objectMapper, properties);
    }

    @Bean
    public QueueDAO queueDAO(RedisQueues redisQueues) {
        return new DynoQueueDAO(redisQueues);
    }
}
