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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link OkHttpClientHttpRequestFactory}, the local replacement for Spring's removed {@code
 * OkHttp3ClientHttpRequestFactory}.
 *
 * <p>The point of these tests is not that a request succeeds — any factory would manage that. It is
 * that the caller's {@link OkHttpClient} is genuinely the transport, so the connection pool,
 * timeouts and interceptors configured on it still apply. Swapping in a stock factory (e.g. {@code
 * JdkClientHttpRequestFactory}) compiles and returns 200s while silently discarding all of that, so
 * {@link #interceptorOnSuppliedClientRuns()} is the test that would actually catch it.
 */
class OkHttpClientHttpRequestFactoryTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    private RestClient clientBackedBy(OkHttpClient okHttp) {
        return RestClient.builder()
                .requestFactory(new OkHttpClientHttpRequestFactory(okHttp))
                .baseUrl(server.url("/").toString())
                .build();
    }

    @Test
    void getReturnsStatusHeadersAndBody() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("X-Custom", "abc")
                        .setBody("{\"ok\":true}"));

        ResponseEntity<String> response =
                clientBackedBy(new OkHttpClient())
                        .get()
                        .uri("/probe")
                        .retrieve()
                        .toEntity(String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"ok\":true}", response.getBody());
        assertEquals("abc", response.getHeaders().getFirst("X-Custom"));
    }

    @Test
    void postSendsBodyAndContentType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("done"));

        clientBackedBy(new OkHttpClient())
                .post()
                .uri("/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"prompt\":\"hi\"}")
                .retrieve()
                .toBodilessEntity();

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/submit", recorded.getPath());
        assertEquals("{\"prompt\":\"hi\"}", recorded.getBody().readUtf8());
        assertTrue(recorded.getHeader("Content-Type").startsWith("application/json"));
    }

    /**
     * A POST with no body must still be accepted — OkHttp rejects body-requiring methods with a
     * null body, which is why the factory substitutes an empty one.
     */
    @Test
    void postWithNoBodyIsAccepted() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(202));

        ResponseEntity<Void> response =
                clientBackedBy(new OkHttpClient())
                        .post()
                        .uri("/trigger")
                        .retrieve()
                        .toBodilessEntity();

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("POST", server.takeRequest().getMethod());
    }

    @Test
    void requestHeadersArePropagated() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        clientBackedBy(new OkHttpClient())
                .get()
                .uri("/h")
                .header("Authorization", "Bearer token-123")
                .retrieve()
                .toBodilessEntity();

        assertEquals("Bearer token-123", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void serverErrorSurfacesAsSpringException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(
                HttpServerErrorException.class,
                () ->
                        clientBackedBy(new OkHttpClient())
                                .get()
                                .uri("/fail")
                                .retrieve()
                                .toBodilessEntity());
    }

    /**
     * The regression guard. An interceptor is configuration that lives only on the supplied {@link
     * OkHttpClient} — in production that is {@code RetryInterceptor} from {@link AIHttpClients}. If
     * a future change swaps this factory for a stock Spring one, the request still succeeds but the
     * interceptor never fires and this assertion fails.
     */
    @Test
    void interceptorOnSuppliedClientRuns() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        AtomicInteger intercepted = new AtomicInteger();
        OkHttpClient withInterceptor =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain -> {
                                    intercepted.incrementAndGet();
                                    return chain.proceed(chain.request());
                                })
                        .build();

        clientBackedBy(withInterceptor).get().uri("/intercepted").retrieve().toBodilessEntity();

        assertEquals(
                1, intercepted.get(), "the supplied OkHttpClient must be the actual transport");
    }
}
