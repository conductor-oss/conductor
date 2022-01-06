/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.jedis.JedisCluster;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.commands.JedisCommands;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_cluster")
public class RedisClusterConfiguration extends JedisCommandsConfigurer {

    @Override
    protected JedisCommands createJedisCommands(
            RedisProperties properties,
            ConductorProperties conductorProperties,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier) {
        GenericObjectPoolConfig<?> genericObjectPoolConfig = new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setMaxTotal(properties.getMaxConnectionsPerHost());
        Set<HostAndPort> hosts =
                hostSupplier.getHosts().stream()
                        .map(h -> new HostAndPort(h.getHostName(), h.getPort()))
                        .collect(Collectors.toSet());
        return new JedisCluster(
                new redis.clients.jedis.JedisCluster(hosts, genericObjectPoolConfig));
    }
}
