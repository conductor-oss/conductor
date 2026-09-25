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
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;

/**
 * Placeholder for a future Google Vertex AI Agent Builder client.
 *
 * <p>Vertex agents are A2A-native (Google created the protocol). Auth uses Workload Identity
 * Federation or a service account key via the Google Auth Library. Sessions in Vertex map directly
 * to {@code executionId}.
 *
 * <p>To implement: add {@code @Component} to activate auto-registration, inject {@link
 * org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService} for
 * credentials, and replace each method body with the real Vertex AI Agent Builder REST calls.
 *
 * <p>rawConfig keys to support: {@code projectId}, {@code location}, {@code agentId}, {@code
 * sessionId}.
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
    public ConductorAgentStatusResponse getAgentStatus(String executionId) {
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
