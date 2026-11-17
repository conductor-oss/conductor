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
package org.conductoross.conductor.es8.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * Conditional configuration for enabling Elasticsearch 8.x as the indexing backend.
 *
 * <p>Elasticsearch 8.x is enabled when:
 *
 * <ul>
 *   <li>{@code conductor.indexing.enabled=true} (defaults to true if not specified)
 *   <li>{@code conductor.indexing.type} selects Elasticsearch -- either the explicit {@code
 *       elasticsearch8} value or the generic {@code elasticsearch} value.
 * </ul>
 *
 * <p>The generic {@code elasticsearch} value is accepted because the build-time {@code
 * indexingBackend} selection guarantees only one Elasticsearch persistence module is ever on the
 * classpath. In an es8 build the generic value therefore resolves unambiguously to ES 8, which lets
 * pre-existing configs that set {@code conductor.indexing.type=elasticsearch} keep working after
 * the default backend moved from ES 7 to ES 8. Against an actual 7.17 cluster the es8 client fails
 * on the real version mismatch rather than on a missing {@code IndexDAO} bean.
 */
public class ElasticSearchConditions {

    private ElasticSearchConditions() {}

    public static class ElasticSearchV8Enabled extends AllNestedConditions {

        ElasticSearchV8Enabled() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(
                name = "conductor.indexing.enabled",
                havingValue = "true",
                matchIfMissing = true)
        static class enabledIndexing {}

        @SuppressWarnings("unused")
        @Conditional(ElasticSearchTypeSelected.class)
        static class typeSelected {}
    }

    /**
     * Matches when {@code conductor.indexing.type} selects Elasticsearch, accepting both the
     * explicit {@code elasticsearch8} selector and the generic {@code elasticsearch} selector.
     */
    static class ElasticSearchTypeSelected extends AnyNestedCondition {

        ElasticSearchTypeSelected() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch8")
        static class es8Selector {}

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch")
        static class elasticsearchSelector {}
    }
}
