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

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MetricsCollector {

    static final CompositeMeterRegistry compositeRegistry = new CompositeMeterRegistry();
    private static final MeterRegistry simpleRegistry = new SimpleMeterRegistry();

    public MetricsCollector(MeterRegistry... registries) {
        log.info("=========");
        log.info("Conductor configured with {} metrics registries", registries.length);
        for (MeterRegistry registry : registries) {
            log.info("Metrics registry: {}", registry);
        }
        log.info(
                "check https://docs.micrometer.io/micrometer/reference/ for configuration options");
        log.info("=========");
        compositeRegistry.add(simpleRegistry);
        for (MeterRegistry meterRegistry : registries) {
            compositeRegistry.add(meterRegistry);
        }
    }

    public static MeterRegistry getMeterRegistry() {
        return compositeRegistry;
    }
}
