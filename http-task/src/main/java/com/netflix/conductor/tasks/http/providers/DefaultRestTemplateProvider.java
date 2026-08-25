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
import java.util.Optional;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.tasks.http.HttpTask;

/**
 * Provider for a customized RestTemplateBuilder. This class provides a default {@link
 * RestTemplateBuilder} which can be configured or extended as needed.
 */
@Component
public class DefaultRestTemplateProvider implements RestTemplateProvider {

    private final ThreadLocal<RestTemplateBuilder> threadLocalRestTemplateBuilder;

    private final int defaultReadTimeout;
    private final int defaultConnectTimeout;

    public DefaultRestTemplateProvider(
            @Value("${conductor.tasks.http.readTimeout:150ms}") Duration readTimeout,
            @Value("${conductor.tasks.http.connectTimeout:100ms}") Duration connectTimeout) {
        this.threadLocalRestTemplateBuilder = ThreadLocal.withInitial(RestTemplateBuilder::new);
        this.defaultReadTimeout = (int) readTimeout.toMillis();
        this.defaultConnectTimeout = (int) connectTimeout.toMillis();
    }

    @Override
    public @NonNull RestTemplate getRestTemplate(@NonNull HttpTask.Input input) {
        int readTimeoutMillis =
                Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout);
        int connectTimeoutMillis =
                Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeout);
        Duration readTimeout = Duration.ofMillis(readTimeoutMillis);

        RestTemplate restTemplate =
                threadLocalRestTemplateBuilder.get().readTimeout(readTimeout).build();

        // The connect timeout belongs on the connection manager rather than the request factory:
        // Framework 7 dropped HttpComponentsClientHttpRequestFactory.setConnectTimeout, since
        // HttpClient 5 owns connection establishment.
        ConnectionConfig connectionConfig =
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMillis))
                        .setSocketTimeout(Timeout.ofMilliseconds(readTimeoutMillis))
                        .build();
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();
        RequestConfig requestConfig =
                RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMillis))
                        .build();
        HttpClient httpClient =
                HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(requestConfig)
                        .build();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
        return restTemplate;
    }
}
