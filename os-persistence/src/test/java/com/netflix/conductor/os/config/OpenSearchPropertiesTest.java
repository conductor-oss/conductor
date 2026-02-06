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
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

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

    // =========================================================================
    // Test Cluster 2: Version Validation
    // =========================================================================

    @Test
    public void testSupportedVersion1IsAccepted() {
        OpenSearchProperties props = new OpenSearchProperties();
        props.setVersion(1);
        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);
        props.init(); // Should not throw

        assertEquals(1, props.getVersion());
    }

    @Test
    public void testSupportedVersion2IsAccepted() {
        OpenSearchProperties props = new OpenSearchProperties();
        props.setVersion(2);
        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);
        props.init(); // Should not throw

        assertEquals(2, props.getVersion());
    }

    @Test
    public void testDefaultVersionIs2() {
        OpenSearchProperties props = new OpenSearchProperties();
        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);
        props.init();

        assertEquals(2, props.getVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsupportedVersion3ThrowsException() {
        OpenSearchProperties props = new OpenSearchProperties();
        props.setVersion(3);
        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);
        props.init(); // Should throw IllegalArgumentException
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsupportedVersion99ThrowsException() {
        OpenSearchProperties props = new OpenSearchProperties();
        props.setVersion(99);
        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);
        props.init(); // Should throw IllegalArgumentException
    }

    @Test
    public void testUnsupportedVersionExceptionMessage() {
        OpenSearchProperties props = new OpenSearchProperties();
        props.setVersion(99);
        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);

        try {
            props.init();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Verify error message contains useful information
            assertTrue(e.getMessage().contains("Unsupported OpenSearch version: 99"));
            assertTrue(e.getMessage().contains("Supported versions are"));
        }
    }

    @Test
    public void testAllSupportedVersionsAreAccepted() {
        // Test all versions in SUPPORTED_VERSIONS set
        for (int version : OpenSearchProperties.getSupportedVersions()) {
            OpenSearchProperties props = new OpenSearchProperties();
            props.setVersion(version);
            MockEnvironment env = new MockEnvironment();
            props.setEnvironment(env);
            props.init(); // Should not throw

            assertEquals(version, props.getVersion());
        }
    }

    // =========================================================================
    // Test Cluster 3: Property Precedence
    // =========================================================================

    @Test
    public void testNewUrlPropertyOverridesLegacy() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.url", "http://legacy:9200");
        env.setProperty("conductor.opensearch.url", "http://new:9200");

        OpenSearchProperties props = new OpenSearchProperties();
        // Simulate Spring's @ConfigurationProperties binding for new property
        props.setUrl("http://new:9200");
        props.setEnvironment(env);
        props.init();

        // New property should be preserved (not overridden by legacy fallback)
        assertEquals("http://new:9200", props.getUrl());
    }

    @Test
    public void testNewVersionPropertyOverridesLegacy() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.version", "1");
        env.setProperty("conductor.opensearch.version", "2");

        OpenSearchProperties props = new OpenSearchProperties();
        // Simulate Spring's @ConfigurationProperties binding for new property
        props.setVersion(2);
        props.setEnvironment(env);
        props.init();

        // New property should be preserved (not overridden by legacy fallback)
        assertEquals(2, props.getVersion());
    }

    @Test
    public void testNewIndexPrefixPropertyOverridesLegacy() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.elasticsearch.indexName", "legacy_prefix");
        env.setProperty("conductor.opensearch.indexPrefix", "new_prefix");

        OpenSearchProperties props = new OpenSearchProperties();
        // Simulate Spring's @ConfigurationProperties binding for new property
        props.setIndexPrefix("new_prefix");
        props.setEnvironment(env);
        props.init();

        // New property should be preserved (not overridden by legacy fallback)
        assertEquals("new_prefix", props.getIndexPrefix());
    }

    @Test
    public void testMixedPropertiesResolveCorrectly() {
        MockEnvironment env = new MockEnvironment();
        // Legacy properties set in environment
        env.setProperty("conductor.elasticsearch.indexName", "legacy_prefix");
        env.setProperty("conductor.elasticsearch.indexBatchSize", "100");
        env.setProperty("conductor.opensearch.url", "http://new:9200");
        env.setProperty("conductor.opensearch.version", "2");

        OpenSearchProperties props = new OpenSearchProperties();
        // Simulate Spring's @ConfigurationProperties binding for new properties
        props.setUrl("http://new:9200");
        props.setVersion(2);
        // Don't set indexPrefix or indexBatchSize - let them fallback from legacy
        props.setEnvironment(env);
        props.init();

        // New properties should be preserved
        assertEquals("http://new:9200", props.getUrl());
        assertEquals(2, props.getVersion());
        // Legacy properties should be used where new ones aren't set
        assertEquals("legacy_prefix", props.getIndexPrefix());
        assertEquals(100, props.getIndexBatchSize());
    }

    @Test
    public void testOnlyNewPropertiesWork() {
        OpenSearchProperties props = new OpenSearchProperties();
        // Simulate Spring's @ConfigurationProperties binding
        props.setUrl("http://new:9200");
        props.setVersion(2);
        props.setIndexPrefix("new_prefix");
        props.setClusterHealthColor("yellow");

        MockEnvironment env = new MockEnvironment();
        props.setEnvironment(env);
        props.init();

        assertEquals("http://new:9200", props.getUrl());
        assertEquals(2, props.getVersion());
        assertEquals("new_prefix", props.getIndexPrefix());
        assertEquals("yellow", props.getClusterHealthColor());
    }

    @Test
    public void testNewPropertiesTakePrecedenceForAllConfigurableFields() {
        MockEnvironment env = new MockEnvironment();
        // Set all as legacy
        env.setProperty("conductor.elasticsearch.url", "http://legacy:9200");
        env.setProperty("conductor.elasticsearch.indexName", "legacy_prefix");
        env.setProperty("conductor.elasticsearch.clusterHealthColor", "green");
        env.setProperty("conductor.elasticsearch.indexReplicasCount", "1");
        env.setProperty("conductor.elasticsearch.username", "legacy_user");
        // Set new properties in environment so hasNewProperty() returns true
        env.setProperty("conductor.opensearch.url", "http://new:9200");
        env.setProperty("conductor.opensearch.indexPrefix", "new_prefix");
        env.setProperty("conductor.opensearch.clusterHealthColor", "yellow");
        env.setProperty("conductor.opensearch.indexReplicasCount", "2");
        env.setProperty("conductor.opensearch.username", "new_user");

        OpenSearchProperties props = new OpenSearchProperties();
        // Simulate Spring's @ConfigurationProperties binding for new properties
        props.setUrl("http://new:9200");
        props.setIndexPrefix("new_prefix");
        props.setClusterHealthColor("yellow");
        props.setIndexReplicasCount(2);
        props.setUsername("new_user");
        props.setEnvironment(env);
        props.init();

        // All new properties should be preserved (not overridden by legacy fallback)
        assertEquals("http://new:9200", props.getUrl());
        assertEquals("new_prefix", props.getIndexPrefix());
        assertEquals("yellow", props.getClusterHealthColor());
        assertEquals(2, props.getIndexReplicasCount());
        assertEquals("new_user", props.getUsername());
    }

    @Test
    public void testHasNewPropertyDetectsCorrectly() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("conductor.opensearch.url", "http://new:9200");
        env.setProperty("conductor.elasticsearch.indexName", "legacy_prefix");

        OpenSearchProperties props = new OpenSearchProperties();
        // Set only URL via new property
        props.setUrl("http://new:9200");
        // Don't set indexPrefix - let it fallback from legacy
        props.setEnvironment(env);
        props.init();

        // URL was set via new property (should be preserved)
        assertEquals("http://new:9200", props.getUrl());
        // IndexPrefix was not set via new property (should use legacy fallback)
        assertEquals("legacy_prefix", props.getIndexPrefix());
    }

    // =========================================================================
    // Test Cluster 4: Integration Tests
    // =========================================================================

    /** Integration test with Spring Boot context to verify new properties work end-to-end. */
    @RunWith(SpringRunner.class)
    @SpringBootTest(
            classes = OpenSearchPropertiesTest.IntegrationTestWithNewProperties.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.opensearch.url=http://integration-new:9200",
                "conductor.opensearch.version=2",
                "conductor.opensearch.indexPrefix=integration_new",
                "conductor.opensearch.clusterHealthColor=yellow"
            })
    public static class IntegrationTestWithNewProperties {

        @Configuration
        @EnableConfigurationProperties(OpenSearchProperties.class)
        static class TestConfig {}

        @Autowired private OpenSearchProperties properties;

        @Test
        public void testNewPropertiesBindCorrectlyInSpringContext() {
            assertEquals("http://integration-new:9200", properties.getUrl());
            assertEquals(2, properties.getVersion());
            assertEquals("integration_new", properties.getIndexPrefix());
            assertEquals("yellow", properties.getClusterHealthColor());
        }
    }

    /** Integration test with Spring Boot context to verify legacy properties still work. */
    @RunWith(SpringRunner.class)
    @SpringBootTest(
            classes = OpenSearchPropertiesTest.IntegrationTestWithLegacyProperties.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.elasticsearch.url=http://integration-legacy:9200",
                "conductor.elasticsearch.version=1",
                "conductor.elasticsearch.indexName=integration_legacy",
                "conductor.elasticsearch.clusterHealthColor=green"
            })
    public static class IntegrationTestWithLegacyProperties {

        @Configuration
        @EnableConfigurationProperties(OpenSearchProperties.class)
        static class TestConfig {}

        @Autowired private OpenSearchProperties properties;

        @Test
        public void testLegacyPropertiesBindCorrectlyInSpringContext() {
            assertEquals("http://integration-legacy:9200", properties.getUrl());
            assertEquals(1, properties.getVersion());
            assertEquals("integration_legacy", properties.getIndexPrefix());
            assertEquals("green", properties.getClusterHealthColor());
        }
    }

    /** Integration test with Spring Boot context to verify mixed properties resolve correctly. */
    @RunWith(SpringRunner.class)
    @SpringBootTest(
            classes = OpenSearchPropertiesTest.IntegrationTestWithMixedProperties.TestConfig.class)
    @TestPropertySource(
            properties = {
                // Legacy properties
                "conductor.elasticsearch.indexName=legacy_mixed",
                "conductor.elasticsearch.clusterHealthColor=green",
                // New properties (should take precedence)
                "conductor.opensearch.url=http://integration-mixed:9200",
                "conductor.opensearch.version=2"
            })
    public static class IntegrationTestWithMixedProperties {

        @Configuration
        @EnableConfigurationProperties(OpenSearchProperties.class)
        static class TestConfig {}

        @Autowired private OpenSearchProperties properties;

        @Test
        public void testMixedPropertiesResolveCorrectlyInSpringContext() {
            // New properties should win
            assertEquals("http://integration-mixed:9200", properties.getUrl());
            assertEquals(2, properties.getVersion());
            // Legacy properties should be used where new ones aren't set
            assertEquals("legacy_mixed", properties.getIndexPrefix());
            assertEquals("green", properties.getClusterHealthColor());
        }
    }
}
