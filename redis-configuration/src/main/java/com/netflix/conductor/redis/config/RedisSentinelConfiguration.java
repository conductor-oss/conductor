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

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.redis.jedis.JedisCommands;
import com.netflix.conductor.redis.jedis.RetryingJedisCommands;
import com.netflix.conductor.redis.jedis.UnifiedJedisCommands;
import com.netflix.dyno.connectionpool.Host;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisSentineled;
import redis.clients.jedis.UnifiedJedis;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Configuration(proxyBeanMethods = false)
@Conditional(RedisSentinelCondition.class)
@Slf4j
public class RedisSentinelConfiguration extends RedisConfiguration {

    @Bean
    public JedisCommands getJedisCommands(
            UnifiedJedis unifiedJedis, RedisProperties redisProperties) {
        return RetryingJedisCommands.wrap(new UnifiedJedisCommands(unifiedJedis), redisProperties);
    }

    @Override
    @Bean
    protected UnifiedJedis createUnifiedJedis(RedisProperties redisProperties) {

        ConfigurationHostSupplier hostSupplier = new ConfigurationHostSupplier(redisProperties);

        // Pool config for connections to the master
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(redisProperties.getMaxConnectionsPerHost());
        poolConfig.setMaxIdle(redisProperties.getMaxIdleConnections());
        poolConfig.setMinIdle(redisProperties.getMinIdleConnections());
        poolConfig.setMinEvictableIdleDuration(
                Duration.ofMillis(redisProperties.getMinEvictableIdleTimeMillis()));
        poolConfig.setTimeBetweenEvictionRuns(
                Duration.ofMillis(redisProperties.getTimeBetweenEvictionRunsMillis()));
        poolConfig.setTestWhileIdle(redisProperties.isTestWhileIdle());
        poolConfig.setFairness(redisProperties.isFairness());

        // Sentinel nodes
        Set<HostAndPort> sentinels =
                hostSupplier.getHosts().stream()
                        .map(h -> new HostAndPort(h.getHostName(), h.getPort()))
                        .collect(Collectors.toSet());

        String password = getPassword(hostSupplier.getHosts());
        String masterName = redisProperties.getSentinelMasterName();

        log.info(
                "Starting conductor server using redis_sentinel, master={}, sentinels={}, SSL={}",
                masterName,
                sentinels,
                redisProperties.isSsl());

        JedisClientConfig masterConfig = createMasterClientConfig(redisProperties, password);
        JedisClientConfig sentinelConfig = createSentinelClientConfig(redisProperties, password);

        JedisSentineled sentineled =
                new JedisSentineled(
                        masterName, masterConfig, poolConfig, sentinels, sentinelConfig);

        return sentineled;
    }

    JedisClientConfig createMasterClientConfig(RedisProperties redisProperties, String password) {
        DefaultJedisClientConfig.Builder builder = createBaseClientConfigBuilder(redisProperties);
        builder.database(redisProperties.getDatabase());
        applyCredentials(builder, redisProperties, password);
        return builder.build();
    }

    JedisClientConfig createSentinelClientConfig(RedisProperties redisProperties, String password) {
        DefaultJedisClientConfig.Builder builder = createBaseClientConfigBuilder(redisProperties);
        applyCredentials(builder, redisProperties, password);
        return builder.build();
    }

    private DefaultJedisClientConfig.Builder createBaseClientConfigBuilder(
            RedisProperties redisProperties) {
        DefaultJedisClientConfig.Builder builder =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(30_000)
                        .socketTimeoutMillis(30_000)
                        .ssl(redisProperties.isSsl());

        if (redisProperties.isSsl() && redisProperties.isIgnoreSsl()) {
            try {
                SSLContext context =
                        SSLContextBuilder.create()
                                .loadTrustMaterial(
                                        (X509Certificate[] certificateChain, String authType) ->
                                                true)
                                .build();
                builder.sslParameters(context.getDefaultSSLParameters())
                        .hostnameVerifier(new NoopHostnameVerifier())
                        .sslSocketFactory(context.getSocketFactory());
            } catch (Exception e) {
                log.error("Failed to init naive ssl context", e);
            }
        }

        return builder;
    }

    private void applyCredentials(
            DefaultJedisClientConfig.Builder builder,
            RedisProperties redisProperties,
            String password) {
        if (isNotBlank(redisProperties.getUser())) {
            builder.user(redisProperties.getUser()).password(password);
        } else if (password != null) {
            builder.password(password);
        }
    }

    private String getPassword(java.util.List<Host> hosts) {
        return hosts.isEmpty() ? null : hosts.getFirst().getPassword();
    }
}
