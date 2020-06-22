/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dyno;

import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.redis.sharding.ShardingStrategy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.commands.JedisCommands;

public class RedisQueuesProvider implements Provider<RedisQueues> {

    public static final String READ_CLIENT_INJECTION_NAME = "DynoReadClient";

    private static final Logger logger = LoggerFactory.getLogger(RedisQueuesProvider.class);

    private final JedisCommands dynoClient;
    private final JedisCommands dynoClientRead;
    private final ShardSupplier shardSupplier;
    private final DynomiteConfiguration configuration;
    private final ShardingStrategy shardingStrategy;

    @Inject
    public RedisQueuesProvider(
            JedisCommands dynoClient,
            @Named(READ_CLIENT_INJECTION_NAME) JedisCommands dynoClientRead,
            ShardSupplier ss,
            DynomiteConfiguration config,
            ShardingStrategy shardingStrategy
    ) {
        this.dynoClient = dynoClient;
        this.dynoClientRead = dynoClientRead;
        this.shardSupplier = ss;
        this.configuration = config;
        this.shardingStrategy = shardingStrategy;
    }

    @Override
    public RedisQueues get() {
        RedisQueues queues = new RedisQueues(
                dynoClient,
                dynoClientRead,
                configuration.getQueuePrefix(),
                shardSupplier,
                60_000,
                60_000,
                shardingStrategy
        );

        logger.info("DynoQueueDAO initialized with prefix " + configuration.getQueuePrefix() + "!");

        return queues;
    }
}
