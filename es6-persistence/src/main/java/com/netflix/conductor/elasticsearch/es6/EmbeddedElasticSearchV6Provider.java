package com.netflix.conductor.elasticsearch.es6;

import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

import javax.inject.Inject;
import java.util.Optional;

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
