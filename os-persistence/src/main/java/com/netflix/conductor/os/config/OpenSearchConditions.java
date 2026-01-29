/*
 * Copyright 2023 Conductor Authors.
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

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Conditional configuration for enabling OpenSearch as the indexing backend.
 *
 * <p>OpenSearch is enabled when:
 *
 * <ul>
 *   <li>{@code conductor.indexing.enabled=true} (defaults to true if not specified)
 *   <li>{@code conductor.indexing.type=opensearch}
 * </ul>
 *
 * <p><b>Important:</b> To prevent Elasticsearch 7 auto-configuration from conflicting with
 * OpenSearch, you must also set {@code conductor.elasticsearch.version=0}. This is a temporary
 * workaround that will be addressed in a future release.
 *
 * <p><b>Recommended Configuration:</b>
 *
 * <pre>{@code
 * # Enable OpenSearch indexing
 * conductor.indexing.enabled=true
 * conductor.indexing.type=opensearch
 *
 * # Disable ES7 auto-configuration (required until ES7 module is updated)
 * conductor.elasticsearch.version=0
 *
 * # OpenSearch connection settings (new namespace)
 * conductor.opensearch.url=http://localhost:9200
 * conductor.opensearch.version=2
 * conductor.opensearch.indexPrefix=conductor
 * }</pre>
 */
public class OpenSearchConditions {

    private OpenSearchConditions() {}

    public static class OpenSearchEnabled extends AllNestedConditions {

        OpenSearchEnabled() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(
                name = "conductor.indexing.enabled",
                havingValue = "true",
                matchIfMissing = true)
        static class enabledIndexing {}

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch")
        static class enabledOS {}
    }
}
