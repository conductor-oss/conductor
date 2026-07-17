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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentService#withClassifierFilter(String, String)} — the folding of the
 * optional classifier request parameter into Conductor's structured search query. Deterministic —
 * pure string logic.
 */
class AgentServiceClassifierFilterTest {

    @Test
    void nullOrBlankClassifierLeavesQueryUntouched() {
        assertThat(AgentService.withClassifierFilter("status = 'RUNNING'", null))
                .isEqualTo("status = 'RUNNING'");
        assertThat(AgentService.withClassifierFilter("status = 'RUNNING'", "  "))
                .isEqualTo("status = 'RUNNING'");
        assertThat(AgentService.withClassifierFilter(null, null)).isNull();
    }

    @Test
    void classifierOnlyBecomesTheQuery() {
        assertThat(AgentService.withClassifierFilter(null, "agent"))
                .isEqualTo("classifier IN (agent)");
        assertThat(AgentService.withClassifierFilter("", "agent"))
                .isEqualTo("classifier IN (agent)");
    }

    @Test
    void classifierIsAppendedToExistingQuery() {
        assertThat(AgentService.withClassifierFilter("status = 'RUNNING'", "agent"))
                .isEqualTo("status = 'RUNNING' AND classifier IN (agent)");
    }

    @Test
    void commaSeparatedValuesAreTrimmed() {
        assertThat(AgentService.withClassifierFilter(null, " agent , workflow "))
                .isEqualTo("classifier IN (agent,workflow)");
    }

    @Test
    void degenerateClassifierValueIsIgnored() {
        assertThat(AgentService.withClassifierFilter("status = 'RUNNING'", " , ,"))
                .isEqualTo("status = 'RUNNING'");
    }
}
