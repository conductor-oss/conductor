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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.redis.jedis.JedisClusterCommands;
import com.netflix.dyno.connectionpool.Host;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Protocol;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Configuration(proxyBeanMethods = false)
@Conditional(RedisClusterCondition.class)
@Slf4j
public class RedisClusterConfiguration extends RedisConfiguration {

    protected static final int DEFAULT_MAX_ATTEMPTS = 5;

    public RedisClusterConfiguration() {
        super();
    }

    @Bean
    public JedisClusterCommands getJedisClusterClient(RedisProperties properties) {
        JedisCluster unifiedJedis = createUnifiedJedis(properties);
        return new JedisClusterCommands(unifiedJedis);
    }

    @Override
    @Bean
    public JedisCluster createUnifiedJedis(RedisProperties properties) {

        GenericObjectPoolConfig<Connection> genericObjectPoolConfig =
                new GenericObjectPoolConfig<>();

        genericObjectPoolConfig.setMaxTotal(properties.getMaxConnectionsPerHost());
        genericObjectPoolConfig.setMinIdle(properties.getMinIdleConnections());
        genericObjectPoolConfig.setMaxIdle(properties.getMaxIdleConnections());

        genericObjectPoolConfig.setMinEvictableIdleDuration(
                Duration.ofMillis(properties.getMinEvictableIdleTimeMillis()));
        genericObjectPoolConfig.setTimeBetweenEvictionRuns(
                Duration.ofMillis(properties.getTimeBetweenEvictionRunsMillis()));
        genericObjectPoolConfig.setTestWhileIdle(properties.isTestWhileIdle());
        genericObjectPoolConfig.setFairness(properties.isFairness());

        ConfigurationHostSupplier hostSupplier = new ConfigurationHostSupplier(properties);
        Set<HostAndPort> hosts =
                hostSupplier.getHosts().stream()
                        .map(h -> new HostAndPort(h.getHostName(), h.getPort()))
                        .collect(Collectors.toSet());
        String password = getPassword(hostSupplier.getHosts());

        if (password != null) {
            log.info("Connecting to Redis Cluster with AUTH");
        }
        DefaultJedisClientConfig.Builder configBuilder =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(Protocol.DEFAULT_TIMEOUT)
                        .socketTimeoutMillis(Protocol.DEFAULT_TIMEOUT)
                        .password(password)
                        .ssl(properties.isSsl());
        if (properties.isSsl() && properties.isIgnoreSsl()) {
            SSLContext context = null;
            try {
                context =
                        SSLContextBuilder.create()
                                .loadTrustMaterial(
                                        (X509Certificate[] certificateChain, String authType) ->
                                                true)
                                .build();
                configBuilder =
                        configBuilder
                                .sslParameters(context.getDefaultSSLParameters())
                                .hostnameVerifier(new NoopHostnameVerifier())
                                .sslSocketFactory(context.getSocketFactory());
            } catch (Exception e) {
                log.error("Failed to init naive ssl context", e);
            }
        }
        if (isNotBlank(properties.getUser())) {
            configBuilder.user(properties.getUser());
        }

        JedisCluster clusterClient =
                new JedisCluster(
                        hosts,
                        configBuilder.build(),
                        DEFAULT_MAX_ATTEMPTS,
                        properties.getMaxTotalRetriesDuration(),
                        genericObjectPoolConfig);
        clusterClient.getClusterNodes().values().forEach(this::monitorJedisPool);
        return clusterClient;
    }

    private String getPassword(List<Host> hosts) {
        return hosts.isEmpty() ? null : hosts.getFirst().getPassword();
    }
}
