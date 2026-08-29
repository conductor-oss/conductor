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

import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;

/**
 * Placeholder for a future Google Vertex AI Agent Builder client. <b>Not registered</b> — there is
 * no {@code @Component}, so {@code agentType: vertex} is rejected as unsupported rather than served
 * by this class.
 *
 * <p>Vertex agents are A2A-native (Google created the protocol), so they are reachable today
 * without a bespoke client: use {@code agentType: a2a} with the agent's A2A endpoint as {@code
 * agentUrl}. A dedicated client is only worth adding for the parts A2A does not cover.
 *
 * <p>To implement: add {@code @Component}, add a {@code AGENT_TYPE_VERTEX} constant to {@link
 * org.conductoross.conductor.ai.a2a.A2AService} and return it from {@link #agentType()}, inject the
 * request's already-substituted {@code credentials}, and replace each method body with real Vertex
 * AI Agent Builder REST calls. Follow the pattern the other clients settled on: keep no per-run
 * state, return the provider's own session id as the executionId, and re-derive everything else
 * from {@code credentials} and {@code rawConfig} on each call, so any replica can serve any
 * request.
 *
 * <p>rawConfig keys to support: {@code projectId}, {@code location}, {@code agentId}, {@code
 * sessionId}. Auth is Workload Identity Federation or a service account key via the Google Auth
 * Library.
 */
// @Component  -- uncomment when implemented
public class VertexAiAgentClient implements ConductorAgentClient {

    @Override
    public String agentType() {
        return "vertex";
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        throw new UnsupportedOperationException("VertexAiAgentClient is not yet implemented");
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        throw new UnsupportedOperationException("VertexAiAgentClient is not yet implemented");
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        throw new UnsupportedOperationException("VertexAiAgentClient is not yet implemented");
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        throw new UnsupportedOperationException("VertexAiAgentClient is not yet implemented");
    }
}
