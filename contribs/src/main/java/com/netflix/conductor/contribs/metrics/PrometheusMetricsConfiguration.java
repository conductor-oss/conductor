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

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.prometheus.client.CollectorRegistry;

/**
 * Metrics prometheus module, sending all metrics to a Prometheus server.
 * <p>
 * Enable in config: conductor.metrics-prometheus.enabled=true
 * <p>
 */
@ConditionalOnProperty(value = "conductor.metrics-prometheus.enabled", havingValue = "true")
@Configuration
public class PrometheusMetricsConfiguration {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsConfiguration.class);

  public PrometheusMetricsConfiguration() {
    LOGGER.info("Prometheus metrics module initialized");
    final PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT,
                                                                              CollectorRegistry.defaultRegistry,
                                                                              Clock.SYSTEM);
    final MicrometerRegistry metricsRegistry = new MicrometerRegistry(meterRegistry);
    meterRegistry.config().meterFilter(new PrometheusRenameFilter());
    Spectator.globalRegistry().add(metricsRegistry);
  }
}
