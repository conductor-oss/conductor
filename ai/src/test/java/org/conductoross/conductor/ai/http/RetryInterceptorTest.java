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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.BufferedSink;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryInterceptorTest {

    private MockWebServer server;
    private OkHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client =
                new OkHttpClient.Builder()
                        .addInterceptor(new RetryInterceptor(3, 10))
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .writeTimeout(2, TimeUnit.SECONDS)
                        .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private Request buildRequest() {
        return new Request.Builder().url(server.url("/test")).build();
    }

    @Test
    void noRetryOn200() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(200, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void retriesOn503ThenSucceeds() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(200, response.code());
        }

        assertEquals(3, server.getRequestCount());
    }

    @Test
    void exhaustsRetriesAndReturnsLastResponse() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(503, response.code());
        }

        // 1 initial + 3 retries = 4 total requests
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void noRetryOn400() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(400));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(400, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void noRetryOn404() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(404));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(404, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void retriesOn429WithRetryAfterHeader() throws IOException {
        server.enqueue(
                new MockResponse().setResponseCode(429).addHeader("Retry-After", "1"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(200, response.code());
        }

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void retriesOn500() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(200, response.code());
        }

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void noRetryOn501() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(501));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(501, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void retriesOnIOExceptionThenSucceeds() throws IOException {
        // First request: socket disconnect (simulates network failure)
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        // Second request: success
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        try (Response response = client.newCall(buildRequest()).execute()) {
            assertEquals(200, response.code());
        }

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void noRetryForOneShotBody() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(503));

        RequestBody oneShotBody =
                new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.get("text/plain");
                    }

                    @Override
                    public boolean isOneShot() {
                        return true;
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        sink.writeUtf8("payload");
                    }
                };

        Request request =
                new Request.Builder().url(server.url("/test")).post(oneShotBody).build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(503, response.code());
        }

        // No retry — one-shot body cannot be replayed
        assertEquals(1, server.getRequestCount());
    }
}
