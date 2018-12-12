package com.netflix.conductor.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.conductor.dyno.DynoShardSupplierProvider;
import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.conductor.dyno.RedisQueuesProvider;
import com.netflix.conductor.dyno.SystemPropertiesDynomiteConfiguration;
import com.netflix.conductor.jedis.ConfigurationHostSupplierProvider;
import com.netflix.conductor.jedis.RedisSentinelJedisProvider;
import com.netflix.conductor.jedis.TokenMapSupplierProvider;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.queues.ShardSupplier;

import redis.clients.jedis.JedisCommands;

public class RedisSentinelModule extends AbstractModule {
    @Override
    protected void configure(){
        bind(DynomiteConfiguration.class).to(SystemPropertiesDynomiteConfiguration.class);
        bind(JedisCommands.class).toProvider(RedisSentinelJedisProvider.class).asEagerSingleton();
        bind(JedisCommands.class)
                .annotatedWith(Names.named(RedisQueuesProvider.READ_CLIENT_INJECTION_NAME))
                .toProvider(RedisSentinelJedisProvider.class)
                .asEagerSingleton();
        bind(HostSupplier.class).toProvider(ConfigurationHostSupplierProvider.class);
        bind(TokenMapSupplier.class).toProvider(TokenMapSupplierProvider.class);
        bind(ShardSupplier.class).toProvider(DynoShardSupplierProvider.class);
    }
}
