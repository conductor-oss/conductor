/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.os.config;

import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.*;

public class OpenSearchPropertiesTest {

    // =========================================================================
    // Test Cluster 1: Backward Compatibility
    // =========================================================================

    @Test
    public void testLegacyUrlPropertyFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.url", "http://legacy:9200");

        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(env);
        props.init();

        assertEquals("http://legacy:9200", props.getUrl());
    }

    @Test
    public void testLegacyVersionPropertyFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.version", "2");

        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(env);
        props.init();

        assertEquals(2, props.getVersion());
    }

    @Test
    public void testLegacyIndexNamePropertyFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.indexName", "legacy_prefix");

        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(env);
        props.init();

        assertEquals("legacy_prefix", props.getIndexPrefix());
    }

    @Test
    public void testVersionZeroIsIgnoredAsES7DisableFlag() {
        // conductor.elasticsearch.version=0 is a special flag to disable ES7 auto-config
        // It should NOT set OpenSearch version to 0
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.version", "0");

        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(env);
        props.init();

        assertEquals(2, props.getVersion()); // Should use default, not 0
    }

    @Test
    public void testInvalidLegacyVersionUsesDefault() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.version", "invalid");

        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(env);
        props.init();

        assertEquals(2, props.getVersion()); // Should use default
    }

    @Test
    public void testAllLegacyPropertiesFallback() {
        MockEnvironment env = new MockEnvironment();
        // Set all legacy properties
        env.setProperty("conductor.elasticsearch.url", "http://legacy:9200");
        env.setProperty("conductor.elasticsearch.version", "1");
        env.setProperty("conductor.elasticsearch.indexName", "legacy_prefix");
        env.setProperty("conductor.elasticsearch.clusterHealthColor", "yellow");
        env.setProperty("conductor.elasticsearch.indexBatchSize", "100");
        env.setProperty("conductor.elasticsearch.asyncWorkerQueueSize", "200");
        env.setProperty("conductor.elasticsearch.asyncMaxPoolSize", "24");
        env.setProperty("conductor.elasticsearch.indexShardCount", "10");
        env.setProperty("conductor.elasticsearch.indexReplicasCount", "2");
        env.setProperty("conductor.elasticsearch.taskLogResultLimit", "50");
        env.setProperty("conductor.elasticsearch.restClientConnectionRequestTimeout", "5000");
        env.setProperty("conductor.elasticsearch.autoIndexManagementEnabled", "false");
        env.setProperty("conductor.elasticsearch.username", "admin");
        env.setProperty("conductor.elasticsearch.password", "secret");

        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(env);
        props.init();

        // Verify all properties loaded from legacy namespace
        assertEquals("http://legacy:9200", props.getUrl());
        assertEquals(1, props.getVersion());
        assertEquals("legacy_prefix", props.getIndexPrefix());
        assertEquals("yellow", props.getClusterHealthColor());
        assertEquals(100, props.getIndexBatchSize());
        assertEquals(200, props.getAsyncWorkerQueueSize());
        assertEquals(24, props.getAsyncMaxPoolSize());
        assertEquals(10, props.getIndexShardCount());
        assertEquals(2, props.getIndexReplicasCount());
        assertEquals(50, props.getTaskLogResultLimit());
        assertEquals(5000, props.getRestClientConnectionRequestTimeout());
        assertFalse(props.isAutoIndexManagementEnabled());
        assertEquals("admin", props.getUsername());
        assertEquals("secret", props.getPassword());
    }

    @Test
    public void testNullEnvironmentIsHandledGracefully() {
        OpenSearchProperties props = new OpenSearchProperties();
        props.setEnvironment(null);
        props.init(); // Should not throw

        // Should use defaults
        assertEquals(2, props.getVersion());
        assertEquals("localhost:9201", props.getUrl());
    }
}
