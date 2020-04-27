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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.codahale.metrics.Slf4jReporter;
import com.netflix.conductor.core.config.Configuration;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.slf4j.Logger;

public class LoggingMetricsModuleTest {

    @Test
    public void testCollector() {
        Logger logger = mock(Logger.class);
        doReturn(true).when(logger).isInfoEnabled(null);

        Configuration cfg = mock(Configuration.class);
        doReturn(1L).when(cfg).getLongProperty(anyString(), anyLong());

        LoggingMetricsModule.Slf4jReporterProvider logMetrics =
                new LoggingMetricsModule.Slf4jReporterProvider(cfg, MetricsRegistryModule.METRIC_REGISTRY, logger);

        MetricsRegistryModule.METRIC_REGISTRY.counter("test").inc();
        Slf4jReporter slf4jReporter = logMetrics.get();

        verify(logger, timeout(TimeUnit.SECONDS.toMillis(10))).isInfoEnabled(null);
    }
}