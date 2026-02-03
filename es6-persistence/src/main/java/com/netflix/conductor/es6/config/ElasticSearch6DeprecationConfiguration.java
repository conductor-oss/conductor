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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Deprecation stub for Elasticsearch 6.x support.
 *
 * <p>This configuration activates when {@code conductor.indexing.type=elasticsearch_v6} is used,
 * which is now deprecated. Elasticsearch 6.x reached end-of-life in November 2020.
 *
 * <p><b>Migration Required:</b>
 *
 * <p>Change your configuration to use Elasticsearch 7.x:
 *
 * <pre>{@code
 * # FROM:
 * conductor.indexing.type=elasticsearch_v6
 * conductor.elasticsearch.url=http://localhost:9200
 *
 * # TO:
 * conductor.indexing.type=elasticsearch
 * conductor.elasticsearch.url=http://localhost:9200
 * }</pre>
 *
 * <p>For legacy code reference, the Elasticsearch 6.x implementation has been archived at: <a
 * href="https://github.com/conductor-oss/conductor-es6-persistence">conductor-es6-persistence</a>
 *
 * @see <a href="https://github.com/conductor-oss/conductor-es6-persistence">Elasticsearch 6.x
 *     Archive</a>
 */
@Configuration
@ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch_v6")
public class ElasticSearch6DeprecationConfiguration {

    @PostConstruct
    public void failWithMigrationMessage() {
        String message =
                "\n"
                        + "╔════════════════════════════════════════════════════════════════════════════╗\n"
                        + "║  CONFIGURATION ERROR: Elasticsearch 6.x support is deprecated             ║\n"
                        + "╠════════════════════════════════════════════════════════════════════════════╣\n"
                        + "║                                                                            ║\n"
                        + "║  Elasticsearch 6.x reached end-of-life in November 2020.                  ║\n"
                        + "║                                                                            ║\n"
                        + "║  REQUIRED ACTION: Upgrade to Elasticsearch 7.x                            ║\n"
                        + "║                                                                            ║\n"
                        + "║  FROM:                                                                     ║\n"
                        + "║    conductor.indexing.type=elasticsearch_v6                               ║\n"
                        + "║                                                                            ║\n"
                        + "║  TO:                                                                       ║\n"
                        + "║    conductor.indexing.type=elasticsearch  # For Elasticsearch 7.x         ║\n"
                        + "║                                                                            ║\n"
                        + "║  All other conductor.elasticsearch.* properties remain the same.          ║\n"
                        + "║                                                                            ║\n"
                        + "║  Legacy code: github.com/conductor-oss/conductor-es6-persistence          ║\n"
                        + "║                                                                            ║\n"
                        + "╚════════════════════════════════════════════════════════════════════════════╝\n";

        throw new IllegalStateException(message);
    }
}
