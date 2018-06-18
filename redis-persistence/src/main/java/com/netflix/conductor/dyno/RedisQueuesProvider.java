package com.netflix.conductor.dyno;

import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class RedisQueuesProvider implements Provider<RedisQueues> {

    public static final String READ_CLIENT_INJECTION_NAME = "DynoReadClient";

    private static final Logger logger = LoggerFactory.getLogger(RedisQueuesProvider.class);

    private final JedisCommands dynoClient;
    private final JedisCommands dynoClientRead;
    private final ShardSupplier shardSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public RedisQueuesProvider(
            JedisCommands dynoClient,
            @Named(READ_CLIENT_INJECTION_NAME) JedisCommands dynoClientRead,
            ShardSupplier ss,
            DynomiteConfiguration config
    ) {
        this.dynoClient = dynoClient;
        this.dynoClientRead = dynoClientRead;
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
