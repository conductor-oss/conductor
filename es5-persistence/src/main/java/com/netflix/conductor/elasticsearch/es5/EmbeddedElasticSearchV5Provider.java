package com.netflix.conductor.elasticsearch.es5;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

import java.util.Optional;

import javax.inject.Inject;

public class EmbeddedElasticSearchV5Provider implements EmbeddedElasticSearchProvider {
    private final ElasticSearchConfiguration configuration;

    @Inject
    public EmbeddedElasticSearchV5Provider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Optional<EmbeddedElasticSearch> get() {
        return isEmbedded() ? Optional.of(
                new EmbeddedElasticSearchV5(
                        configuration.getEmbeddedClusterName(),
                        configuration.getEmbeddedHost(),
                        configuration.getEmbeddedPort()
                )
        ) : Optional.empty();
    }

    private boolean isEmbedded() {
        return configuration.getElasticSearchType().equals(Configuration.ELASTICSEARCH.MEMORY);
    }
}
