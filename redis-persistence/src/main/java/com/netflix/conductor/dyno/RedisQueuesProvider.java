package com.netflix.conductor.dyno;

import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class RedisQueuesProvider implements Provider<RedisQueues> {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueuesProvider.class);

    private final JedisCommands dynoClient;
    private final JedisCommands dynoClientRead;
    private final ShardSupplier shardSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public RedisQueuesProvider(
            JedisCommands dynoClient,
            JedisCommands dynoClientRead,
            ShardSupplier ss,
            DynomiteConfiguration config
    ) {
        this.dynoClient = dynoClient;
        // FIXME: This was in the original code, but seems like a bug?
        this.dynoClientRead = dynoClient;
        this.shardSupplier = ss;
        this.configuration = config;
    }

    @Override
    public RedisQueues get() {
        RedisQueues queues = new RedisQueues(
                dynoClient,
                dynoClientRead,
                configuration.getQueuePrefix(),
                shardSupplier,
                60_000,
                60_000
        );

        logger.info("DynoQueueDAO initialized with prefix " + configuration.getQueuePrefix() + "!");

        return queues;
    }
}
