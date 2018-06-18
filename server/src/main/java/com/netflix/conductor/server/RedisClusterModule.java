package com.netflix.conductor.server;

import com.google.inject.AbstractModule;

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.conductor.dyno.SystemPropertiesDynomiteConfiguration;
import com.netflix.conductor.jedis.ConfigurationHostSupplierProvider;
import com.netflix.conductor.jedis.RedisClusterJedisProvider;
import com.netflix.dyno.connectionpool.HostSupplier;

import redis.clients.jedis.JedisCommands;

public class RedisClusterModule extends AbstractModule {
    @Override
    protected void configure(){
        bind(HostSupplier.class).toProvider(ConfigurationHostSupplierProvider.class);
        bind(DynomiteConfiguration.class).to(SystemPropertiesDynomiteConfiguration.class);
        bind(JedisCommands.class).toProvider(RedisClusterJedisProvider.class);
    }
}
