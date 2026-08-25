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

    @JsonProperty("static_plan")
    private Map<String, Object> staticPlan;

    private String credentialRef;

    // When true and userAssertion is set, the Azure Foundry client exchanges the caller's
    // Entra SSO token for a Foundry-scoped token via OBO (enterprise clusters only).
    private boolean useCallerIdentity;

    // The caller's Entra ID assertion (SSO token) for OBO exchange.
    // Named "userAssertion" to match the OAuth2 OBO spec parameter name.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String userAssertion;

    // Remote agent URL — same field name as A2A so all agent types use a consistent top-level
    // field.
    // Azure Foundry: https://my-resource.openai.azure.com/openai
    // Bedrock: bedrock://AGENTID/ALIASID  (optional ?region=us-west-2 query param)
    @JsonProperty("agent_url")
    private String agentUrl;
}
