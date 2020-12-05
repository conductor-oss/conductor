package com.netflix.conductor.redis.configuration;

import com.netflix.conductor.redis.config.utils.RedisProperties;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.commands.JedisCommands;

import static com.netflix.conductor.redis.configuration.RedisCommonConfiguration.DEFAULT_CLIENT_INJECTION_NAME;
import static com.netflix.conductor.redis.configuration.RedisCommonConfiguration.READ_CLIENT_INJECTION_NAME;

public abstract class JedisCommandsConfigurer {

    @Bean(name = DEFAULT_CLIENT_INJECTION_NAME)
    public JedisCommands jedisCommands(RedisProperties properties, HostSupplier hostSupplier,
                                       TokenMapSupplier tokenMapSupplier) {
        return createJedisCommands(properties, hostSupplier, tokenMapSupplier);
    }

    @Bean(name = READ_CLIENT_INJECTION_NAME)
    public JedisCommands readJedisCommands(RedisProperties properties, HostSupplier hostSupplier,
                                           TokenMapSupplier tokenMapSupplier) {
        return createJedisCommands(properties, hostSupplier, tokenMapSupplier);
    }

    protected abstract JedisCommands createJedisCommands(RedisProperties properties, HostSupplier hostSupplier,
                                                TokenMapSupplier tokenMapSupplier);
}
