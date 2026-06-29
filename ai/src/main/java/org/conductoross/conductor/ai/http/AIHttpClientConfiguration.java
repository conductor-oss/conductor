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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;

/**
 * Spring configuration for the shared AI {@link OkHttpClient}.
 *
 * <p>Exposes a single application-wide client, {@link #conductorAiHttpClient}, used by all LLM/AI
 * provider calls. Its timeouts, connection pooling and retries are bound from {@link
 * AIHttpClientProperties} (prefix {@code conductor.ai.http.*}). The client is built once via {@link
 * AIHttpClients} and torn down on shutdown (see {@link #shutdown()}) so its dispatcher thread pool
 * and connection pool are released cleanly.
 */
@Configuration
public class AIHttpClientConfiguration {

    private OkHttpClient client;

    /**
     * The shared {@link OkHttpClient} bean for AI/LLM provider calls.
     *
     * <p>Configured from {@link AIHttpClientProperties} ({@code conductor.ai.http.*}) — notably a
     * generous read timeout suited to reasoning models over large contexts. Inject it by type, or
     * by name via {@code @Qualifier("conductorAiHttpClient")}.
     */
    @Bean
    public OkHttpClient conductorAiHttpClient(AIHttpClientProperties props) {
        client = AIHttpClients.build(props);
        return client;
    }

    /** Releases the client's dispatcher thread pool and connection pool when the context closes. */
    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
