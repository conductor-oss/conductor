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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@ConditionalOnProperty(value = "management.cloudwatch.metrics.export.enabled", havingValue = "true")
@Configuration
@Slf4j
public class CloudWatchMetricsConfiguration {

    @Bean
    public MeterRegistry getCloudWatchMetrics(
            @Value("${management.cloudwatch.metrics.export.namespace:conductor}")
                    String namespace) {
        CloudWatchConfig cloudWatchConfig =
                new CloudWatchConfig() {
                    @Override
                    public String get(String s) {
                        return null;
                    }

                    @Override
                    public String namespace() {
                        return namespace;
                    }
                };
        log.info("Using namespace '{}' for cloudwatch metrics", namespace);
        return new CloudWatchMeterRegistry(
                cloudWatchConfig, Clock.SYSTEM, CloudWatchAsyncClient.create());
    }
}
