package com.netflix.conductor.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import com.netflix.conductor.dyno.DynoShardSupplierProvider;
import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.conductor.dyno.RedisQueuesProvider;
import com.netflix.conductor.dyno.SystemPropertiesDynomiteConfiguration;
import com.netflix.conductor.jedis.InMemoryJedisProvider;
import com.netflix.conductor.jedis.LocalHostSupplierProvider;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.queues.ShardSupplier;

import redis.clients.jedis.JedisCommands;

public class LocalRedisModule extends AbstractModule {
    @Override
    protected void configure() {

        bind(DynomiteConfiguration.class).to(SystemPropertiesDynomiteConfiguration.class);
        bind(JedisCommands.class).toProvider(InMemoryJedisProvider.class);
        bind(JedisCommands.class)
                .annotatedWith(Names.named(RedisQueuesProvider.READ_CLIENT_INJECTION_NAME))
                .toProvider(InMemoryJedisProvider.class);
        bind(HostSupplier.class).toProvider(LocalHostSupplierProvider.class);
        bind(ShardSupplier.class).toProvider(DynoShardSupplierProvider.class);
    }
}
