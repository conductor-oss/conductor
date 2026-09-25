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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.metrics.MetricsCollector;
import com.netflix.conductor.metrics.Monitors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for two issues on the OTLP metrics export path:
 *
 * <ul>
 *   <li><b>#1418</b>: the OTLP registry dependency was dropped from the server classpath when the
 *       conductor-metrics module was retired (commit 396a09a4f, PR #1059), so {@code
 *       management.otlp.metrics.export.enabled=true} produced no registry at all.
 *   <li><b>#1534</b>: after Spring Boot was upgraded to 3.5, its {@code
 *       OtlpMetricsExportAutoConfiguration} builds the registry via {@code
 *       OtlpMeterRegistry.builder(...)}, a class that only exists in micrometer-registry-otlp
 *       1.15.x. With the registry pinned to 1.14.6 the server crashed at startup with {@code
 *       NoClassDefFoundError: OtlpMeterRegistry$Builder}.
 * </ul>
 *
 * <p>Both regressions live in Spring Boot's auto-configuration wiring, so this test drives that
 * wiring directly: it loads {@link OtlpMetricsExportAutoConfiguration} (plus {@link
 * MetricsAutoConfiguration} for the {@link io.micrometer.core.instrument.Clock} bean it depends on)
 * with {@code management.otlp.metrics.export.enabled=true} and asserts (1) the context starts
 * without failure — the #1534 crash — and the auto-configured {@link OtlpMeterRegistry} bean is
 * present (the #1418 regression), (2) it is wired into {@link Monitors} by {@link
 * MetricsCollector}, and (3) metrics recorded via {@link Monitors} are exported over HTTP to a
 * collector endpoint.
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
    public void otlpRegistryIsAutoConfiguredWiredIntoMonitorsAndExportsMetrics() {
        String url = "http://localhost:" + server.port() + "/v1/metrics";

        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(
                                        MetricsAutoConfiguration.class,
                                        OtlpMetricsExportAutoConfiguration.class))
                        .withUserConfiguration(MetricsCollectorConfig.class)
                        .withPropertyValues(
                                "management.otlp.metrics.export.enabled=true",
                                "management.otlp.metrics.export.url=" + url,
                                // Emit immediately so the counter is flushed without waiting for
                                // the default 60s step.
                                "management.otlp.metrics.export.step=1s");

        runner.run(
                context -> {
                    // #1534: on micrometer-registry-otlp 1.14.6 the auto-configuration throws
                    // NoClassDefFoundError building the registry, and the context fails to start.
                    assertThat(context).hasNotFailed();

                    // #1418: the OTLP registry must actually be auto-configured and present.
                    OtlpMeterRegistry registry = context.getBean(OtlpMeterRegistry.class);
                    assertNotNull("OTLP meter registry bean must be present", registry);

                    // MetricsCollector wires every MeterRegistry bean into Monitors, so a counter
                    // recorded through Monitors must be visible in the OTLP registry and exported
                    // to the collector endpoint.
                    Counter counter =
                            Monitors.getCounter("otlp_regression_test_counter", "source", "test");
                    counter.increment(3);

                    assertEquals(
                            3.0,
                            registry.find("otlp_regression_test_counter").counter().count(),
                            0.001);

                    // Closing the registry flushes any pending meters to the collector, so the
                    // embedded HTTP server receives the export request before the context tears
                    // down.
                    registry.close();
                });

        // The collector should have received at least one OTLP request carrying our counter. A
        // small bounded wait covers the async close flush.
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
     * Wires every auto-configured {@link MeterRegistry} bean into {@link Monitors} through {@link
     * MetricsCollector}, exactly as the server does at runtime.
     */
    @Configuration
    static class MetricsCollectorConfig {

        @Bean
        MetricsCollector metricsCollector(MeterRegistry... registries) {
            return new MetricsCollector(registries);
        }
    }

    /** Minimal HTTP server that records every POST received on /v1/metrics. */
    private static final class CapturingOtlpServer {
        private HttpServer httpServer;
        final AtomicInteger requests = new AtomicInteger(0);

        void start(int port) throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/v1/metrics", new CapturingHandler(this));
            httpServer.start();
        }

        int port() {
            return httpServer.getAddress().getPort();
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
