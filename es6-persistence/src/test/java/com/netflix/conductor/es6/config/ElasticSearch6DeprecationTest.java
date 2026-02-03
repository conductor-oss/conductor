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
package com.netflix.conductor.es6.config;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests that verify the deprecated 'elasticsearch_v6' indexing type throws a helpful error. */
public class ElasticSearch6DeprecationTest {

    // =========================================================================
    // Test: PostConstruct always throws IllegalStateException
    // =========================================================================

    @Test(expected = IllegalStateException.class)
    public void testPostConstructAlwaysThrows() {
        // The @PostConstruct method should always throw
        ElasticSearch6DeprecationConfiguration config =
                new ElasticSearch6DeprecationConfiguration();
        config.failWithMigrationMessage();
    }

    // =========================================================================
    // Test: Error message contains migration instructions
    // =========================================================================

    @Test
    public void testDeprecationConfigurationThrowsHelpfulError() {
        ElasticSearch6DeprecationConfiguration config =
                new ElasticSearch6DeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify error message contains key information
            assertTrue(
                    "Error should mention it's a configuration error",
                    message.contains("CONFIGURATION ERROR"));

            assertTrue(
                    "Error should mention Elasticsearch 6.x is deprecated",
                    message.contains("deprecated") || message.contains("Elasticsearch 6.x"));

            assertTrue(
                    "Error should show the old configuration",
                    message.contains("conductor.indexing.type=elasticsearch_v6"));

            assertTrue(
                    "Error should show elasticsearch option",
                    message.contains("conductor.indexing.type=elasticsearch"));

            assertTrue(
                    "Error should mention Elasticsearch 7.x",
                    message.contains("Elasticsearch 7.x") || message.contains("7.x"));

            assertTrue(
                    "Error should mention EOL",
                    message.contains("end-of-life") || message.contains("November 2020"));
        }
    }

    // =========================================================================
    // Test: Deprecation message formatting is readable
    // =========================================================================

    @Test
    public void testDeprecationMessageIsWellFormatted() {
        ElasticSearch6DeprecationConfiguration config =
                new ElasticSearch6DeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify message has box formatting (makes it stand out in logs)
            assertTrue("Message should have top border", message.contains("╔"));

            assertTrue("Message should have bottom border", message.contains("╚"));

            // Verify message has multiple lines (not just a single line error)
            String[] lines = message.split("\n");
            assertTrue("Message should be multi-line for readability", lines.length > 5);

            // Verify message is not too verbose (should fit in terminal)
            assertTrue("Message should be concise (under 30 lines)", lines.length < 30);
        }
    }

    // =========================================================================
    // Test: Verify GitHub archive link is present
    // =========================================================================

    @Test
    public void testErrorMessageIncludesGitHubArchiveLink() {
        ElasticSearch6DeprecationConfiguration config =
                new ElasticSearch6DeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify GitHub archive link is included
            assertTrue(
                    "Error should include GitHub archive link",
                    message.contains("github.com/conductor-oss/conductor-es6-persistence")
                            || message.contains("conductor-es6-persistence"));
        }
    }

    // =========================================================================
    // Test: Verify migration instructions mention property compatibility
    // =========================================================================

    @Test
    public void testErrorMessageMentionsPropertyCompatibility() {
        ElasticSearch6DeprecationConfiguration config =
                new ElasticSearch6DeprecationConfiguration();

        try {
            config.failWithMigrationMessage();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String message = e.getMessage();

            // Verify message mentions that other properties remain the same
            assertTrue(
                    "Error should mention properties remain the same",
                    message.contains("remain the same")
                            || message.contains("conductor.elasticsearch.*"));
        }
    }
}
