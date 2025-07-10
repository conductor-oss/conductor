/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.os.config;

import java.net.URL;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.os.dao.index.OpenSearchRestDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenSearchProperties.class)
@Conditional(OpenSearchConditions.OpenSearchEnabled.class)
public class OpenSearchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchConfiguration.class);

    @Bean
    public RestClient restClient(RestClientBuilder restClientBuilder) {
        return restClientBuilder.build();
    }

    @Bean
    public RestClientBuilder osRestClientBuilder(OpenSearchProperties properties) {
        RestClientBuilder builder = RestClient.builder(convertToHttpHosts(properties.toURLs()));

        if (properties.getRestClientConnectionRequestTimeout() > 0) {
            builder.setRequestConfigCallback(
                    requestConfigBuilder ->
                            requestConfigBuilder.setConnectionRequestTimeout(
                                    properties.getRestClientConnectionRequestTimeout()));
        }

        if (properties.getUsername() != null && properties.getPassword() != null) {
            log.info(
                    "Configure OpenSearch with BASIC authentication. User:{}",
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
            log.info("Configure OpenSearch with no authentication.");
        }
        return builder;
    }

    @Primary
    @Bean
    public IndexDAO osIndexDAO(
            RestClientBuilder restClientBuilder,
            @Qualifier("osRetryTemplate") RetryTemplate retryTemplate,
            OpenSearchProperties properties,
            ObjectMapper objectMapper) {
        String url = properties.getUrl();
        return new OpenSearchRestDAO(restClientBuilder, retryTemplate, properties, objectMapper);
    }

    @Bean
    public RetryTemplate osRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(1000L);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        return retryTemplate;
    }

    private HttpHost[] convertToHttpHosts(List<URL> hosts) {
        return hosts.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getProtocol()))
                .toArray(HttpHost[]::new);
    }
}
