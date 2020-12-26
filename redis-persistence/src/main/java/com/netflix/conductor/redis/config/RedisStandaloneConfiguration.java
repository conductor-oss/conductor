package com.netflix.conductor.redis.config;


import com.netflix.conductor.redis.jedis.JedisStandalone;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.commands.JedisCommands;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "db", havingValue = "redis_standalone")
public class RedisStandaloneConfiguration extends JedisCommandsConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisSentinelConfiguration.class);

    @Override
    protected JedisCommands createJedisCommands(RedisProperties properties, HostSupplier hostSupplier, TokenMapSupplier tokenMapSupplier) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(properties.getMaxConnectionsPerHost());
        log.warn("Starting conductor server using redis_standalone.");
        Host host = hostSupplier.getHosts().get(0);
        return new JedisStandalone(new JedisPool(config, host.getHostName(), host.getPort()));
    }
}
