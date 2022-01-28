/*
 * Copyright 2021 Netflix, Inc.
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.micrometer.MicrometerRegistry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@Import({PrometheusMetricsConfiguration.class})
@TestPropertySource(properties = {"conductor.metrics-prometheus.enabled=true"})
public class PrometheusMetricsConfigurationTest {

    @SuppressWarnings("unchecked")
    @Test
    public void testCollector() throws IllegalAccessException {
        final Optional<Field> registries =
                Arrays.stream(Spectator.globalRegistry().getClass().getDeclaredFields())
                        .filter(f -> f.getName().equals("registries"))
                        .findFirst();
        assertTrue(registries.isPresent());
        registries.get().setAccessible(true);

        List<Registry> meters = (List<Registry>) registries.get().get(Spectator.globalRegistry());
        assertTrue(meters.size() > 0);
        Optional<Registry> microMeterReg =
                meters.stream()
                        .filter(r -> r.getClass().equals(MicrometerRegistry.class))
                        .findFirst();
        assertTrue(microMeterReg.isPresent());
    }

    @TestConfiguration
    public static class TestConfig {

        /**
         * This bean will be injected in PrometheusMetricsConfiguration, which wraps it with a
         * MicrometerRegistry, and appends it to the global registry.
         *
         * @return a Prometheus registry instance
         */
        @Bean
        @Primary
        public MeterRegistry meterRegistry() {
            return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        }
    }
}
