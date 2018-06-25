package com.netflix.conductor.elasticsearch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

import org.elasticsearch.client.Client;

public class ElasticSearchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ElasticSearchConfiguration.class).to(SystemPropertiesElasticSearchConfiguration.class);
        bind(Client.class).toProvider(ElasticSearchTransportClientProvider.class).in(Singleton.class);
    }
}
