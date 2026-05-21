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

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.Assert.*;

public class MonitorsTest {

    @Test
    public void getRegistry_returnsNonNull() {
        assertNotNull(Monitors.getRegistry());
    }

    @Test
    public void addMeterRegistry_metersAreVisibleInAddedRegistry() {
        SimpleMeterRegistry extra = new SimpleMeterRegistry();
        Monitors.addMeterRegistry(extra);

        // Record something after adding the registry
        Monitors.getCounter("test_add_registry_counter", "tag", "value").increment(3);

        Counter counter = extra.find("test_add_registry_counter").counter();
        assertNotNull("Counter should be visible in newly added registry", counter);
        assertEquals(3.0, counter.count(), 0.001);
    }

    @Test
    public void getCounter_sameKeyReturnsSameInstance() {
        Counter c1 = Monitors.getCounter("test_counter_identity", "k", "v");
        Counter c2 = Monitors.getCounter("test_counter_identity", "k", "v");
        assertSame(c1, c2);
    }

    @Test
    public void getTimer_sameKeyReturnsSameInstance() {
        Timer t1 = Monitors.getTimer("test_timer_identity", "k", "v");
        Timer t2 = Monitors.getTimer("test_timer_identity", "k", "v");
        assertSame(t1, t2);
    }

    @Test
    public void getGauge_aliasMatchesGauge() {
        // getGauge and gauge should return the same AtomicDouble for the same key
        assertSame(
                Monitors.gauge("test_gauge_alias", "k", "v"),
                Monitors.getGauge("test_gauge_alias", "k", "v"));
    }

    @Test
    public void getDistributionSummary_aliasMatchesDistributionSummary() {
        assertSame(
                Monitors.distributionSummary("test_dist_alias", "k", "v"),
                Monitors.getDistributionSummary("test_dist_alias", "k", "v"));
    }

    @Test
    public void recordGauge_valueIsReflectedInRegistry() {
        SimpleMeterRegistry probe = new SimpleMeterRegistry();
        Monitors.addMeterRegistry(probe);

        Monitors.recordGauge("test_gauge_value", 42L);

        // gauge() in Monitors uses an AtomicDouble — the value set should be reflected
        assertNotNull(probe.find("test_gauge_value").gauge());
        assertEquals(42.0, probe.find("test_gauge_value").gauge().value(), 0.001);
    }

    @Test
    public void timerRecordsTime() {
        SimpleMeterRegistry probe = new SimpleMeterRegistry();
        Monitors.addMeterRegistry(probe);

        Monitors.getTimer("test_timer_record", "op", "read").record(100, TimeUnit.MILLISECONDS);

        Timer timer = probe.find("test_timer_record").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    public void metersWithDifferentTagsAreDistinct() {
        MeterRegistry registry = Monitors.getRegistry();

        Monitors.getCounter("test_tag_distinct", "env", "prod").increment(1);
        Monitors.getCounter("test_tag_distinct", "env", "staging").increment(2);

        // Two separate meters, not the same object
        assertNotSame(
                Monitors.getCounter("test_tag_distinct", "env", "prod"),
                Monitors.getCounter("test_tag_distinct", "env", "staging"));
    }
}
