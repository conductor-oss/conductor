package com.netflix.conductor.elasticsearch.es2;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

import java.util.Optional;

import javax.inject.Inject;

public class EmbeddedElasticSearchV2Provider implements EmbeddedElasticSearchProvider {
    private final ElasticSearchConfiguration configuration;

    @Inject
    public EmbeddedElasticSearchV2Provider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Optional<EmbeddedElasticSearch> get() {
        return isMemoryAndVersion() ? Optional.of(new EmbeddedElasticSearchV2()) : Optional.empty();
    }

    private boolean isMemoryAndVersion(){
        return configuration.getVersion() == 2 && configuration.getDB().equals(Configuration.DB.MEMORY);
    }
}
