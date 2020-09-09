/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearchRestClientProvider implements Provider<RestClient> {

    private final ElasticSearchConfiguration configuration;

    @Inject
    public ElasticSearchRestClientProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public RestClient get() {
        RestClientBuilder restClientBuilder = RestClient.builder(convertToHttpHosts(configuration.getURIs()));

        if (configuration.getElasticsearchRestClientConnectionRequestTimeout() > 0) {
            restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder.setConnectionRequestTimeout(configuration.getElasticsearchRestClientConnectionRequestTimeout()));
        }

        return restClientBuilder.build();

    }

    private HttpHost[] convertToHttpHosts(List<URI> hosts) {
        List<HttpHost> list = hosts.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getScheme()))
                .collect(Collectors.toList());

        return list.toArray(new HttpHost[list.size()]);
    }

}
