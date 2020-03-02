package com.netflix.conductor.noopindex;

import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

import java.util.Optional;

/**
 * Dummy implementation of {@link EmbeddedElasticSearchProvider}.
 */
public class NoopEmbeddedElasticSearchProvider implements EmbeddedElasticSearchProvider {
	@Override
	public Optional<EmbeddedElasticSearch> get() {
		return Optional.empty();
	}
}
