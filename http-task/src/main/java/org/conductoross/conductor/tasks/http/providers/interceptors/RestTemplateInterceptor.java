/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.tasks.http.providers.interceptors;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Guards {@code HTTP} system task calls against Server-Side Request Forgery by rejecting requests
 * to hosts or IPs blocked via {@link
 * org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig
 * conductor.worker.http.block.*}. When no block rules are configured this interceptor is a
 * pass-through.
 */
@Component
public class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestTemplateInterceptor.class);

    private final HostAndIPChecker hostAndIPChecker;

    public RestTemplateInterceptor(HostAndIPChecker hostAndIPChecker) {
        this.hostAndIPChecker = hostAndIPChecker;
    }

    @Override
    public @NonNull ClientHttpResponse intercept(
            @NonNull HttpRequest request,
            @NonNull byte[] body,
            @NonNull ClientHttpRequestExecution execution)
            throws IOException {
        final String host = request.getURI().getHost();
        if (host != null && !hostAndIPChecker.isAllowed(host)) {
            LOGGER.warn("Blocked request to host/ip: {}. Request URI: {}", host, request.getURI());
            return forbiddenResponse();
        }

        return execution.execute(request, body);
    }

    private static ClientHttpResponse forbiddenResponse() {
        return new ClientHttpResponse() {
            @Override
            public @NonNull HttpStatus getStatusCode() {
                return HttpStatus.FORBIDDEN;
            }

            @Override
            public @NonNull String getStatusText() {
                return "FORBIDDEN";
            }

            @Override
            public void close() {}

            @Override
            public @NonNull InputStream getBody() {
                return new ByteArrayInputStream(
                        "{\"message\":\"HTTP calls to this host are blocked in this cluster\"}"
                                .getBytes());
            }

            @Override
            public @NonNull HttpHeaders getHeaders() {
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                return httpHeaders;
            }
        };
    }
}
