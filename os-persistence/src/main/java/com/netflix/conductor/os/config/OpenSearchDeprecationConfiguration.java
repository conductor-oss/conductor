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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Deprecation stub for the generic 'opensearch' indexing type.
 *
 * <p>This configuration activates when {@code conductor.indexing.type=opensearch} is used, which is
 * now deprecated in favor of version-specific types.
 *
 * <p>The generic OpenSearch module has been split into version-specific modules to support both
 * OpenSearch 2.x and 3.x with isolated dependencies. Users must now explicitly specify which
 * version they're using.
 *
 * <p><b>Migration Required:</b>
 *
 * <p>Change your configuration from:
 *
 * <pre>{@code
 * conductor.indexing.type=opensearch
 * conductor.opensearch.url=http://localhost:9200
 * }</pre>
 *
 * <p>To one of:
 *
 * <pre>{@code
 * # For OpenSearch 2.x
 * conductor.indexing.type=opensearch2
 * conductor.opensearch.url=http://localhost:9200
 *
 * # OR for OpenSearch 3.x
 * conductor.indexing.type=opensearch3
 * conductor.opensearch.url=http://localhost:9200
 * }</pre>
 *
 * @see <a href="https://github.com/conductor-oss/conductor/issues/678">Issue #678</a>
 */
@Configuration
@ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch")
public class OpenSearchDeprecationConfiguration {

    @PostConstruct
    public void failWithMigrationMessage() {
        String message =
                "\n"
                        + "╔════════════════════════════════════════════════════════════════════════════╗\n"
                        + "║  CONFIGURATION ERROR: Generic 'opensearch' indexing type is deprecated    ║\n"
                        + "╠════════════════════════════════════════════════════════════════════════════╣\n"
                        + "║                                                                            ║\n"
                        + "║  The generic OpenSearch module has been replaced with version-specific    ║\n"
                        + "║  modules to support both OpenSearch 2.x and 3.x.                          ║\n"
                        + "║                                                                            ║\n"
                        + "║  REQUIRED ACTION: Update your configuration                               ║\n"
                        + "║                                                                            ║\n"
                        + "║  FROM:                                                                     ║\n"
                        + "║    conductor.indexing.type=opensearch                                     ║\n"
                        + "║                                                                            ║\n"
                        + "║  TO (choose one):                                                          ║\n"
                        + "║    conductor.indexing.type=opensearch2  # For OpenSearch 2.x              ║\n"
                        + "║    conductor.indexing.type=opensearch3  # For OpenSearch 3.x              ║\n"
                        + "║                                                                            ║\n"
                        + "║  All other conductor.opensearch.* properties remain the same.             ║\n"
                        + "║                                                                            ║\n"
                        + "║  Legacy code: github.com/conductor-oss/conductor-os-persistence-v1        ║\n"
                        + "║  See: https://github.com/conductor-oss/conductor/issues/678               ║\n"
                        + "║                                                                            ║\n"
                        + "╚════════════════════════════════════════════════════════════════════════════╝\n";

        throw new IllegalStateException(message);
    }
}
