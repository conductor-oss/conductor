/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch.es6;

import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import java.util.Optional;
import javax.inject.Inject;

public class EmbeddedElasticSearchV6Provider implements EmbeddedElasticSearchProvider {
    private final ElasticSearchConfiguration configuration;

    @Inject
    public EmbeddedElasticSearchV6Provider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Optional<EmbeddedElasticSearch> get() {
        return isEmbedded() ? Optional.of(
                new EmbeddedElasticSearchV6(
                        configuration.getEmbeddedClusterName(),
                        configuration.getEmbeddedHost(),
                        configuration.getEmbeddedPort()
                )
        ) : Optional.empty();
    }

    private boolean isEmbedded() {
        return configuration.getElasticSearchInstanceType().equals(ElasticSearchConfiguration.ElasticSearchInstanceType.MEMORY);
    }

}
