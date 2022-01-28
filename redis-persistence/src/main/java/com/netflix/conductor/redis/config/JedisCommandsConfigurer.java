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

import org.springframework.context.annotation.Bean;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.dynoqueue.ConfigurationHostSupplier;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;

import redis.clients.jedis.commands.JedisCommands;

import static com.netflix.conductor.redis.config.RedisCommonConfiguration.DEFAULT_CLIENT_INJECTION_NAME;
import static com.netflix.conductor.redis.config.RedisCommonConfiguration.READ_CLIENT_INJECTION_NAME;

abstract class JedisCommandsConfigurer {

    @Bean
    public HostSupplier hostSupplier(RedisProperties properties) {
        return new ConfigurationHostSupplier(properties);
    }

    @Bean(name = DEFAULT_CLIENT_INJECTION_NAME)
    public JedisCommands jedisCommands(
            RedisProperties properties,
            ConductorProperties conductorProperties,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier) {
        return createJedisCommands(properties, conductorProperties, hostSupplier, tokenMapSupplier);
    }

    @Bean(name = READ_CLIENT_INJECTION_NAME)
    public JedisCommands readJedisCommands(
            RedisProperties properties,
            ConductorProperties conductorProperties,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier) {
        return createJedisCommands(properties, conductorProperties, hostSupplier, tokenMapSupplier);
    }

    protected abstract JedisCommands createJedisCommands(
            RedisProperties properties,
            ConductorProperties conductorProperties,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier);
}
