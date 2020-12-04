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
package com.netflix.conductor.redis.config.utils;

import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.redis.sharding.ShardingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.commands.JedisCommands;

public class RedisQueuesProvider {

    public static final String DEFAULT_CLIENT_INJECTION_NAME = "DefaultJedisCommands";
    public static final String READ_CLIENT_INJECTION_NAME = "ReadJedisCommands";

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisQueuesProvider.class);

    private final JedisCommands jedisCommands;
    private final JedisCommands jedisCommandsRead;
    private final ShardSupplier shardSupplier;
    private final RedisProperties properties;
    private final ShardingStrategy shardingStrategy;

    public RedisQueuesProvider(JedisCommands jedisCommands, JedisCommands jedisCommandsRead, ShardSupplier shardSupplier,
                               RedisProperties properties, ShardingStrategy shardingStrategy
    ) {
        this.jedisCommands = jedisCommands;
        this.jedisCommandsRead = jedisCommandsRead;
        this.shardSupplier = shardSupplier;
        this.properties = properties;
        this.shardingStrategy = shardingStrategy;
    }

    public RedisQueues get() {
        RedisQueues queues = new RedisQueues(jedisCommands, jedisCommandsRead, properties.getQueuePrefix(), shardSupplier,
            60_000, 60_000, shardingStrategy);
        LOGGER.info("DynoQueueDAO initialized with prefix " + properties.getQueuePrefix() + "!");
        return queues;
    }
}
