package com.netflix.conductor.server;

import com.google.common.collect.Lists;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisCommands;

public class RedisJedisProvider implements Provider<JedisCommands> {
    private final HostSupplier hostSupplier;

    @Inject
    public RedisJedisProvider(HostSupplier hostSupplier) {
        this.hostSupplier = hostSupplier;
    }

    @Override
    public JedisCommands get() {
        // FIXME Do we really want to ignore all additional hosts?
        Host host = Lists.newArrayList(hostSupplier.getHosts()).get(0);

        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxTotal(1000);
        logger.info("Starting conductor server using redis_cluster " + dynoClusterName);
        return new JedisCluster(new HostAndPort(host.getHostName(), host.getPort()), poolConfig);
    }
}
