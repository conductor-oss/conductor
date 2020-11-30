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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Metrics logging reporter, dumping all metrics into an Slf4J logger.
 * <p>
 * Enable in config: conductor.metrics.logger.enabled=true
 * <p>
 * additional config: conductor.metrics.logger.reportPeriodSeconds=15
 */
@ConditionalOnProperty(value = "conductor.metrics.logger.enabled", havingValue = "true")
@Configuration
public class LoggingMetricsConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMetricsConfiguration.class);

    // Dedicated logger for metrics
    // This way one can cleanly separate the metrics stream from rest of the logs
    private static final Logger METRICS_LOGGER = LoggerFactory.getLogger("ConductorMetrics");

    @Value("${conductor.metrics.logger.reportPeriodSeconds:30}")
    private long metricsReportInterval;

    @Bean
    public Slf4jReporter getSl4jReporter(MetricRegistry metrics3Registry) {

        final Slf4jReporter reporter = Slf4jReporter.forRegistry(metrics3Registry)
            .outputTo(METRICS_LOGGER)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();

        reporter.start(metricsReportInterval, TimeUnit.SECONDS);
        LOGGER.info("Logging metrics reporter started, reporting every {} seconds", metricsReportInterval);
        return reporter;
    }
}
