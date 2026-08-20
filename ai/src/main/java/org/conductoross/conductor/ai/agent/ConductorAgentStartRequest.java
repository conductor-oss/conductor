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

    // Remote agent URL — same field name as A2A so all agent types use a consistent top-level
    // field.
    // Azure Foundry: https://my-resource.openai.azure.com/openai
    // Bedrock: bedrock://AGENTID/ALIASID  (optional ?region=us-west-2 query param)
    @JsonProperty("agent_url")
    private String agentUrl;

    // Dynamic OBO (On-Behalf-Of) — caller passes their live token at request time.
    // Azure: raw AAD bearer token (without "Bearer " prefix); Conductor exchanges it for an
    //        Azure-scoped token using its own service principal via OnBehalfOfCredential.
    // Mutually exclusive with credentialRef; userAssertion takes precedence when both are set.
    @JsonProperty("user_assertion")
    private String userAssertion;

    // AWS: OIDC JWT from the caller's identity provider; Conductor calls STS
    //      AssumeRoleWithWebIdentity to get short-lived creds for this invocation only.
    // Requires credentialRef.roleArn to be set (the role to assume on behalf of the caller).
    @JsonProperty("web_identity_token")
    private String webIdentityToken;
}
