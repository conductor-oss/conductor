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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Tunable settings for the shared AI {@link okhttp3.OkHttpClient} (see {@link
 * AIHttpClientConfiguration}), bound from the {@code conductor.ai.http.*} prefix.
 *
 * <p>The defaults below are chosen for LLM/AI traffic rather than ordinary REST calls and apply
 * out of the box; operators only need to set a property to override one. Durations use Spring's
 * format (e.g. {@code 600s}, {@code 5m}).
 */
@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.http")
public class AIHttpClientProperties {

    /** Maximum time to establish a TCP connection to the provider. */
    private Duration connectTimeout = Duration.ofSeconds(60);

    /**
     * Maximum time to wait between bytes once a request is in flight, i.e. effectively the
     * time-to-first-byte budget for a response.
     *
     * <p>Defaults to 600s because LLM/reasoning calls over large contexts routinely take far
     * longer than OkHttp's 10s default read timeout to begin streaming a response; under-setting
     * this truncates long generations with a read timeout mid-call.
     */
    private Duration readTimeout = Duration.ofSeconds(600);

    /** Maximum time to wait between bytes while sending the request body. */
    private Duration writeTimeout = Duration.ofSeconds(60);

    /** Maximum number of idle connections kept in the shared connection pool. */
    private int maxIdleConnections = 50;

    /** How long an idle connection is kept alive in the pool before eviction. */
    private Duration keepAlive = Duration.ofMinutes(5);

    /**
     * Number of automatic retries for transient failures (0 disables the retry interceptor).
     */
    private int maxRetries = 3;
}
