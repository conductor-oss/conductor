package com.netflix.conductor.elasticsearch;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;

public class ElasticSearchRestClientProvider implements Provider<RestHighLevelClient> {
    private final ElasticSearchConfiguration configuration;

    @Inject
    public ElasticSearchRestClientProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public RestHighLevelClient get() {
        RestClient lowLevelRestClient = RestClient.builder(convertToHttpHosts(configuration.getURIs())).build();
        return new RestHighLevelClient(lowLevelRestClient);
    }

    private HttpHost[] convertToHttpHosts(List<URI> hosts) {
        List<HttpHost> list = hosts.stream().map(host ->
                new HttpHost(host.getHost(), host.getPort()))
                .collect(Collectors.toList());
        return list.toArray(new HttpHost[0]);
    }
}
