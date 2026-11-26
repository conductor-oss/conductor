/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.tasks.http.providers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.tasks.http.HttpTask;

/**
 * Provides the {@link RestTemplate} for an HTTP task call. All calls share one pooled {@link
 * HttpClient}; each call's read and connect timeouts are set on its own request.
 */
@Component
public class DefaultRestTemplateProvider implements RestTemplateProvider {

    /** Pool size of the shared client. */
    private static final int MAX_CONNECTIONS = 500;

    private final int defaultReadTimeout;
    private final int defaultConnectTimeout;

    // Built once: a client per call opened a new connection every time, and new converters per
    // call re-scanned the classpath for Jackson modules.
    private final HttpClient httpClient =
            HttpClients.custom()
                    .setConnectionManager(
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setMaxConnTotal(MAX_CONNECTIONS)
                                    .setMaxConnPerRoute(MAX_CONNECTIONS)
                                    .build())
                    .evictIdleConnections(TimeValue.ofSeconds(30))
                    .build();
    private final List<HttpMessageConverter<?>> messageConverters =
            new RestTemplate().getMessageConverters();

    public DefaultRestTemplateProvider(
            @Value("${conductor.tasks.http.readTimeout:150ms}") Duration readTimeout,
            @Value("${conductor.tasks.http.connectTimeout:100ms}") Duration connectTimeout) {
        this.defaultReadTimeout = (int) readTimeout.toMillis();
        this.defaultConnectTimeout = (int) connectTimeout.toMillis();
    }

    @Override
    public @NonNull RestTemplate getRestTemplate(@NonNull HttpTask.Input input) {
        Timeout readTimeout =
                Timeout.ofMilliseconds(
                        Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout));
        Timeout connectTimeout =
                Timeout.ofMilliseconds(
                        Optional.ofNullable(input.getConnectionTimeOut())
                                .orElse(defaultConnectTimeout));
        RestTemplate restTemplate = new RestTemplate(messageConverters);
        restTemplate.setRequestFactory(
                new HttpComponentsClientHttpRequestFactory(httpClient) {
                    @Override
                    protected RequestConfig mergeRequestConfig(RequestConfig clientConfig) {
                        // HttpClient applies both per request, also on a pooled connection. The
                        // request's connect timeout is deprecated in favor of the connection
                        // manager's, which is one value for the whole pool; it is still honored.
                        return RequestConfig.copy(clientConfig)
                                .setConnectTimeout(connectTimeout)
                                .setResponseTimeout(readTimeout)
                                .build();
                    }
                });
        return restTemplate;
    }
}
