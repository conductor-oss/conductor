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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentService#computePruneCutoffEpochMs(int, Instant)} — the guard against
 * the prune endpoint deleting recent executions (issue #1331). Deterministic — pure time arithmetic
 * against a fixed clock.
 */
class AgentServicePruneCutoffTest {

    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");

    @Test
    void normalAgeYieldsThePlainCutoff() {
        long cutoff = AgentService.computePruneCutoffEpochMs(30, NOW);

        assertThat(cutoff).isEqualTo(NOW.minus(30, ChronoUnit.DAYS).toEpochMilli());
        assertThat(cutoff).isPositive();
    }

    @Test
    void hugeAgeClampsToEpochStartInsteadOfGoingNegative() {
        // 99999 days before 2026 is far before the epoch: unclamped this is a negative epoch
        // value, which the search backend matched against recent executions and deleted them
        long cutoff = AgentService.computePruneCutoffEpochMs(99_999, NOW);

        assertThat(cutoff).isZero();
    }

    @Test
    void largestAgeStillYieldingPositiveCutoffIsNotClamped() {
        long daysSinceEpoch = ChronoUnit.DAYS.between(Instant.EPOCH, NOW);

        long cutoff = AgentService.computePruneCutoffEpochMs((int) daysSinceEpoch, NOW);

        assertThat(cutoff).isNotNegative();
        assertThat(cutoff).isEqualTo(NOW.minus(daysSinceEpoch, ChronoUnit.DAYS).toEpochMilli());
    }

    @Test
    void zeroAndNegativeAgesAreRejected() {
        // A cutoff at or after "now" matches every terminal execution: reject instead of delete
        assertThatThrownBy(() -> AgentService.computePruneCutoffEpochMs(0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("olderThanDays");
        assertThatThrownBy(() -> AgentService.computePruneCutoffEpochMs(-5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("olderThanDays");
    }
}
