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
package com.netflix.conductor.contribs.metrics;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.metrics.Monitors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that MetricsCollector correctly wires Spring-managed MeterRegistry instances into
 * Monitors at startup, and that meters recorded via Monitors are visible in those registries.
 */
@SpringBootTest(classes = MetricsCollectorIntegrationTest.TestConfig.class)
class MetricsCollectorIntegrationTest {

    @Configuration
    static class TestConfig {
        @Bean
        public SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public MetricsCollector metricsCollector(MeterRegistry... registries) {
            return new MetricsCollector(registries);
        }
    }

    @Autowired private SimpleMeterRegistry simpleMeterRegistry;

    @Test
    void monitorsRegistry_isNotNull() {
        assertNotNull(Monitors.getRegistry());
    }

    @Test
    void metricsCollector_wiresSimpleMeterRegistryIntoMonitors() {
        // Record a counter via Monitors after Spring wires MetricsCollector
        Monitors.getCounter("integration_test_counter", "source", "spring").increment(5);

        // The SimpleMeterRegistry provided by Spring should now contain this meter
        var counter = simpleMeterRegistry.find("integration_test_counter").counter();
        assertNotNull(counter, "Counter should be visible in the Spring-wired SimpleMeterRegistry");
        assertEquals(5.0, counter.count(), 0.001);
    }

    @Test
    void timer_recordedViaMonitors_isVisibleInRegistry() {
        Monitors.getTimer("integration_test_timer", "op", "test")
                .record(200, TimeUnit.MILLISECONDS);

        var timer = simpleMeterRegistry.find("integration_test_timer").timer();
        assertNotNull(timer, "Timer should be visible in the wired registry");
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 200);
    }

    @Test
    void gauge_setViaMonitors_isVisibleInRegistry() {
        Monitors.getGauge("integration_test_gauge", "component", "test").set(99.0);

        var gauge = simpleMeterRegistry.find("integration_test_gauge").gauge();
        assertNotNull(gauge, "Gauge should be visible in the wired registry");
        assertEquals(99.0, gauge.value(), 0.001);
    }
}
