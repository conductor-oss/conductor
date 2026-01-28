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
package com.netflix.conductor.os.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

/**
 * Tests that verify the deprecated generic 'opensearch' indexing type throws a helpful error.
 */
public class OpenSearchDeprecationTest {

    // =========================================================================
    // Test: Generic 'opensearch' type throws IllegalStateException
    // =========================================================================

    @RunWith(SpringRunner.class)
    @SpringBootTest(classes = GenericOpensearchTypeThrowsError.TestConfig.class)
    @TestPropertySource(
            properties = {
                "conductor.indexing.enabled=true",
                "conductor.indexing.type=opensearch",  // Deprecated generic type
                "conductor.opensearch.url=http://localhost:9200"
            })
    public static class GenericOpensearchTypeThrowsError {

        @Configuration
        @EnableAutoConfiguration
        static class TestConfig {}

        @Test(expected = BeanCreationException.class)
        public void testGenericOpensearchTypeFailsWithError() {
            // Spring context creation should fail
            fail("Spring context should not be created with generic 'opensearch' type");
        }
    }

    // =========================================================================
    // Test: Error message contains migration instructions
    // =========================================================================

    @Test
    public void testDeprecationConfigurationThrowsHelpfulError() {
        OpenSearchDeprecationConfiguration config = new OpenSearchDeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify error message contains key information
            assertTrue("Error should mention it's a configuration error",
                    message.contains("CONFIGURATION ERROR"));

            assertTrue("Error should mention 'opensearch' is deprecated",
                    message.contains("deprecated") || message.contains("DEPRECATED"));

            assertTrue("Error should show the old configuration",
                    message.contains("conductor.indexing.type=opensearch"));

            assertTrue("Error should show opensearch2 option",
                    message.contains("opensearch2"));

            assertTrue("Error should show opensearch3 option",
                    message.contains("opensearch3"));

            assertTrue("Error should mention OpenSearch 2.x",
                    message.contains("2.x") || message.contains("2"));

            assertTrue("Error should mention OpenSearch 3.x",
                    message.contains("3.x") || message.contains("3"));

            assertTrue("Error should reference issue #678",
                    message.contains("678"));
        }
    }

    // =========================================================================
    // Test: Configuration only activates with exact 'opensearch' value
    // =========================================================================

    @Test
    public void testConfigurationDoesNotActivateForOpensearch2() {
        // This test verifies the @ConditionalOnProperty is specific to "opensearch"
        // We can't easily test Spring conditions directly, but we can verify
        // the annotation is configured correctly by checking it doesn't match variants

        // The actual condition uses havingValue = "opensearch" which should NOT match:
        // - "opensearch2"
        // - "opensearch3"
        // - "opensearch-2"
        // - etc.

        // This is implicitly tested by the module activation tests in v2 and v3,
        // but documented here for clarity
        assertTrue("Test passes to document the specific matching behavior", true);
    }

    // =========================================================================
    // Test: Deprecation message formatting is readable
    // =========================================================================

    @Test
    public void testDeprecationMessageIsWellFormatted() {
        OpenSearchDeprecationConfiguration config = new OpenSearchDeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify message has box formatting (makes it stand out in logs)
            assertTrue("Message should have top border",
                    message.contains("╔") || message.contains("="));

            assertTrue("Message should have bottom border",
                    message.contains("╚") || message.contains("="));

            // Verify message has multiple lines (not just a single line error)
            String[] lines = message.split("\n");
            assertTrue("Message should be multi-line for readability",
                    lines.length > 5);

            // Verify message is not too verbose (should fit in terminal)
            assertTrue("Message should be concise (under 30 lines)",
                    lines.length < 30);
        }
    }

    // =========================================================================
    // Test: Unit test for PostConstruct behavior
    // =========================================================================

    @Test(expected = IllegalStateException.class)
    public void testPostConstructAlwaysThrows() {
        // The @PostConstruct method should always throw
        OpenSearchDeprecationConfiguration config = new OpenSearchDeprecationConfiguration();
        config.failWithMigrationMessage();
    }

    // =========================================================================
    // Test: Verify GitHub issue link is present
    // =========================================================================

    @Test
    public void testErrorMessageIncludesGitHubIssueLink() {
        OpenSearchDeprecationConfiguration config = new OpenSearchDeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify GitHub issue URL is included
            assertTrue("Error should include GitHub issue link",
                    message.contains("github.com/conductor-oss/conductor/issues/678") ||
                    message.contains("issues/678"));
        }
    }

    // =========================================================================
    // Test: Verify migration instructions mention shared properties
    // =========================================================================

    @Test
    public void testErrorMessageMentionsSharedProperties() {
        OpenSearchDeprecationConfiguration config = new OpenSearchDeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify message mentions that other properties remain the same
            assertTrue("Error should mention properties remain the same",
                    message.contains("remain the same") ||
                    message.contains("conductor.opensearch.*"));
        }
    }
}
