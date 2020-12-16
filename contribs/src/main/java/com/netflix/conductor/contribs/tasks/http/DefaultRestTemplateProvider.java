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
package com.netflix.conductor.contribs.tasks.http;

import com.netflix.conductor.contribs.tasks.http.HttpTask.Input;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Provider for a customized RestTemplateBuilder. This class provides a default {@link RestTemplateBuilder} which can be
 * configured or extended as needed.
 */
@Component
public class DefaultRestTemplateProvider implements RestTemplateProvider {

    private final ThreadLocal<RestTemplate> threadLocalRestTemplate;

    private final int defaultReadTimeout;
    private final int defaultConnectTimeout;

    @Autowired
    public DefaultRestTemplateProvider(@Value("${http.task.read.timeout:150}") int readTimeout,
                                       @Value("${http.task.connect.timeout:100}") int connectTimeout) {
        this.threadLocalRestTemplate = ThreadLocal.withInitial(RestTemplate::new);
        this.defaultReadTimeout = readTimeout;
        this.defaultConnectTimeout = connectTimeout;
    }

    @Override
    public @Nonnull RestTemplate getRestTemplate(@Nonnull Input input) {
        RestTemplate restTemplate = threadLocalRestTemplate.get();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeout));
        requestFactory.setReadTimeout(Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout));
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
}
