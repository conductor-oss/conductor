package com.netflix.conductor.noopindex;

import com.google.inject.AbstractModule;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

/**
 * Configures no-op implementation of {@link IndexDAO}. Used when you don't want any indexing support (meaning no
 * ElasticSearch is required.)
 */
public class NoopIndexModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(IndexDAO.class).to(NoopIndexDAO.class);
		bind(EmbeddedElasticSearchProvider.class).to(NoopEmbeddedElasticSearchProvider.class);
	}
}
