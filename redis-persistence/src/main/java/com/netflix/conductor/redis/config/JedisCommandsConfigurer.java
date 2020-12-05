package com.netflix.conductor.redis.config;

import com.netflix.conductor.redis.dynoqueue.ConfigurationHostSupplier;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.commands.JedisCommands;

import static com.netflix.conductor.redis.config.RedisCommonConfiguration.DEFAULT_CLIENT_INJECTION_NAME;
import static com.netflix.conductor.redis.config.RedisCommonConfiguration.READ_CLIENT_INJECTION_NAME;

abstract class JedisCommandsConfigurer {

    @Bean
    public HostSupplier hostSupplier(RedisProperties properties) {
        return new ConfigurationHostSupplier(properties);
    }

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
