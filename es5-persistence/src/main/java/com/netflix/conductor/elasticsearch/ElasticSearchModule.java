package com.netflix.conductor.elasticsearch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

import com.netflix.conductor.elasticsearch.es5.ElasticSearchV5Module;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestClient;

public class ElasticSearchModule extends AbstractModule {
    @Override
    protected void configure() {

        ElasticSearchConfiguration esConfiguration = new SystemPropertiesElasticSearchConfiguration();

        bind(ElasticSearchConfiguration.class).to(SystemPropertiesElasticSearchConfiguration.class);
        bind(Client.class).toProvider(ElasticSearchTransportClientProvider.class).in(Singleton.class);
        bind(RestClient.class).toProvider(ElasticSearchRestClientProvider.class).in(Singleton.class);

        install(new ElasticSearchV5Module(esConfiguration));
    }
}
