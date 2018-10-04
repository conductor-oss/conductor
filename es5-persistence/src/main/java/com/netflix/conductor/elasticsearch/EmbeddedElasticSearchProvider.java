package com.netflix.conductor.elasticsearch;

import java.util.Optional;

import javax.inject.Provider;

public interface EmbeddedElasticSearchProvider extends Provider<Optional<EmbeddedElasticSearch>> {
}
