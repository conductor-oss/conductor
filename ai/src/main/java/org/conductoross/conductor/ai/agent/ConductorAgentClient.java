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
 * <p>The boundary uses only AI-module DTOs. AgentSpan injects an in-process implementation and
 * translates those models to its service layer; external workers inject an SDK-backed adapter.
 */
public interface ConductorAgentClient extends AutoCloseable {

    ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request);

    ConductorAgentStatusResponse getAgentStatus(String executionId);

    void respond(ConductorAgentRespondRequest request);

    void cancelAgent(ConductorAgentCancelRequest request);

    @Override
    default void close() {}
}
