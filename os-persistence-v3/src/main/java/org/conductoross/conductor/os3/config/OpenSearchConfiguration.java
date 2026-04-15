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
package org.conductoross.conductor.os3.config;

import java.net.URL;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.conductoross.conductor.os3.dao.index.OpenSearchRestDAO;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.dao.IndexDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenSearchProperties.class)
@Conditional(OpenSearchConditions.OpenSearchV3Enabled.class)
public class OpenSearchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchConfiguration.class);

    private final Environment environment;

    public OpenSearchConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public RestClient restClient(RestClientBuilder restClientBuilder) {
        return restClientBuilder.build();
    }

    @Bean
    public RestClientBuilder osRestClientBuilder(OpenSearchProperties properties) {
        // Inject environment for backward compatibility with legacy properties
        properties.setEnvironment(environment);
        properties.init();

        RestClientBuilder builder = RestClient.builder(convertToHttpHosts(properties.toURLs()));

        if (properties.getRestClientConnectionRequestTimeout() > 0) {
            builder.setRequestConfigCallback(
                    requestConfigBuilder ->
                            requestConfigBuilder.setConnectionRequestTimeout(
                                    Timeout.ofMilliseconds(
                                            properties.getRestClientConnectionRequestTimeout())));
        }

        if (properties.getUsername() != null && properties.getPassword() != null) {
            log.info(
                    "Configure OpenSearch with BASIC authentication. User:{}",
                    properties.getUsername());
            final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // Set credentials for any auth scope (null host matches any)
            credentialsProvider.setCredentials(
                    new AuthScope(null, -1),
                    new UsernamePasswordCredentials(
                            properties.getUsername(), properties.getPassword().toCharArray()));
            builder.setHttpClientConfigCallback(
                    httpClientBuilder ->
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        } else {
            log.info("Configure OpenSearch with no authentication.");
        }
        return builder;
    }

    @Bean
    public OpenSearchTransport openSearchTransport(
            RestClient restClient, ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }

    @Primary
    @Bean
    public IndexDAO osIndexDAO(
            RestClient restClient,
            OpenSearchClient openSearchClient,
            @Qualifier("osRetryTemplate") RetryTemplate retryTemplate,
            OpenSearchProperties properties,
            ObjectMapper objectMapper) {
        String url = properties.getUrl();
        return new OpenSearchRestDAO(
                restClient, openSearchClient, retryTemplate, properties, objectMapper);
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
                .map(host -> new HttpHost(host.getProtocol(), host.getHost(), host.getPort()))
                .toArray(HttpHost[]::new);
    }
}
