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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.codahale.metrics.MetricRegistry;
import com.netflix.conductor.contribs.metrics.LoggingMetricsConfiguration.Slf4jReporterProvider;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"conductor.metrics-logger.enabled=true"})
@Ignore // Test causes "OutOfMemoryError: GC overhead limit reached" error during build
public class LoggingMetricsConfigurationTest {

    @Test
    public void testCollector() {
        Logger logger = spy(Logger.class);
        doReturn(true).when(logger).isInfoEnabled(any());

        new ApplicationContextRunner()
            .withPropertyValues("conductor.metrics-logger.enabled:true")
            .withUserConfiguration(MetricsRegistryConfiguration.class)
            .run(context -> {
                MetricRegistry metricRegistry = context.getBean(MetricRegistry.class);
                Slf4jReporterProvider reporterProvider = new Slf4jReporterProvider(metricRegistry, logger, 1);
                metricRegistry.counter("test").inc();

                reporterProvider.getReporter();
                verify(logger, timeout(TimeUnit.SECONDS.toMillis(10))).isInfoEnabled(null);
            });
    }
}
