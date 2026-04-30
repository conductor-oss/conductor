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

import com.netflix.conductor.metrics.Monitors;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring component that registers all available {@link MeterRegistry} instances with
 * {@link Monitors} at startup. Monitors owns the composite registry; this class is
 * purely a wiring point between Spring-managed registries and the static Monitors API.
 */
@Slf4j
@Component
public class MetricsCollector {

    public MetricsCollector(MeterRegistry... registries) {
        log.info("=========");
        log.info("Conductor configured with {} metrics registries", registries.length);
        for (MeterRegistry registry : registries) {
            log.info("Metrics registry: {}", registry);
            Monitors.addMeterRegistry(registry);
        }
        log.info(
                "check https://docs.micrometer.io/micrometer/reference/ for configuration options");
        log.info("=========");
    }

    /** @deprecated Use {@link Monitors#getRegistry()} directly. */
    @Deprecated
    public static MeterRegistry getMeterRegistry() {
        return Monitors.getRegistry();
    }
}
