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
 * Agent control-plane client used by portable Conductor workers.
 *
 * <p>The surface intentionally matches the Java SDK agent client while using Conductor-owned DTOs.
 * AgentSpan injects an in-process implementation; external workers inject an SDK-backed adapter.
 */
public interface AgentClient extends AutoCloseable {

    CompileResponse compileAgent(AgentStartRequest request);

    AgentStartResponse deployAgent(AgentStartRequest request);

    AgentStartResponse startAgent(AgentStartRequest request);

    AgentStatusResponse getAgentStatus(String executionId);

    Map<String, Object> getExecution(String executionId);

    Map<String, Object> listExecutions(Map<String, Object> params);

    void respond(String executionId, Map<String, Object> body);

    void stopAgent(String executionId);

    void signalAgent(String executionId, String message);

    void cancelAgent(String executionId, String reason);

    AgentEventStream streamSse(String executionId, String lastEventId);

    @Override
    default void close() {}
}
