/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.metrics;

import org.junit.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.Assert.*;

public class MetricsCollectorTest {

    @Test
    public void constructor_wiresRegistryIntoMonitors() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new MetricsCollector(registry);

        Monitors.getCounter("mc_test_counter", "source", "test").increment(7);

        Counter counter = registry.find("mc_test_counter").counter();
        assertNotNull("Counter should be visible in the wired registry", counter);
        assertEquals(7.0, counter.count(), 0.001);
    }

    @Test
    public void getMeterRegistry_delegatesToMonitors() {
        assertSame(Monitors.getRegistry(), MetricsCollector.getMeterRegistry());
    }
}
