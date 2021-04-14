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

import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Spectator;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"conductor.metrics-prometheus.enabled=true"})
@SuppressWarnings("unchecked")
@Ignore // Test causes "OutOfMemoryError: GC overhead limit reached" error during build
public class PrometheusMetricsConfigurationTest {

    @Test
    public void testCollector() {
        new ApplicationContextRunner()
            .withPropertyValues("conductor.metrics-prometheus.enabled:true")
            .withUserConfiguration(PrometheusMetricsConfiguration.class)
            .run(context -> {
                final Optional<Field> registries = Arrays
                    .stream(Spectator.globalRegistry().getClass().getDeclaredFields())
                    .filter(f -> f.getName().equals("registries")).findFirst();
                Assert.assertTrue(registries.isPresent());
                registries.get().setAccessible(true);
                Assert.assertEquals(1, ((List<Meter>) registries.get().get(Spectator.globalRegistry())).size());
            });
    }
}
