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

import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentStatusResponse;
import org.conductoross.conductor.common.metadata.agent.CompileResponse;

/**
 * Fallback used when the AgentSpan control-plane is not installed.
 *
 * <p>Remote A2A operations remain available; only {@code agentType=conductor} operations fail.
 */
public final class UnavailableAgentClient implements AgentClient {

    private static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException(
                "The Conductor agent control-plane is not available in this runtime");
    }

    @Override
    public CompileResponse compileAgent(AgentStartRequest request) {
        throw unavailable();
    }

    @Override
    public AgentStartResponse deployAgent(AgentStartRequest request) {
        throw unavailable();
    }

    @Override
    public AgentStartResponse startAgent(AgentStartRequest request) {
        throw unavailable();
    }

    @Override
    public AgentStatusResponse getAgentStatus(String executionId) {
        throw unavailable();
    }

    @Override
    public Map<String, Object> getExecution(String executionId) {
        throw unavailable();
    }

    @Override
    public Map<String, Object> listExecutions(Map<String, Object> params) {
        throw unavailable();
    }

    @Override
    public void respond(String executionId, Map<String, Object> body) {
        throw unavailable();
    }

    @Override
    public void stopAgent(String executionId) {
        throw unavailable();
    }

    @Override
    public void signalAgent(String executionId, String message) {
        throw unavailable();
    }

    @Override
    public void cancelAgent(String executionId, String reason) {
        throw unavailable();
    }

    @Override
    public AgentEventStream streamSse(String executionId, String lastEventId) {
        throw unavailable();
    }
}
