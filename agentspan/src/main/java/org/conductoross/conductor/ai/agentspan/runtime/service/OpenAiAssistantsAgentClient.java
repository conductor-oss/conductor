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
 * Placeholder for a future OpenAI Assistants API client.
 *
 * <p>Uses a Thread + Run model: a Thread is a conversation session, a Run is a single execution
 * within it. {@code startAgent()} creates both. {@code getAgentStatus()} polls the Run status.
 * {@code respond()} calls the {@code submit_tool_outputs} endpoint when the Run is in {@code
 * requires_action} state. Auth is a plain API key resolved via {@link
 * org.conductoross.conductor.ai.agent.credentials.StaticTokenProvider}.
 *
 * <p>To implement: add {@code @Component} to activate auto-registration, inject {@link
 * org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService} and an
 * {@code OkHttpClient}, and replace each method body with the real OpenAI Assistants REST calls
 * against {@code https://api.openai.com/v1}.
 *
 * <p>rawConfig keys to support: {@code assistantId}, {@code baseUrl} (optional override for Azure
 * OpenAI or compatible endpoints).
 */
// @Component  -- uncomment when implemented
public class OpenAiAssistantsAgentClient implements ConductorAgentClient {

    @Override
    public String agentType() {
        return "openai-assistants";
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        throw new UnsupportedOperationException(
                "OpenAiAssistantsAgentClient is not yet implemented");
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(String executionId) {
        throw new UnsupportedOperationException(
                "OpenAiAssistantsAgentClient is not yet implemented");
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        throw new UnsupportedOperationException(
                "OpenAiAssistantsAgentClient is not yet implemented");
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        throw new UnsupportedOperationException(
                "OpenAiAssistantsAgentClient is not yet implemented");
    }
}
