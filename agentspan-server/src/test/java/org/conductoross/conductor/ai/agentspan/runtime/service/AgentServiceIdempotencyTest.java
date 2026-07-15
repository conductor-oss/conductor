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

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentService}'s idempotency mechanism. No mocking framework (per
 * AGENTS.md): a hand-written {@link AgentService} subclass stands in for the twelve collaborators
 * (all null) and overrides the two source-of-truth lookup seams.
 *
 * <p>(a) verifies the pure, deterministic workflow-id derivation. (b) verifies the dedup
 * resolution: a re-issued start whose derived id already exists in the execution store resolves to
 * that same execution without depending on the (async, laggy) search index, and falls back to the
 * legacy correlationId search only for runs created before deterministic-id derivation existed.
 */
class AgentServiceIdempotencyTest {

    /**
     * Hand-written stand-in. The real {@code AgentService} constructor requires twelve
     * collaborators; we pass nulls since these tests only drive the derivation and resolution
     * logic. The two lookup seams are overridden to simulate execution-store / search-index
     * contents.
     */
    private static final class FakeAgentService extends AgentService {

        private WorkflowModel byId;
        private String legacySearchResult;

        FakeAgentService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        WorkflowModel existingWorkflowById(String workflowId) {
            return byId != null && byId.getWorkflowId().equals(workflowId) ? byId : null;
        }

        @Override
        String findExistingExecution(String workflowName, String idempotencyKey) {
            return legacySearchResult;
        }
    }

    // ── (a) deterministic derivation ────────────────────────────────

    @Test
    void sameKeyDerivesSameWorkflowId() {
        String key = "conductor-agent-wf1:refA:0";
        assertThat(AgentService.deriveWorkflowId(key))
                .isEqualTo(AgentService.deriveWorkflowId(key));
    }

    @Test
    void differentKeysDeriveDifferentWorkflowIds() {
        assertThat(AgentService.deriveWorkflowId("conductor-agent-wf1:refA:0"))
                .isNotEqualTo(AgentService.deriveWorkflowId("conductor-agent-wf1:refA:1"));
    }

    @Test
    void derivedWorkflowIdIsAValidUuid() {
        String derived = AgentService.deriveWorkflowId("conductor-agent-wf1:refA:0");
        // Round-trips through UUID.fromString => a valid workflow id, safe as the store's PK.
        assertThat(UUID.fromString(derived).toString()).isEqualTo(derived);
    }

    // ── (b) dedup resolution (source-of-truth first) ────────────────

    @Test
    void resolvesExistingRunByDerivedIdWithoutSearchIndex() {
        FakeAgentService svc = new FakeAgentService();
        String key = "conductor-agent-wf1:refA:0";
        String derived = AgentService.deriveWorkflowId(key);
        WorkflowModel existing = new WorkflowModel();
        existing.setWorkflowId(derived);
        svc.byId = existing;
        // Deliberately leave the legacy search unset: an exact id hit must NOT consult it.
        svc.legacySearchResult = "SHOULD_NOT_BE_RETURNED";

        assertThat(svc.findExistingExecutionByKey("agent", key, derived)).isEqualTo(derived);
    }

    @Test
    void fallsBackToLegacyCorrelationSearchWhenNoDerivedIdMatch() {
        FakeAgentService svc = new FakeAgentService();
        svc.byId = null;
        svc.legacySearchResult = "legacy-run-42";
        String key = "conductor-agent-wf1:refA:0";

        assertThat(svc.findExistingExecutionByKey("agent", key, AgentService.deriveWorkflowId(key)))
                .isEqualTo("legacy-run-42");
    }

    @Test
    void returnsNullWhenNoExistingRun() {
        FakeAgentService svc = new FakeAgentService();
        svc.byId = null;
        svc.legacySearchResult = null;
        String key = "conductor-agent-wf1:refA:0";

        assertThat(svc.findExistingExecutionByKey("agent", key, AgentService.deriveWorkflowId(key)))
                .isNull();
    }
}
