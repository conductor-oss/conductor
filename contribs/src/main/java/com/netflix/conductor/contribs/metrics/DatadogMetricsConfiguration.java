/*
 * Copyright 2022 Netflix, Inc.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.micrometer.MicrometerRegistry;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Metrics Datadog module, sending all metrics to a Datadog server.
 *
 * <p>Enable in config: conductor.metrics-datadog.enabled=true
 *
 * <p>Make sure your dependencies include both micrometer-registry-datadog &
 * spring-boot-starter-actuator
 */
@ConditionalOnProperty(value = "conductor.metrics-datadog.enabled", havingValue = "true")
@Configuration
public class DatadogMetricsConfiguration {

    public DatadogMetricsConfiguration(MeterRegistry meterRegistry) {
        final MicrometerRegistry metricsRegistry = new MicrometerRegistry(meterRegistry);
        Spectator.globalRegistry().add(metricsRegistry);
    }
}
