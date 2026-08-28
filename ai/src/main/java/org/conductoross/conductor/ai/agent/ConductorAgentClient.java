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

// Agent control-plane client used by portable Conductor workers.
// The boundary uses only AI-module DTOs. Conductor-Agents injects an in-process implementation
// and translates those models to its service layer; external workers inject an SDK-backed adapter.
public interface ConductorAgentClient extends AutoCloseable {

    // Agent type string that routes requests to this client (e.g. "conductor", "bedrock").
    // Defaults to "conductor" so existing Conductor control plane implementations keep compiling.
    // Clients backing any other runtime must override.
    default String agentType() {
        return "conductor";
    }

    ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request);

    // Polls the current status of a running agent execution.
    // The request carries the original task input (credentialRef, rawConfig) so stateless
    // implementations can re-authenticate on any replica without relying on in-process memory.
    ConductorAgentStatusResponse getAgentStatus(String executionId, ConductorAgentRequest request);

    void respond(ConductorAgentRespondRequest request);

    // Responds and, where the runtime already knows the outcome, returns it. Runtimes with a status
    // API return null (the default) and the caller polls as usual; Bedrock completes the turn
    // inside
    // respond() and has nowhere to keep the answer, so it returns it here instead.
    default ConductorAgentStatusResponse respondWithStatus(ConductorAgentRespondRequest request) {
        respond(request);
        return null;
    }

    void cancelAgent(ConductorAgentCancelRequest request);

    @Override
    default void close() {}
}
