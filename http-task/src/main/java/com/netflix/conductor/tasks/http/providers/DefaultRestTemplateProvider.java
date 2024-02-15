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
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
        Duration timeout =
                Duration.ofMillis(
                        Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout));
        threadLocalRestTemplateBuilder.get().setReadTimeout(timeout);
        RestTemplate restTemplate =
                threadLocalRestTemplateBuilder.get().setReadTimeout(timeout).build();
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory();
        SocketConfig.Builder builder = SocketConfig.custom();
        builder.setSoTimeout(
                Timeout.of(
                        Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout),
                        TimeUnit.MILLISECONDS));
        requestFactory.setConnectTimeout(
                Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeout));
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
}
