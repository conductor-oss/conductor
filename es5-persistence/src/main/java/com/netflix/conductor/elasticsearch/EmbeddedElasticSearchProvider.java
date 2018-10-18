package com.netflix.conductor.elasticsearch;

import javax.inject.Provider;
import java.util.Optional;

public interface EmbeddedElasticSearchProvider extends Provider<Optional<EmbeddedElasticSearch>> {
}
