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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.jedis.JedisStandalone;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.commands.JedisCommands;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_standalone")
public class RedisStandaloneConfiguration extends JedisCommandsConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisSentinelConfiguration.class);

    @Override
    protected JedisCommands createJedisCommands(
            RedisProperties properties,
            ConductorProperties conductorProperties,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(properties.getMaxConnectionsPerHost());
        log.info("Starting conductor server using redis_standalone.");
        Host host = hostSupplier.getHosts().get(0);
        return new JedisStandalone(new JedisPool(config, host.getHostName(), host.getPort()));
    }
}
