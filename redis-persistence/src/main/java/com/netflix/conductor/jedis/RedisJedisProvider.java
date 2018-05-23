package com.netflix.conductor.jedis;

import com.google.common.collect.Lists;

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisCommands;

public class RedisJedisProvider implements Provider<JedisCommands> {
    private static Logger logger = LoggerFactory.getLogger(RedisJedisProvider.class);

    private final HostSupplier hostSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public RedisJedisProvider(HostSupplier hostSupplier, DynomiteConfiguration configuration) {
        this.hostSupplier = hostSupplier;
        this.configuration = configuration;
    }

    @Override
    public JedisCommands get() {
        // FIXME Do we really want to ignore all additional hosts?
        Host host = Lists.newArrayList(hostSupplier.getHosts()).get(0);

        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxTotal(1000);
        logger.info("Starting conductor server using redis_cluster " + configuration.getClusterName());
        return new JedisCluster(new HostAndPort(host.getHostName(), host.getPort()), poolConfig);
    }
}
