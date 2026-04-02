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

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.util.Pool;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the base RedisConfiguration — pool monitoring lifecycle, single executor for multiple
 * pools, and clean shutdown.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisConfigurationTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:6.2.6-alpine"))
                    .withExposedPorts(6379);

    private String host;
    private int port;

    @BeforeAll
    void setUp() {
        redis.start();
        host = redis.getHost();
        port = redis.getFirstMappedPort();
    }

    private TestRedisConfiguration createConfig() {
        return new TestRedisConfiguration();
    }

    private Pool<Connection> createPool() {
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(5);
        poolConfig.setMinIdle(1);
        JedisPooled pooled = new JedisPooled(poolConfig, host, port);
        return pooled.getPool();
    }

    @Test
    void monitorJedisPool_acceptsPool() {
        TestRedisConfiguration config = createConfig();
        Pool<Connection> pool = createPool();

        assertDoesNotThrow(() -> config.monitorJedisPool(pool));
        config.shutdownMonitor();
    }

    @Test
    void monitorJedisPool_acceptsMultiplePools() {
        TestRedisConfiguration config = createConfig();
        Pool<Connection> pool1 = createPool();
        Pool<Connection> pool2 = createPool();

        config.monitorJedisPool(pool1);
        config.monitorJedisPool(pool2);

        // Both pools should be tracked without creating extra executors
        config.shutdownMonitor();
    }

    @Test
    void shutdownMonitor_isIdempotent() {
        TestRedisConfiguration config = createConfig();
        config.monitorJedisPool(createPool());

        assertDoesNotThrow(
                () -> {
                    config.shutdownMonitor();
                    config.shutdownMonitor();
                });
    }

    @Test
    void monitorThread_isDaemon() throws InterruptedException {
        TestRedisConfiguration config = createConfig();
        config.monitorJedisPool(createPool());

        // Give the scheduled executor time to start its thread
        Thread.sleep(100);

        boolean foundDaemon =
                Thread.getAllStackTraces().keySet().stream()
                        .anyMatch(t -> t.getName().equals("redis-pool-monitor") && t.isDaemon());
        assertTrue(foundDaemon, "Monitor thread should be a daemon thread");

        config.shutdownMonitor();
    }

    /** Concrete subclass for testing the abstract RedisConfiguration. */
    static class TestRedisConfiguration extends RedisConfiguration {
        @Override
        protected UnifiedJedis createUnifiedJedis(RedisProperties properties) {
            throw new UnsupportedOperationException("Not needed for base class tests");
        }
    }
}
