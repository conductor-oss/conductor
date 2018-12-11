package com.netflix.conductor.jedis;

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisSentinelPool;

public class RedisSentinelJedisProvider implements Provider<JedisCommands> {
    private static Logger logger = LoggerFactory.getLogger(RedisSentinelJedisProvider.class);
    private final JedisSentinelPool jedisPool;

    @Inject
    public RedisSentinelJedisProvider(HostSupplier hostSupplier, DynomiteConfiguration configuration) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxTotal(1000);

        logger.info("Starting conductor server using redis_sentinel and cluster " + configuration.getClusterName());

        Set<String> sentinels = new HashSet<>();

        for (Host host : hostSupplier.getHosts()) {
            sentinels.add(host.getHostName() + ":" + host.getPort());
        }

        jedisPool = new JedisSentinelPool(configuration.getClusterName(), sentinels, poolConfig);
    }

    @Override
    public JedisCommands get() {
        return new JedisClusterSentinel(jedisPool);
    }
}
