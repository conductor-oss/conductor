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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.jedis.JedisSentinel;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;

import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.commands.JedisCommands;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_sentinel")
public class RedisSentinelConfiguration extends JedisCommandsConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisSentinelConfiguration.class);

    @Override
    protected JedisCommands createJedisCommands(
            RedisProperties properties,
            ConductorProperties conductorProperties,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier) {
        GenericObjectPoolConfig<?> genericObjectPoolConfig = new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setMinIdle(5);
        genericObjectPoolConfig.setMaxTotal(properties.getMaxConnectionsPerHost());
        log.info(
                "Starting conductor server using redis_sentinel and cluster "
                        + properties.getClusterName());
        Set<String> sentinels = new HashSet<>();
        for (Host host : hostSupplier.getHosts()) {
            sentinels.add(host.getHostName() + ":" + host.getPort());
        }
        return new JedisSentinel(
                new JedisSentinelPool(
                        properties.getClusterName(), sentinels, genericObjectPoolConfig));
    }
}
