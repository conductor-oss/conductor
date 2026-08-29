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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI-module request for responding to a waiting native Conductor agent execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConductorAgentRespondRequest {

    private String executionId;
    private Map<String, Object> body;

    /**
     * Credential values for the agent platform, already resolved by the engine. Carried here
     * because respond and cancel receive no task input of their own, so a stateless client would
     * otherwise have nothing to authenticate with.
     */
    private Map<String, String> credentials;

    /**
     * Provider-specific configuration from the originating task input (endpoint, assistant/agent
     * id, api version, ...). Carried for the same reason as {@code credentials}: it lets a client
     * re-derive where the run lives instead of remembering it in process.
     */
    private Map<String, Object> rawConfig;

    /**
     * The pending tool call this responds to, as reported by the last status. Bedrock needs the
     * action group name to shape its {@code returnControlInvocationResults}, and carrying it here
     * keeps that off the client's heap.
     */
    private Map<String, Object> pendingTool;

    /**
     * Every tool call the turn is blocked on, as reported by the last status. Lets a client check
     * that the reply covers all of them rather than padding one result across the set.
     */
    private List<Map<String, Object>> pendingTools;

    /**
     * Tool results keyed by {@code tool_call_id}. Set this when answering a turn that requested
     * more than one tool; every outstanding call must appear, since the provider will not resume
     * the run until each has an output. For a single-tool turn {@link #body} remains sufficient.
     */
    private Map<String, Object> toolResults;
}
