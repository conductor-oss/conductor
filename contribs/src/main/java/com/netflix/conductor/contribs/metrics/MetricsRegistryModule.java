/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.netflix.conductor.contribs.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.metrics3.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton metrics registry.
 */
public class MetricsRegistryModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsRegistryModule.class);

    public static final MetricRegistry METRIC_REGISTRY = new MetricRegistry();
    public static final MetricsRegistry METRICS_REGISTRY = new MetricsRegistry(Clock.SYSTEM, METRIC_REGISTRY);
    static {
        Spectator.globalRegistry().add(METRICS_REGISTRY);
    }

    @Override
    protected void configure() {
        LOGGER.info("Metrics registry module initialized");
        bind(MetricRegistry.class).toInstance(METRIC_REGISTRY);
        bind(MetricsRegistry.class).toInstance(METRICS_REGISTRY);
    }
}
