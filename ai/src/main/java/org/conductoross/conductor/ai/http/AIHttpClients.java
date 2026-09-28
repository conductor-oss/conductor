/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.http;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

/**
 * Single source of truth for building the AI {@link OkHttpClient}. Both the Spring-managed {@code
 * conductorAiHttpClient} bean and the fallbacks used when no client has been injected (tests,
 * manual instantiation) go through here so timeouts, connection pooling and retries stay
 * consistent.
 */
public final class AIHttpClients {

    private AIHttpClients() {}

    /** Builds an OkHttpClient configured from the given properties. */
    public static OkHttpClient build(AIHttpClientProperties props) {
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(props.getConnectTimeout())
                        .readTimeout(props.getReadTimeout())
                        .writeTimeout(props.getWriteTimeout())
                        .connectionPool(
                                new ConnectionPool(
                                        props.getMaxIdleConnections(),
                                        props.getKeepAlive().toMillis(),
                                        TimeUnit.MILLISECONDS));

        if (props.getMaxRetries() > 0) {
            builder.addInterceptor(new RetryInterceptor(props.getMaxRetries()));
        }

        return builder.build();
    }

    /**
     * Builds an OkHttpClient using the default {@link AIHttpClientProperties} values. Used as a
     * fallback when no Spring-managed client has been injected, e.g. in tests or when a provider is
     * constructed directly. Note that clients created this way are not tied to the Spring lifecycle
     * and so are not shut down via {@code @PreDestroy}.
     */
    public static OkHttpClient defaultClient() {
        return build(new AIHttpClientProperties());
    }
}
