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

/**
 * Agent control-plane client used by portable Conductor workers.
 *
 * <p>The boundary uses only AI-module DTOs. Conductor-Agents injects an in-process implementation
 * and translates those models to its service layer; external workers inject an SDK-backed adapter.
 */
public interface ConductorAgentClient extends AutoCloseable {

    /**
     * Agent type string that routes requests to this client (e.g. "conductor", "bedrock").
     *
     * <p>Defaults to {@code "conductor"} — the value of {@code A2AService.AGENT_TYPE_CONDUCTOR},
     * inlined to keep this boundary interface free of imports — so existing implementations of the
     * Conductor control plane keep compiling. Clients backing any other runtime must override.
     */
    default String agentType() {
        return "conductor";
    }

    ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request);

    /**
     * Polls the current status of a running agent execution.
     *
     * <p>The {@code request} carries the original task input — specifically {@code credentialRef}
     * and {@code rawConfig} — so that stateless implementations can re-authenticate and locate the
     * remote run without relying on in-process memory. This is required for correctness in
     * multi-replica deployments where the status poll may arrive on a different server instance
     * than the one that called {@link #startAgent}.
     */
    ConductorAgentStatusResponse getAgentStatus(String executionId, ConductorAgentRequest request);

    void respond(ConductorAgentRespondRequest request);

    void cancelAgent(ConductorAgentCancelRequest request);

    @Override
    default void close() {}
}
