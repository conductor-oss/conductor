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

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.config.ConductorProperties;

import static org.junit.jupiter.api.Assertions.*;

class RedisPropertiesTest {

    private RedisProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RedisProperties(new ConductorProperties());
    }

    // --- Defaults ---

    @Test
    void defaults_connectionPool() {
        assertEquals(10, properties.getMaxConnectionsPerHost());
        assertEquals(8, properties.getMaxIdleConnections());
        assertEquals(5, properties.getMinIdleConnections());
        assertEquals(180000, properties.getMinEvictableIdleTimeMillis());
        assertEquals(60000, properties.getTimeBetweenEvictionRunsMillis());
        assertTrue(properties.isTestWhileIdle());
        assertTrue(properties.isFairness());
        assertEquals(3, properties.getNumTestsPerEvictionRun());
    }

    @Test
    void defaults_connection() {
        assertNull(properties.getHosts());
        assertNull(properties.getUser());
        assertFalse(properties.isSsl());
        assertFalse(properties.isIgnoreSsl());
        assertEquals(0, properties.getDatabase());
    }

    @Test
    void defaults_sentinel() {
        assertEquals("mymaster", properties.getSentinelMasterName());
    }

    @Test
    void defaults_eventExecutionPersistenceTTL() {
        assertEquals(Duration.ofSeconds(60), properties.getEventExecutionPersistenceTTL());
    }

    @Test
    void defaults_replication() {
        assertEquals(1, properties.getReplicasToSync());
        assertEquals(5000, properties.getReplicaSyncWaitTime());
    }

    @Test
    void defaults_caching() {
        assertEquals(Duration.ofSeconds(60), properties.getTaskDefCacheRefreshInterval());
        assertEquals(Duration.ofSeconds(60), properties.getMetadataCacheRefreshInterval());
        assertEquals(3600, properties.getQueueCacheExpireAfterAccessSeconds());
        assertEquals(4000, properties.getQueueCacheMaxSize());
    }

    @Test
    void defaults_namespace() {
        assertNull(properties.getWorkflowNamespacePrefix());
        assertNull(properties.getQueueNamespacePrefix());
        assertNull(properties.getKeyspaceDomain());
    }

    @Test
    void defaults_cluster() {
        assertEquals("", properties.getClusterName());
        assertEquals("us-east-1", properties.getDataCenterRegion());
        assertEquals("us-east-1c", properties.getAvailabilityZone());
        assertEquals(Duration.ofMillis(10000), properties.getMaxTotalRetriesDuration());
    }

    // --- Setters ---

    @Test
    void setHosts() {
        properties.setHosts("redis1:6379:rack1;redis2:6379:rack2");
        assertEquals("redis1:6379:rack1;redis2:6379:rack2", properties.getHosts());
    }

    @Test
    void setUser() {
        properties.setUser("admin");
        assertEquals("admin", properties.getUser());
    }

    @Test
    void setSentinelMasterName() {
        properties.setSentinelMasterName("my-master");
        assertEquals("my-master", properties.getSentinelMasterName());
    }

    @Test
    void setSsl() {
        properties.setSsl(true);
        assertTrue(properties.isSsl());
        properties.setIgnoreSsl(true);
        assertTrue(properties.isIgnoreSsl());
    }

    @Test
    void setDatabase() {
        properties.setDatabase(5);
        assertEquals(5, properties.getDatabase());
    }

    @Test
    void setConnectionPool() {
        properties.setMaxConnectionsPerHost(50);
        properties.setMaxIdleConnections(20);
        properties.setMinIdleConnections(10);
        assertEquals(50, properties.getMaxConnectionsPerHost());
        assertEquals(20, properties.getMaxIdleConnections());
        assertEquals(10, properties.getMinIdleConnections());
    }

    @Test
    void setEventExecutionPersistenceTTL() {
        properties.setEventExecutionPersistenceTTL(Duration.ofSeconds(120));
        assertEquals(Duration.ofSeconds(120), properties.getEventExecutionPersistenceTTL());
    }

    // --- getQueuePrefix ---

    @Test
    void getQueuePrefix_withDomain() {
        properties.setQueueNamespacePrefix("queues");
        properties.setKeyspaceDomain("prod");
        String prefix = properties.getQueuePrefix();
        assertTrue(prefix.startsWith("queues."));
        assertTrue(prefix.endsWith(".prod"));
    }

    @Test
    void getQueuePrefix_withoutDomain() {
        properties.setQueueNamespacePrefix("queues");
        String prefix = properties.getQueuePrefix();
        assertTrue(prefix.startsWith("queues."));
        assertFalse(prefix.contains(".null"));
    }

    // --- ExecutionDAOProperties ---

    @Test
    void executionDAOProperties_defaults() {
        RedisProperties.ExecutionDAOProperties exec = properties.getExecutionProperties();
        assertNotNull(exec);
        assertEquals(10_000, exec.getWorkflowDefCacheMaxSize());
        assertEquals(1000, exec.getTaskCacheMaxSize());
        assertEquals(10, exec.getTaskCacheExpireAfterWriteSeconds());
    }

    @Test
    void executionDAOProperties_setters() {
        RedisProperties.ExecutionDAOProperties exec = new RedisProperties.ExecutionDAOProperties();
        exec.setWorkflowDefCacheMaxSize(5000);
        exec.setTaskCacheMaxSize(500);
        exec.setTaskCacheExpireAfterWriteSeconds(30);
        properties.setExecutionProperties(exec);

        assertEquals(5000, properties.getExecutionProperties().getWorkflowDefCacheMaxSize());
        assertEquals(500, properties.getExecutionProperties().getTaskCacheMaxSize());
        assertEquals(30, properties.getExecutionProperties().getTaskCacheExpireAfterWriteSeconds());
    }
}
