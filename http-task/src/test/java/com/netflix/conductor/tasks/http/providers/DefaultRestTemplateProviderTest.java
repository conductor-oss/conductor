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

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.tasks.http.HttpTask;

import com.sun.net.httpserver.HttpServer;

import static org.junit.Assert.*;

public class DefaultRestTemplateProviderTest {

    /** Answers after this long. */
    private static final int RESPONSE_DELAY_MS = 1000;

    private static HttpServer server;
    private static String url;

    private final DefaultRestTemplateProvider provider =
            new DefaultRestTemplateProvider(Duration.ofMillis(150), Duration.ofMillis(100));

    @BeforeClass
    public static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        Thread.sleep(RESPONSE_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] body = "ok".getBytes();
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
    }

    @AfterClass
    public static void stopServer() {
        server.stop(0);
    }

    @Test
    public void callsWithDifferentTimeoutsShareOneClient() {
        HttpTask.Input slow = new HttpTask.Input();
        slow.setReadTimeOut(10_000);
        slow.setConnectionTimeOut(5_000);

        assertSame(
                httpClientOf(provider.getRestTemplate(new HttpTask.Input())),
                httpClientOf(provider.getRestTemplate(slow)));
    }

    @Test
    public void readTimeoutIsTheTasks() {
        assertTimesOut(get(500));
        assertEquals("ok", get(3_000));
        // the same pooled connection, with the shorter timeout again
        assertTimesOut(get(500));
    }

    @Test
    public void concurrentCallsKeepTheirOwnReadTimeout() {
        CompletableFuture<Object> shortCall = CompletableFuture.supplyAsync(() -> get(500));
        CompletableFuture<Object> longCall = CompletableFuture.supplyAsync(() -> get(3_000));

        assertTimesOut(shortCall.join());
        assertEquals("ok", longCall.join());
    }

    /** The response body, or the exception the call threw. */
    private Object get(int readTimeoutMillis) {
        HttpTask.Input input = new HttpTask.Input();
        input.setReadTimeOut(readTimeoutMillis);
        try {
            return provider.getRestTemplate(input).getForObject(url, String.class);
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static void assertTimesOut(Object result) {
        assertTrue(
                "expected a read timeout, got " + result,
                result instanceof ResourceAccessException);
    }

    private static Object httpClientOf(RestTemplate restTemplate) {
        return ((HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory())
                .getHttpClient();
    }
}
