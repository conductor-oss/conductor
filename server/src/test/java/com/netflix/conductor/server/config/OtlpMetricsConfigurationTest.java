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
package com.netflix.conductor.server.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.metrics.MetricsCollector;
import com.netflix.conductor.metrics.Monitors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for issue #1418: OTLP metrics export regressed in v3.30.0 when the
 * conductor-metrics module was retired (commit 396a09a4f, PR #1059). The OTLP registry dependency
 * must remain on the server classpath so that {@code management.otlp.metrics.export.enabled=true}
 * produces a working {@link OtlpMeterRegistry} that gets wired into {@link Monitors} via {@link
 * MetricsCollector}.
 *
 * <p>This test constructs the {@link OtlpMeterRegistry} bean the same way Spring Boot's {@code
 * OtlpMetricsAutoConfiguration} does — the only thing it does not exercise is the
 * auto-configuration wiring itself, which is owned by Spring Boot. It verifies (1) the OTLP
 * registry class is resolvable on the classpath (the regression), (2) it is wired into {@link
 * Monitors} by {@link MetricsCollector}, and (3) metrics recorded via {@link Monitors} are exported
 * over HTTP to a collector endpoint.
 */
public class OtlpMetricsConfigurationTest {

    private CapturingOtlpServer server;

    @Before
    public void startServer() throws IOException {
        server = new CapturingOtlpServer();
        server.start(0); // ephemeral port
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void otlpRegistryIsWiredIntoMonitorsAndExportsMetrics() {
        ApplicationContextRunner runner =
                new ApplicationContextRunner().withUserConfiguration(TestConfig.class);
        runner.run(
                context -> {
                    OtlpMeterRegistry registry = context.getBean(OtlpMeterRegistry.class);
                    assertNotNull("OTLP meter registry bean must be present", registry);

                    // MetricsCollector wires every MeterRegistry bean into Monitors, so
                    // a counter recorded through Monitors must be visible in the OTLP
                    // registry and exported to the collector endpoint.
                    Counter counter =
                            Monitors.getCounter("otlp_regression_test_counter", "source", "test");
                    counter.increment(3);

                    assertEquals(
                            3.0,
                            registry.find("otlp_regression_test_counter").counter().count(),
                            0.001);

                    // Closing the registry flushes any pending meters to the collector,
                    // so the embedded HTTP server receives the export request before
                    // the context tears down.
                    registry.close();
                });

        // The collector should have received at least one OTLP request carrying our
        // counter. A small bounded wait covers the async close flush.
        try {
            server.awaitRequest(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(
                "OTLP collector should receive at least one export request",
                true,
                server.requests.get() > 0);
    }

    /**
     * Mirrors the server's metrics wiring: an {@link OtlpMeterRegistry} bean configured exactly as
     * Spring Boot's auto-configuration would, a {@link SimpleMeterRegistry} alongside it so the
     * composite path is exercised, and a {@link MetricsCollector} that wires both into {@link
     * Monitors}.
     */
    @Configuration
    static class TestConfig {

        @Bean
        OtlpMeterRegistry otlpMeterRegistry() {
            OtlpConfig config =
                    new OtlpConfig() {
                        @Override
                        public String url() {
                            return "http://localhost:"
                                    + CapturingOtlpServerHolder.PORT
                                    + "/v1/metrics";
                        }

                        // Emit immediately so the counter is flushed without waiting
                        // for the default 60s step.
                        @Override
                        public Duration step() {
                            return Duration.ofSeconds(1);
                        }

                        @Override
                        public String get(String key) {
                            return null;
                        }
                    };
            return new OtlpMeterRegistry(config, Clock.SYSTEM);
        }

        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        MetricsCollector metricsCollector(MeterRegistry... registries) {
            return new MetricsCollector(registries);
        }
    }

    /**
     * Holder used by {@link TestConfig} to resolve the collector port, which is only known after
     * the test server starts. Set in {@link OtlpMetricsConfigurationTest#startServer()}.
     */
    static final class CapturingOtlpServerHolder {
        static volatile int PORT;
    }

    /** Minimal HTTP server that records every POST received on /v1/metrics. */
    private static final class CapturingOtlpServer {
        private HttpServer httpServer;
        final AtomicInteger requests = new AtomicInteger(0);

        void start(int port) throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/v1/metrics", new CapturingHandler(this));
            httpServer.start();
            CapturingOtlpServerHolder.PORT = httpServer.getAddress().getPort();
        }

        InetSocketAddress address() {
            return httpServer.getAddress();
        }

        void awaitRequest(int timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (requests.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        }

        void stop() {
            if (httpServer != null) {
                httpServer.stop(0);
            }
        }
    }

    private static final class CapturingHandler implements HttpHandler {
        private final CapturingOtlpServer owner;

        CapturingHandler(CapturingOtlpServer owner) {
            this.owner = owner;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Drain the request body so the client does not get a broken pipe.
            try (var is = exchange.getRequestBody()) {
                byte[] buffer = new byte[1024];
                while (is.read(buffer) != -1) {
                    // discard
                }
            }
            owner.requests.incrementAndGet();
            byte[] response = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
