/*
 * Copyright 2020 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redis.config;

import java.time.Duration;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.netflix.conductor.redis.jedis.JedisCommands;
import com.netflix.conductor.redis.jedis.RetryingJedisCommands;
import com.netflix.conductor.redis.jedis.UnifiedJedisCommands;
import com.netflix.dyno.connectionpool.Host;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Configuration(proxyBeanMethods = false)
@Conditional(RedisStandaloneCondition.class)
@Component
@Slf4j
public class RedisStandaloneConfiguration extends RedisConfiguration {

    public RedisStandaloneConfiguration() {
        super();
    }

    @Bean
    public JedisCommands getJedisCommands(
            UnifiedJedis unifiedJedis, RedisProperties redisProperties) {
        return RetryingJedisCommands.wrap(new UnifiedJedisCommands(unifiedJedis), redisProperties);
    }

    @Override
    @Bean
    protected UnifiedJedis createUnifiedJedis(RedisProperties redisProperties) {

        ConfigurationHostSupplier hostSupplier = new ConfigurationHostSupplier(redisProperties);

        // Pool config
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(redisProperties.getMaxConnectionsPerHost());
        poolConfig.setMaxIdle(redisProperties.getMaxIdleConnections());
        poolConfig.setMinIdle(redisProperties.getMinIdleConnections());

        // Optional tuning
        poolConfig.setMinEvictableIdleDuration(
                Duration.ofMillis(redisProperties.getMinEvictableIdleTimeMillis()));
        poolConfig.setTimeBetweenEvictionRuns(
                Duration.ofMillis(redisProperties.getTimeBetweenEvictionRunsMillis()));
        poolConfig.setTestWhileIdle(redisProperties.isTestWhileIdle());
        poolConfig.setFairness(redisProperties.isFairness());

        log.info(
                "unified jedis starting conductor server using redis_standalone - use SSL? {} and max connections: {}",
                redisProperties.isSsl(),
                redisProperties.getMaxConnectionsPerHost());

        Host host = hostSupplier.getHosts().getFirst();
        if (host.getPassword() != null) {
            log.info("unified jedis connecting to Redis Standalone with AUTH");
        }

        HostAndPort hp = new HostAndPort(host.getHostName(), host.getPort());

        JedisClientConfig clientConfig;
        DefaultJedisClientConfig.Builder builder =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(30_000)
                        .socketTimeoutMillis(30_000)
                        .database(redisProperties.getDatabase())
                        .ssl(redisProperties.isSsl());

        if (isNotBlank(redisProperties.getUser())) {
            builder.user(redisProperties.getUser()).password(host.getPassword());
        } else if (host.getPassword() != null) {
            builder.password(host.getPassword());
        }

        clientConfig = builder.build();

        JedisPooled pooled = new JedisPooled(poolConfig, hp, clientConfig);
        monitorJedisPool(pooled.getPool());
        return pooled;
    }
}
