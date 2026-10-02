/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.core.events.scheduler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.core.events.queue.Message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * DRR fairness tests. Drives the dispatcher synchronously (no worker pool, no real handler) and
 * inspects which lane each pop came from. Synchronous control lets us assert exact interleaving.
 */
public class DrrDispatcherTest {

    private static void fill(EventLane lane, int n) {
        for (int i = 0; i < n; i++) {
            lane.buffer().offer(new Message(lane.event() + "-" + i, "p", null));
        }
    }

    /**
     * With two lanes and a quantum of 1, the dispatcher must alternate one message per lane per
     * round. After 10 rounds we should see exactly 10 dispatches from each lane regardless of how
     * many messages each one had buffered. This is the core "max-min fairness" property.
     */
    @Test
    public void equalQuantum_alternatesAcrossLanes() {
        AdaptiveSchedulerTestQueue qHot = new AdaptiveSchedulerTestQueue("hot");
        AdaptiveSchedulerTestQueue qCold = new AdaptiveSchedulerTestQueue("cold");
        EventLane hot = new EventLane("hot", qHot, 20_000);
        EventLane cold = new EventLane("cold", qCold, 100);
        fill(hot, 10_000);
        fill(cold, 10);

        DrrDispatcher drr = new DrrDispatcher(1);
        Map<String, Integer> counts = new HashMap<>();
        // Sink finishes synchronously so the limiter slot is released immediately, letting the
        // next round see the same per-lane Vegas budget.
        for (int round = 0; round < 10; round++) {
            drr.dispatch(
                    Arrays.asList(hot, cold),
                    (lane, ticket) -> {
                        counts.merge(lane.event(), 1, Integer::sum);
                        ticket.listener().onSuccess();
                    });
        }
        assertEquals(Integer.valueOf(10), counts.get("hot"));
        assertEquals(Integer.valueOf(10), counts.get("cold"));
    }

    /**
     * Skewed-load scenario: a hot lane with 10_000 buffered messages must NOT starve a cold lane
     * with 10. After enough rounds for the cold lane to drain completely (~10 rounds at q=1), the
     * cold lane's buffer should be empty while the hot lane still has the vast majority of its
     * messages outstanding.
     */
    @Test
    public void noisyNeighbor_coldLaneDrainedWhileHotStillHasWork() {
        AdaptiveSchedulerTestQueue qHot = new AdaptiveSchedulerTestQueue("hot");
        AdaptiveSchedulerTestQueue qCold = new AdaptiveSchedulerTestQueue("cold");
        EventLane hot = new EventLane("hot", qHot, 20_000);
        EventLane cold = new EventLane("cold", qCold, 100);
        fill(hot, 10_000);
        fill(cold, 10);

        DrrDispatcher drr = new DrrDispatcher(1);
        List<EventLane> lanes = Arrays.asList(hot, cold);
        // 15 rounds: enough for cold (10 msgs at 1/round) to be fully drained while the hot lane
        // is bounded by its Vegas limit (initial limit 2) and so cannot monopolise.
        for (int round = 0; round < 15; round++) {
            drr.dispatch(lanes, (lane, ticket) -> ticket.listener().onSuccess());
        }
        assertEquals("cold lane must be drained", 0, cold.buffer().size());
        assertTrue(
                "hot lane must still have messages outstanding under skew",
                hot.buffer().size() > 9_900);
    }

    /**
     * A lane that exhausts its Vegas limit in a single round must not block other lanes from being
     * visited. We simulate this by holding the limiter permits (never calling onSuccess) and
     * verifying the dispatcher still advances cold-lane work in the next round.
     */
    @Test
    public void hotLaneAtLimitDoesNotBlockColdLane() {
        AdaptiveSchedulerTestQueue qHot = new AdaptiveSchedulerTestQueue("hot");
        AdaptiveSchedulerTestQueue qCold = new AdaptiveSchedulerTestQueue("cold");
        EventLane hot = new EventLane("hot", qHot, 1_000);
        EventLane cold = new EventLane("cold", qCold, 100);
        fill(hot, 1_000);
        fill(cold, 5);

        DrrDispatcher drr = new DrrDispatcher(1);
        List<EventLane> lanes = Arrays.asList(hot, cold);

        // Round 1: do not release permits — both lanes will dispatch one each (limit starts at 2).
        Map<String, Integer> first = new HashMap<>();
        drr.dispatch(lanes, (lane, ticket) -> first.merge(lane.event(), 1, Integer::sum));
        // Round 2 onwards: hot has 2 in flight (its initial Vegas limit) so further hot dispatch
        // is blocked, but cold still has its full ceiling.
        for (int round = 0; round < 10; round++) {
            drr.dispatch(lanes, (lane, ticket) -> ticket.listener().onSuccess());
        }
        assertEquals(0, cold.buffer().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroQuantum() {
        new DrrDispatcher(0);
    }
}
