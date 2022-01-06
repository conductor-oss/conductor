/*
 * Copyright 2020 Netflix, Inc.
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.metrics3.MetricsRegistry;

import com.codahale.metrics.MetricRegistry;

@ConditionalOnProperty(value = "conductor.metrics-logger.enabled", havingValue = "true")
@Configuration
public class MetricsRegistryConfiguration {

    public static final MetricRegistry METRIC_REGISTRY = new MetricRegistry();
    public static final MetricsRegistry METRICS_REGISTRY =
            new MetricsRegistry(Clock.SYSTEM, METRIC_REGISTRY);

    static {
        Spectator.globalRegistry().add(METRICS_REGISTRY);
    }

    @Bean
    public MetricRegistry metricRegistry() {
        return METRIC_REGISTRY;
    }

    @Bean
    public MetricsRegistry metricsRegistry() {
        return METRICS_REGISTRY;
    }
}
