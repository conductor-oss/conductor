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
import com.codahale.metrics.Slf4jReporter;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.conductor.core.config.Configuration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metrics logging reporter, dumping all metrics into an Slf4J logger.
 * <p>
 * Enable in config:
 * conductor.additional.modules=com.netflix.conductor.contribs.metrics.MetricsRegistryModule,com.netflix.conductor.contribs.metrics.LoggingMetricsModule
 * <p>
 * additional config:
 * com.netflix.conductor.contribs.metrics.LoggingMetricsModule.reportPeriodSeconds=15
 */
public class LoggingMetricsModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMetricsModule.class);

    // Dedicated logger for metrics
    // This way one can cleanly separate the metrics stream from rest of the logs
    private static final Logger METRICS_LOGGER = LoggerFactory.getLogger("ConductorMetrics");

    @Override
    protected void configure() {
        LOGGER.info("Logging metrics module initialized");
        bind(Slf4jReporter.class).toProvider(Slf4jReporterProvider.class).asEagerSingleton();
    }

    static class Slf4jReporterProvider implements Provider<Slf4jReporter> {

        public static final String PERIOD_CONFIG_KEY = LoggingMetricsModule.class.getName() + ".reportPeriodSeconds";
        public static final int DEFAULT_PERIOD = 30;

        private final Configuration config;
        private final MetricRegistry metrics3Registry;
        private final Logger logger;

        @Inject
        Slf4jReporterProvider(Configuration config, MetricRegistry metrics3Registry) {
            this(config, metrics3Registry, METRICS_LOGGER);
        }

        Slf4jReporterProvider(Configuration config, MetricRegistry metrics3Registry, Logger outputLogger) {
            this.config = config;
            this.metrics3Registry = metrics3Registry;
            this.logger = outputLogger;
        }

        @Override
        public Slf4jReporter get() {
            final Slf4jReporter reporter = Slf4jReporter.forRegistry(metrics3Registry)
                    .outputTo(logger)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build();

            long period = config.getLongProperty(PERIOD_CONFIG_KEY, DEFAULT_PERIOD);
            reporter.start(period, TimeUnit.SECONDS);
            LOGGER.info("Logging metrics reporter started, reporting every {} seconds", period);
            return reporter;
        }
    }
}
