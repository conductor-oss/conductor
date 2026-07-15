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
package org.conductoross.conductor.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * A real, scriptable {@link ConductorAgentRuntime} for the {@code conductor.ai.agent} test suite —
 * the in-process analogue of the a2a suite's {@code EmbeddedA2AAgent}. No mock framework is used
 * (per AGENTS.md); behaviour is driven purely by the public fields below.
 *
 * <p>Scriptable inputs:
 *
 * <ul>
 *   <li>{@link #startResult} — the snapshot returned by {@link #start}.
 *   <li>{@link #statusResult} — the snapshot returned by {@link #getStatus} (falls back to {@link
 *       #startResult} when unset), letting a test advance a run between poll cycles.
 *   <li>{@link #throwOnStatus} — when true, {@link #getStatus} throws, driving the poll-failure
 *       guard.
 * </ul>
 *
 * <p>Recorded outputs let tests assert what the delegate handed the runtime: {@link
 * #lastStartRequest}, {@link #lastRespondExecutionId}, {@link #lastRespondMessage}, {@link
 * #lastCancelExecutionId}, and {@link #lastCancelReason}.
 */
class FakeConductorAgentRuntime implements ConductorAgentRuntime {

    ConductorAgentExecution startResult;
    ConductorAgentExecution statusResult;
    boolean throwOnStatus;

    // When set, the corresponding method throws this instead of returning — lets a test drive both
    // the transient (any RuntimeException) and non-retryable (NonRetryableAgentException) branches.
    RuntimeException startException;
    RuntimeException statusException;
    RuntimeException respondException;

    ConductorAgentStartRequest lastStartRequest;
    String lastRespondExecutionId;
    Map<String, Object> lastRespondMessage;
    String lastCancelExecutionId;
    String lastCancelReason;

    @Override
    public ConductorAgentExecution start(ConductorAgentStartRequest request) {
        this.lastStartRequest = request;
        if (startException != null) {
            throw startException;
        }
        return startResult;
    }

    @Override
    public ConductorAgentExecution getStatus(String executionId) {
        if (statusException != null) {
            throw statusException;
        }
        if (throwOnStatus) {
            throw new RuntimeException("runtime unreachable");
        }
        return statusResult != null ? statusResult : startResult;
    }

    @Override
    public void respond(String executionId, Map<String, Object> message) {
        this.lastRespondExecutionId = executionId;
        this.lastRespondMessage = message;
        if (respondException != null) {
            throw respondException;
        }
    }

    @Override
    public void cancel(String executionId, String reason) {
        this.lastCancelExecutionId = executionId;
        this.lastCancelReason = reason;
    }

    @Override
    public List<ConductorAgentSummary> listAgents() {
        return List.of();
    }
}
