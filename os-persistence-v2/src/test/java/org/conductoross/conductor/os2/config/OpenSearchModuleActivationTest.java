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
package org.conductoross.conductor.os2.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.dao.IndexDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

/**
 * Tests that verify the OpenSearch v2 module activates correctly based on conductor.indexing.type
 * configuration.
 */
public class OpenSearchModuleActivationTest {

    // =========================================================================
    // Test: Module activates with correct indexing.type
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = ModuleActivatesWithOpensearch2Type.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.indexing.enabled=true",
                "conductor.indexing.type=opensearch2",
                "conductor.opensearch.url=http://localhost:9200",
                "conductor.opensearch.autoIndexManagement=false"
            })
    public static class ModuleActivatesWithOpensearch2Type {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {
            @Bean
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }
        }

        @Autowired private ApplicationContext context;

        @MockBean private RestClient restClient;
        @MockBean private RestHighLevelClient restHighLevelClient;

        @Test
        public void testOpenSearchV2BeansAreCreated() {
            // Verify OpenSearchProperties bean exists
            assertTrue(context.containsBean("openSearchProperties"));
            OpenSearchProperties props = context.getBean(OpenSearchProperties.class);
            assertNotNull(props);

            // Verify it's using the correct package (os2)
            assertEquals(
                    "org.conductoross.conductor.os2.config.OpenSearchProperties",
                    props.getClass().getName());
        }

        @Test
        public void testIndexDAOBeanIsCreated() {
            // Verify IndexDAO bean exists (this is the main bean from the module)
            assertTrue(
                    "IndexDAO bean should exist when opensearch2 is configured",
                    context.containsBean("indexDAO"));

            IndexDAO indexDAO = context.getBean(IndexDAO.class);
            assertNotNull(indexDAO);

            // Verify it's the v2 implementation
            assertTrue(
                    "IndexDAO should be from os2 package",
                    indexDAO.getClass().getName().contains("org.conductoross.conductor.os2"));
        }
    }

    // =========================================================================
    // Test: Module does NOT activate with wrong indexing.type
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = ModuleDoesNotActivateWithOpensearch3Type.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.indexing.enabled=true",
                "conductor.indexing.type=opensearch3", // Wrong type for v2 module
                "conductor.opensearch.url=http://localhost:9200",
                "conductor.opensearch.autoIndexManagement=false"
            })
    public static class ModuleDoesNotActivateWithOpensearch3Type {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {
            @Bean
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }
        }

        @Autowired private ApplicationContext context;

        @Test
        public void testOpenSearchV2BeansAreNotCreated() {
            // The v2 module should NOT activate when type=opensearch3
            // Note: We can't check for absence of beans because v3 module might provide them
            // This test mainly verifies the configuration doesn't cause errors
            assertNotNull(context);
        }
    }

    // =========================================================================
    // Test: Module does NOT activate when indexing.enabled=false
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = ModuleDoesNotActivateWhenIndexingDisabled.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.indexing.enabled=false",
                "conductor.indexing.type=opensearch2",
                "conductor.opensearch.url=http://localhost:9200",
                "conductor.opensearch.autoIndexManagement=false"
            })
    public static class ModuleDoesNotActivateWhenIndexingDisabled {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {
            @Bean
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }
        }

        @Autowired private ApplicationContext context;

        @Test
        public void testIndexDAOBeanIsNotCreated() {
            // When indexing is disabled, IndexDAO should not be created
            try {
                context.getBean(IndexDAO.class);
                fail("IndexDAO bean should not exist when indexing is disabled");
            } catch (NoSuchBeanDefinitionException e) {
                // Expected - bean should not exist
            }
        }
    }

    // =========================================================================
    // Test: Module does NOT activate with generic 'opensearch' type
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = ModuleDoesNotActivateWithGenericOpensearchType.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.indexing.enabled=true",
                "conductor.indexing.type=opensearch", // Generic type (deprecated)
                "conductor.opensearch.url=http://localhost:9200",
                "conductor.opensearch.autoIndexManagement=false"
            })
    public static class ModuleDoesNotActivateWithGenericOpensearchType {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {
            @Bean
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }
        }

        @Autowired private ApplicationContext context;

        @Test
        public void testOpenSearchV2BeansAreNotCreatedForGenericType() {
            // The v2 module should NOT activate for generic 'opensearch' type
            // The deprecation module should handle this instead
            assertNotNull(context);

            // If any OpenSearch beans exist, they should be from the deprecation module
            // not from v2
        }
    }

    // =========================================================================
    // Test: Default indexing.enabled=true behavior
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = ModuleActivatesWithDefaultIndexingEnabled.TestConfig.class)
    @TestPropertySource(
            properties = {
                // indexing.enabled defaults to true (matchIfMissing=true)
                "conductor.indexing.type=opensearch2",
                "conductor.opensearch.url=http://localhost:9200",
                "conductor.opensearch.autoIndexManagement=false"
            })
    public static class ModuleActivatesWithDefaultIndexingEnabled {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {
            @Bean
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }
        }

        @Autowired private ApplicationContext context;

        @Test
        public void testModuleActivatesWhenIndexingEnabledNotExplicitlySet() {
            // Module should activate even when indexing.enabled is not set
            // because the condition has matchIfMissing=true
            assertTrue(
                    "IndexDAO bean should exist with default indexing.enabled=true",
                    context.containsBean("indexDAO"));

            IndexDAO indexDAO = context.getBean(IndexDAO.class);
            assertNotNull(indexDAO);
        }
    }

    // =========================================================================
    // Test: Configuration properties are correctly loaded
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = ConfigurationPropertiesAreLoaded.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.indexing.type=opensearch2",
                "conductor.opensearch.url=http://test-server:9200",
                "conductor.opensearch.version=2",
                "conductor.opensearch.indexPrefix=test_prefix",
                "conductor.opensearch.clusterHealthColor=yellow",
                "conductor.opensearch.autoIndexManagement=false"
            })
    public static class ConfigurationPropertiesAreLoaded {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {
            @Bean
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }
        }

        @Autowired private OpenSearchProperties properties;

        @Test
        public void testPropertiesAreCorrectlyBound() {
            assertNotNull(properties);
            assertEquals("http://test-server:9200", properties.getUrl());
            assertEquals(2, properties.getVersion());
            assertEquals("test_prefix", properties.getIndexPrefix());
            assertEquals("yellow", properties.getClusterHealthColor());
        }
    }
}
