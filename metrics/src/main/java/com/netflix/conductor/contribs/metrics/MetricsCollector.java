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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MetricsCollector {

    static final CompositeMeterRegistry compositeRegistry = new CompositeMeterRegistry();
    private static final MeterRegistry simpleRegistry = new SimpleMeterRegistry();
    private static final double[] percentiles = new double[] {0.5, 0.75, 0.90, 0.95, 0.99};

    private static final Map<String, AtomicDouble> gauges = new ConcurrentHashMap<>();
    private static final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private static final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public MetricsCollector(MeterRegistry... registries) {
        log.info("Metrics registries in the system: {}", registries.length);
        for (MeterRegistry registry : registries) {
            log.info("Metrics registry {} available", registry);
        }
        compositeRegistry.add(simpleRegistry);
        for (MeterRegistry meterRegistry : registries) {
            compositeRegistry.add(meterRegistry);
        }
    }

    public Counter counter(String name, String... tags) {
        String key = name + Arrays.toString(tags);
        return counters.computeIfAbsent(
                key, s -> Counter.builder(name).tags(tags).register(compositeRegistry));
    }

    public Timer timer(String name, String... tags) {
        String key = name + Arrays.toString(tags);
        return timers.computeIfAbsent(
                key,
                s ->
                        Timer.builder(name)
                                .tags(tags)
                                .publishPercentiles(percentiles)
                                .publishPercentileHistogram()
                                .register(compositeRegistry));
    }

    public AtomicDouble gauge(String name, String... tags) {
        String key = name + Arrays.toString(tags);

        return gauges.computeIfAbsent(
                key,
                s -> {
                    AtomicDouble value = new AtomicDouble(0);
                    Gauge.builder(name, () -> value).tags(tags).register(compositeRegistry);
                    return value;
                });
    }
}
