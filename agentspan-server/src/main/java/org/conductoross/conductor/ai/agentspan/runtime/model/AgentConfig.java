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
package org.conductoross.conductor.ai.agentspan.runtime.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Root configuration DTO for an agent definition. Mirrors the Python Agent class fields for
 * server-side compilation.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentConfig {

    private String name;
    private String description;
    private String model;

    /** Custom base URL for the LLM provider (per-agent override). */
    private String baseUrl;

    /**
     * Instructions can be a plain string or a PromptTemplateRef object. When serialized from
     * Python, callable instructions are resolved to strings.
     */
    private Object instructions;

    private List<ToolConfig> tools;

    /** Recursive sub-agent definitions. */
    private List<AgentConfig> agents;

    @Builder.Default private String strategy = "handoff";

    /** Router can be an AgentConfig (agent-based) or a WorkerRef (function-based). */
    private Object router;

    private OutputTypeConfig outputType;
    private List<GuardrailConfig> guardrails;
    private MemoryConfig memory;

    private Integer maxTurns;

    private Integer maxTokens;

    /**
     * Token budget for context condensation. When the estimated prompt token count exceeds this
     * value, condensation fires proactively — even if well below the model's actual context window.
     */
    private Integer contextWindowBudget;

    @Builder.Default private int timeoutSeconds = 0;

    private Double temperature;

    /**
     * OpenAI reasoning models (o1, gpt-5-codex, etc.) accept "minimal" | "low" | "medium" | "high".
     * Forwarded to {@code ChatCompletion.reasoningEffort}; ignored by non-reasoning models.
     */
    private String reasoningEffort;

    /** Worker reference for stop_when callable. */
    private WorkerRef stopWhen;

    private TerminationConfig termination;
    private List<HandoffConfig> handoffs;
    private List<CallbackConfig> callbacks;

    /** Map of agent name -> list of allowed next agent names. */
    private Map<String, List<String>> allowedTransitions;

    private String introduction;
    private Map<String, Object> metadata;
    private CodeExecutionConfig codeExecution;
    private CliConfig cliConfig;

    /**
     * Controls whether parent conversation context is passed to this sub-agent. "none" = fresh
     * context (only the prompt), null/absent = inherit parent context.
     */
    private String includeContents;

    /** Extended thinking/reasoning config. */
    private ThinkingConfig thinkingConfig;

    /**
     * Whether the agent should plan before executing. Augments the system prompt with a "plan
     * first, then execute" preamble. Used by the Google ADK normalizer; unrelated to the {@link
     * #planner} sub-agent slot below.
     */
    private Boolean enablePlanning;

    /**
     * PLAN_EXECUTE: the agent that produces the JSON plan. Required when {@link #strategy} is
     * {@code "plan_execute"}. The planner can be a simple agent or a multi-agent (e.g. SEQUENTIAL
     * of explorer + planner). Replaces the old positional {@code agents.get(0)}.
     */
    private AgentConfig planner;

    /**
     * PLAN_EXECUTE: the agent that runs agentically when the plan can't compile or the compiled
     * SUB_WORKFLOW fails at execution. Optional — if absent, plan failures TERMINATE the workflow.
     * Replaces the old positional {@code agents.get(1)}.
     */
    private AgentConfig fallback;

    /** Tools that must be called before the agent can complete. */
    private List<String> requiredTools;

    /** Tool calls to execute before the first LLM turn. Results are injected into context. */
    private List<PrefillToolCallConfig> prefillTools;

    /**
     * Gate condition for conditional sequential pipelines. Can be a Map (declarative, e.g.
     * text_contains) or a WorkerRef (callable).
     */
    private Map<String, Object> gate;

    /** Agent-level credential names (e.g. ["GH_TOKEN", "AWS_ACCESS_KEY_ID"]). */
    private List<String> credentials;

    /** Max LLM turns for the fallback agent in PLAN_EXECUTE strategy. */
    private Integer fallbackMaxTurns;

    /**
     * Optional deterministic plan source for PLAN_EXECUTE strategy. A SIMPLE task is called after
     * the planner to read the plan from an external source (e.g. contextbook). If the planner's
     * text output fails extraction, this fallback source is tried. Format: {"tool": "tool_name",
     * "args": {"key": "value"}}.
     */
    private Map<String, Object> planSource;

    /**
     * PLAN_EXECUTE planner context: a list of text snippets and/or URLs whose contents are appended
     * to the planner's user prompt as a {@code ## Reference Context} block at runtime. URLs are
     * fetched <em>per planner invocation</em> (no compile-time fetch, no cache) so doc edits go
     * live without recompile.
     *
     * <p>Each entry has either {@code text} (inlined verbatim) or {@code url} (HTTP GET, body
     * included). URL entries may declare:
     *
     * <ul>
     *   <li>{@code headers}: arbitrary HTTP headers. Values may contain {@code ${CRED_NAME}}
     *       placeholders that resolve against the agent's credential store at request time — same
     *       shape as {@code ToolConfig.config.headers}, so e.g. Confluence/Notion bearer tokens
     *       work without a separate auth pipeline.
     *   <li>{@code required}: when {@code true} (default) a fetch failure fails the workflow; when
     *       {@code false} a {@code [doc unavailable]} marker is substituted and the planner runs on
     *       partial context.
     *   <li>{@code maxBytes}: per-doc truncation cap (default 16384). Larger responses are
     *       truncated with a {@code [doc truncated]} marker so a single oversized wiki page can't
     *       blow the planner's context window.
     * </ul>
     *
     * <p>Only meaningful when {@link #strategy} is {@code "plan_execute"}; the compiler emits HTTP
     * fetch tasks for URL entries inside the planner-route live branch (skipped when {@code
     * static_plan} is set, so the static-plan path stays free of fetch latency).
     */
    private List<Map<String, Object>> plannerContext;

    /**
     * Input/output field names whose values should be redacted in the execution history and UI.
     * Maps directly to Conductor's {@code WorkflowDef.maskedFields}.
     */
    private List<String> maskedFields;

    /** Whether this is an external agent (no model, references existing workflow). */
    @Builder.Default private boolean external = false;

    /**
     * Whether to append a final LLM synthesis step after specialist agents complete. Set to false
     * to pass specialist output through unchanged. Default true.
     *
     * <p>Modelled as nullable Boolean (no Builder.Default) so {@code @JsonInclude(NON_NULL)} keeps
     * the field out of serialized output when callers leave it unset — the SDKs only emit
     * ``synthesize`` on the wire when explicitly disabled, and the metadata.agentDef round-trip
     * must preserve that. {@link #isSynthesize()} treats null as ``true`` for compiler use.
     *
     * <p>Without this nullable-Boolean shape,
     * ``Suite16Synthesize.test_handoff_default_has_final_task`` fails: a primitive ``boolean
     * synthesize = true`` always serializes to JSON, so ``agentDef`` contains ``synthesize: true``
     * even when the caller never set the flag — violating the test's contract (omit unless
     * explicitly disabled).
     */
    private Boolean synthesize;

    /** True iff synthesize hasn't been explicitly disabled (treats null/unset as default true). */
    public boolean isSynthesize() {
        return synthesize == null || synthesize;
    }
}
