/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.common.metadata.agent;

import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for POST /api/agent/start.
 *
 * <p>A start request identifies the agent either by {@link #name} and optional {@link #version}, or
 * by supplying inline construction details ({@link #agentConfig} or {@link #framework}). The two
 * forms are mutually exclusive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStartRequest {

    /** Name of a previously deployed agent definition. Also accepted as {@code agentName}. */
    @JsonAlias("agentName")
    private String name;

    /** Optional deployed agent version. The latest version is used when omitted. */
    private Integer version;

    private AgentConfig agentConfig;
    private String prompt;

    /**
     * Per-call model override for a registered agent identified by {@link #name}/{@link #version}.
     * When set, the stored agent config is recompiled with this model for this run only — the
     * persisted agent definition is never modified. Ignored on inline construction ({@link
     * #agentConfig}/{@link #framework}), where the desired model already belongs in that config.
     */
    private String model;

    private String sessionId;
    private List<String> media;
    private Map<String, Object> context;
    private String idempotencyKey;

    /**
     * Framework identifier for foreign agents (e.g. "openai", "google_adk"). Null for native
     * agents.
     */
    private String framework;

    /** Raw framework-specific agent config. Used when {@code framework} is non-null. */
    private Map<String, Object> rawConfig;

    /**
     * Reference to a server-registered skill package. Used with {@code framework="skill"} when the
     * caller wants the server to resolve the raw skill config from the skill registry instead of
     * sending it inline.
     */
    private Map<String, Object> skillRef;

    /** Per-call timeout override (seconds). Applied server-side to the workflow definition. */
    private Integer timeoutSeconds;

    /**
     * Per-execution isolation key for stateful agents.
     *
     * <p>When set, the server maps every worker tool task to this domain via {@code taskToDomain}
     * in the {@link StartWorkflowRequest}. The Python SDK registers the corresponding workers under
     * the same domain so that Conductor routes tasks exclusively to the workers that belong to this
     * execution, preventing cross-instance reply mixing when multiple concurrent instances of the
     * same agent script are running.
     */
    private String runId;

    /**
     * Optional deterministic plan for {@code Strategy.PLAN_EXECUTE} harnesses. The SDK forwards a
     * user-supplied {@code Plan}/dict here; the server stuffs it into {@code
     * workflow.input.static_plan} so PAC's extract_json INLINE picks it up as Case-0 (highest
     * priority) and discards whatever the planner LLM emitted. Lets callers replay a recorded plan
     * or run a fully deterministic pipeline without an LLM planner.
     */
    @JsonProperty("static_plan")
    private Map<String, Object> staticPlan;

    // ── External agent fields (Azure Foundry, AWS Bedrock) ────────────────────────────────────────

    /**
     * URL identifying the external agent endpoint. When set, the request is routed to the
     * appropriate external agent client instead of the native Conductor runtime.
     *
     * <ul>
     *   <li>Azure Foundry: {@code https://my-resource.cognitiveservices.azure.com} or
     *       {@code https://my-resource.openai.azure.com/openai}
     *   <li>AWS Bedrock: {@code bedrock://AGENT_ID/ALIAS_ID?region=us-east-1}
     * </ul>
     */
    @JsonProperty("agent_url")
    private String agentUrl;

    /**
     * Reference to a server-side credential secret. The secret content determines the auth mode
     * (API key, SP credentials, managed identity config, etc.). Omit to use
     * {@code DefaultAzureCredential} / default AWS credential chain.
     */
    private String credentialRef;

    /**
     * Dynamic OBO (On-Behalf-Of) — caller passes their live Azure AAD bearer token (without the
     * {@code Bearer } prefix). Conductor exchanges it for an Azure-scoped downstream token using its
     * own service principal via {@code OnBehalfOfCredential}. Takes precedence over
     * {@code credentialRef} when set.
     */
    @JsonProperty("user_assertion")
    private String userAssertion;

    /**
     * Dynamic OBO for AWS — OIDC JWT from the caller's identity provider. Conductor calls STS
     * {@code AssumeRoleWithWebIdentity} to obtain short-lived credentials for this invocation.
     * Requires {@code credentialRef.roleArn} to be set in the credential secret.
     */
    @JsonProperty("web_identity_token")
    private String webIdentityToken;
}
