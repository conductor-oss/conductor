package com.netflix.conductor.elasticsearch;

import com.google.inject.AbstractModule;

public class ElasticSearchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ElasticSearchConfiguration.class).to(SystemPropertiesElasticSearchConfiguration.class);
    }
}
