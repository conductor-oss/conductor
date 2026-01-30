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
package com.netflix.conductor.es8.config;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
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
import com.netflix.conductor.es8.dao.index.ElasticSearchRestDAOV8;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ElasticSearchProperties.class)
@Conditional(ElasticSearchConditions.ElasticSearchV8Enabled.class)
public class ElasticSearchV8Configuration {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchV8Configuration.class);

    @Bean
    public RestClient restClient(RestClientBuilder restClientBuilder) {
        return restClientBuilder.build();
    }

    @Bean
    public RestClientBuilder elasticRestClientBuilder(ElasticSearchProperties properties) {
        RestClientBuilder builder = RestClient.builder(convertToHttpHosts(properties.toURLs()));

        CredentialsProvider credentialsProvider = null;
        if (properties.getRestClientConnectionRequestTimeout() > 0) {
            builder.setRequestConfigCallback(
                    requestConfigBuilder ->
                            requestConfigBuilder.setConnectionRequestTimeout(
                                    properties.getRestClientConnectionRequestTimeout()));
        }

        if (properties.getUsername() != null && properties.getPassword() != null) {
            log.info(
                    "Configure ElasticSearch with BASIC authentication. User:{}",
                    properties.getUsername());
            credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            properties.getUsername(), properties.getPassword()));
        } else {
            log.info("Configure ElasticSearch with no authentication.");
        }

        SSLContext sslContext = null;
        if (properties.getTrustCertPath() != null && !properties.getTrustCertPath().isBlank()) {
            try {
                sslContext = buildSslContextFromCert(properties.getTrustCertPath().trim());
                log.info("Configured Elasticsearch REST client with custom trust certificate.");
            } catch (Exception e) {
                log.warn("Failed to load trust certificate for Elasticsearch REST client", e);
            }
        }

        if (credentialsProvider != null || sslContext != null) {
            CredentialsProvider finalCredentialsProvider = credentialsProvider;
            SSLContext finalSslContext = sslContext;
            builder.setHttpClientConfigCallback(
                    httpClientBuilder -> {
                        if (finalCredentialsProvider != null) {
                            httpClientBuilder.setDefaultCredentialsProvider(
                                    finalCredentialsProvider);
                        }
                        if (finalSslContext != null) {
                            httpClientBuilder.setSSLContext(finalSslContext);
                        }
                        return httpClientBuilder;
                    });
        }
        return builder;
    }

    private SSLContext buildSslContextFromCert(String certPath) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        var certificates = List.<Certificate>of();
        try (var inputStream = Files.newInputStream(Path.of(certPath))) {
            certificates =
                    certificateFactory.generateCertificates(inputStream).stream()
                            .map(Certificate.class::cast)
                            .toList();
        }
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("No certificates found at " + certPath);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        for (int i = 0; i < certificates.size(); i++) {
            trustStore.setCertificateEntry("conductor-es-cert-" + i, certificates.get(i));
        }
        return org.apache.http.ssl.SSLContexts.custom().loadTrustMaterial(trustStore, null).build();
    }

    @Primary // If you are including this project, it's assumed you want ES to be your indexing
    // mechanism
    @Bean
    public IndexDAO es8IndexDAO(
            RestClientBuilder restClientBuilder,
            @Qualifier("es8RetryTemplate") RetryTemplate retryTemplate,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper) {
        String url = properties.getUrl();
        return new ElasticSearchRestDAOV8(
                restClientBuilder, retryTemplate, properties, objectMapper);
    }

    @Bean
    public RetryTemplate es8RetryTemplate() {
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
