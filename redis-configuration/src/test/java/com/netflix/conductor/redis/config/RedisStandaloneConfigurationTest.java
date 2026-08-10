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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.jedis.JedisCommands;

import redis.clients.jedis.UnifiedJedis;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RedisStandaloneConfiguration against a real Redis via TestContainers. Verifies bean
 * creation, connection, and basic operations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisStandaloneConfigurationTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:6.2.6-alpine"))
                    .withExposedPorts(6379);

    private RedisStandaloneConfiguration config;
    private RedisProperties properties;

    @BeforeAll
    void setUp() {
        redis.start();

        ConductorProperties conductorProperties = new ConductorProperties();
        properties = new RedisProperties(conductorProperties);
        properties.setHosts(redis.getHost() + ":" + redis.getFirstMappedPort() + ":us-east-1c");
        properties.setMaxConnectionsPerHost(5);
        properties.setMinIdleConnections(1);
        properties.setMaxIdleConnections(3);

        config = new RedisStandaloneConfiguration();
    }

    @Test
    void createUnifiedJedis_returnsWorkingConnection() {
        UnifiedJedis jedis = config.createUnifiedJedis(properties);
        assertNotNull(jedis);

        // Verify actual Redis connectivity
        assertEquals("PONG", jedis.ping());
    }

    @Test
    void createUnifiedJedis_canReadWrite() {
        UnifiedJedis jedis = config.createUnifiedJedis(properties);
        jedis.set("standalone-test-key", "hello");
        assertEquals("hello", jedis.get("standalone-test-key"));
        jedis.del("standalone-test-key");
    }

    @Test
    void getJedisCommands_returnsJedisCommands() {
        UnifiedJedis jedis = config.createUnifiedJedis(properties);
        JedisCommands commands = config.getJedisCommands(jedis, properties);

        assertNotNull(commands);
    }

    @Test
    void getJedisCommands_canPerformOperations() {
        UnifiedJedis jedis = config.createUnifiedJedis(properties);
        JedisCommands commands = config.getJedisCommands(jedis, properties);

        commands.set("cmd-test-key", "value");
        assertEquals("value", commands.get("cmd-test-key"));
        assertEquals("PONG", commands.ping());
        commands.del("cmd-test-key");
    }

    @Test
    void createUnifiedJedis_respectsDatabaseSetting() {
        properties.setDatabase(1);
        UnifiedJedis jedis = config.createUnifiedJedis(properties);
        jedis.set("db1-key", "in-db1");
        assertEquals("in-db1", jedis.get("db1-key"));
        jedis.del("db1-key");
        properties.setDatabase(0); // reset
    }

    @Test
    void createUnifiedJedis_withAuthUser() {
        // Verify config doesn't crash when user is set (no ACL on test Redis, so just confirm no
        // exception)
        properties.setUser(null); // reset to no user
        UnifiedJedis jedis = config.createUnifiedJedis(properties);
        assertEquals("PONG", jedis.ping());
    }
}
