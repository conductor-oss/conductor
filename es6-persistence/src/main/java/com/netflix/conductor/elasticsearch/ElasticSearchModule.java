package com.netflix.conductor.elasticsearch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.netflix.conductor.elasticsearch.es6.ElasticSearchV6Module;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

public class ElasticSearchModule extends AbstractModule {
    @Override
    protected void configure() {

        ElasticSearchConfiguration esConfiguration = new SystemPropertiesElasticSearchConfiguration();

        bind(ElasticSearchConfiguration.class).to(SystemPropertiesElasticSearchConfiguration.class);
        bind(Client.class).toProvider(ElasticSearchTransportClientProvider.class).in(Singleton.class);

        bind(RestClient.class).toProvider(ElasticSearchRestClientProvider.class).in(Singleton.class);
        bind(RestClientBuilder.class).toProvider(ElasticSearchRestClientBuilderProvider.class).in(Singleton.class);

        install(new ElasticSearchV6Module(esConfiguration));
    }
}
