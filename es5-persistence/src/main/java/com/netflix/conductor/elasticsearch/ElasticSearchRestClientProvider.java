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

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchRestClientProvider implements Provider<RestClient> {
    private final ElasticSearchConfiguration configuration;
    private static Logger logger = LoggerFactory.getLogger(ElasticSearchRestClientProvider.class);
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

        if (configuration.getElasticSearchBasicAuthUsername() != null && configuration.getElasticSearchBasicAuthPassword() != null) {
            logger.info("Configure ElasticSearch with BASIC authentication. User:{}",configuration.getElasticSearchBasicAuthUsername());
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(configuration.getElasticSearchBasicAuthUsername(), configuration.getElasticSearchBasicAuthPassword()));
            restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        } else {
            logger.info("Configure ElasticSearch with no authentication.");
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
