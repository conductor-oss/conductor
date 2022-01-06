/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.es7.config;

import java.net.URL;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.es7.dao.index.ElasticSearchRestDAOV7;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ElasticSearchProperties.class)
@Conditional(ElasticSearchConditions.ElasticSearchV7Enabled.class)
public class ElasticSearchV7Configuration {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchV7Configuration.class);

    @Bean
    public RestClient restClient(ElasticSearchProperties properties) {
        RestClientBuilder restClientBuilder =
                RestClient.builder(convertToHttpHosts(properties.toURLs()));
        if (properties.getRestClientConnectionRequestTimeout() > 0) {
            restClientBuilder.setRequestConfigCallback(
                    requestConfigBuilder ->
                            requestConfigBuilder.setConnectionRequestTimeout(
                                    properties.getRestClientConnectionRequestTimeout()));
        }
        return restClientBuilder.build();
    }

    @Bean
    public RestClientBuilder restClientBuilder(ElasticSearchProperties properties) {
        RestClientBuilder builder = RestClient.builder(convertToHttpHosts(properties.toURLs()));

        if (properties.getUsername() != null && properties.getPassword() != null) {
            log.info(
                    "Configure ElasticSearch with BASIC authentication. User:{}",
                    properties.getUsername());
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(
                    httpClientBuilder ->
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        } else {
            log.info("Configure ElasticSearch with no authentication.");
        }
        return builder;
    }

    @Bean
    public IndexDAO es7IndexDAO(
            RestClientBuilder restClientBuilder,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper) {
        String url = properties.getUrl();
        return new ElasticSearchRestDAOV7(restClientBuilder, properties, objectMapper);
    }

    private HttpHost[] convertToHttpHosts(List<URL> hosts) {
        return hosts.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getProtocol()))
                .toArray(HttpHost[]::new);
    }
}
