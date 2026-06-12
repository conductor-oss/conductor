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
package org.conductoross.conductor.os3.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Conditional configuration for enabling OpenSearch 3.x as the indexing backend.
 *
 * <p>OpenSearch 3.x is enabled when:
 *
 * <ul>
 *   <li>{@code conductor.indexing.enabled=true} (defaults to true if not specified)
 *   <li>{@code conductor.indexing.type=opensearch3}
 * </ul>
 *
 * <p><b>Recommended Configuration:</b>
 *
 * <pre>{@code
 * # Enable OpenSearch 3.x indexing
 * conductor.indexing.enabled=true
 * conductor.indexing.type=opensearch3
 *
 * # OpenSearch connection settings
 * conductor.opensearch.url=http://localhost:9200
 * conductor.opensearch.indexPrefix=conductor
 * conductor.opensearch.indexReplicasCount=0
 * conductor.opensearch.clusterHealthColor=green
 * }</pre>
 */
public class OpenSearchConditions {

    private OpenSearchConditions() {}

    public static class OpenSearchV3Enabled extends AllNestedConditions {

        OpenSearchV3Enabled() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(
                name = "conductor.indexing.enabled",
                havingValue = "true",
                matchIfMissing = true)
        static class enabledIndexing {}

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch3")
        static class enabledOS3 {}
    }
}
