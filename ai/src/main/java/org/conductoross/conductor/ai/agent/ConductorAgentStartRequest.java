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

import org.conductoross.conductor.common.metadata.agent.AgentConfig;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI-module request for starting a native Conductor agent execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConductorAgentStartRequest {

    private String name;
    private Integer version;
    private AgentConfig agentConfig;
    private String prompt;
    private String model;
    private String sessionId;
    private List<String> media;
    private Map<String, Object> context;
    private String idempotencyKey;
    private String framework;
    private Map<String, Object> rawConfig;
    private Map<String, Object> skillRef;
    private Integer timeoutSeconds;
    private String runId;

    @JsonAlias("staticPlan")
    @JsonProperty("static_plan")
    private Map<String, Object> staticPlan;

    private String credentialRef;

    /**
     * Run the agent as the caller rather than as the deployment's own identity. With {@link
     * #userAssertion}, the Azure Foundry client exchanges the caller's Entra SSO token for a
     * Foundry-scoped one. Enterprise clusters only; without both, credential-based auth is used.
     */
    private boolean useCallerIdentity;

    /**
     * The caller's Entra ID assertion. Named for the OAuth 2.0 on-behalf-of parameter it becomes.
     * Set by the SSO layer, not by a workflow definition.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String userAssertion;

    /**
     * Where the agent lives, as a top-level field so every agent type names it the same way A2A
     * does. Azure Foundry takes its endpoint URL; Bedrock takes {@code bedrock://AGENTID/ALIASID},
     * optionally with {@code ?region=}.
     */
    @JsonAlias("agentUrl")
    @JsonProperty("agent_url")
    private String agentUrl;
}
