/*
 * Copyright 2023 Conductor Authors.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Metrics logging reporter, dumping all metrics into an Slf4J logger.
 *
 * <p>Enable in config: conductor.metrics-logger.enabled=true
 *
 * <p>additional config: conductor.metrics-logger.reportInterval=15s
 */
@ConditionalOnProperty(value = "conductor.metrics-logger.enabled", havingValue = "true")
@Configuration
@Slf4j
public class LoggingMetricsConfiguration {

    @Bean
    public MeterRegistry getLoggingMeterRegistry() {
        return new LoggingMeterRegistry(log::info);
    }
}
