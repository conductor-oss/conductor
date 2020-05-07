package com.netflix.conductor.tests.utils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

public class IntegrationTestModule extends AbstractModule {

    @Override
    protected void configure() {

    }

    @Provides
    public EmbeddedElasticSearchProvider getElasticSearchProvider(ModulesProvider modulesProvider) {
        return Guice.createInjector(modulesProvider.get()).getInstance(EmbeddedElasticSearchProvider.class);
    }
}
