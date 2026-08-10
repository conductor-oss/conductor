/*
 * Copyright 2026 Conductor Authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.config.ConductorProperties;

import redis.clients.jedis.JedisClientConfig;

import static org.junit.jupiter.api.Assertions.*;

class RedisSentinelConfigurationTest {

    private RedisSentinelConfiguration configuration;
    private RedisProperties properties;

    @BeforeEach
    void setUp() {
        configuration = new RedisSentinelConfiguration();
        properties = new RedisProperties(new ConductorProperties());
    }

    @Test
    void createMasterClientConfig_setsAuthAndDatabase() {
        properties.setUser("sentinel-user");
        properties.setDatabase(7);

        JedisClientConfig config = configuration.createMasterClientConfig(properties, "secret");

        assertEquals("sentinel-user", config.getUser());
        assertEquals("secret", config.getPassword());
        assertEquals(7, config.getDatabase());
    }

    @Test
    void createSentinelClientConfig_preservesLegacySentinelAuth() {
        properties.setUser("sentinel-user");

        JedisClientConfig config = configuration.createSentinelClientConfig(properties, "secret");

        assertEquals("sentinel-user", config.getUser());
        assertEquals("secret", config.getPassword());
    }

    @Test
    void createSentinelClientConfig_honorsSslSettings() {
        properties.setSsl(true);
        properties.setIgnoreSsl(true);

        JedisClientConfig config = configuration.createSentinelClientConfig(properties, "secret");

        assertTrue(config.isSsl());
        assertNotNull(config.getSslSocketFactory());
        assertNotNull(config.getHostnameVerifier());
    }
}
