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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.conductoross.conductor.common.metadata.agent.ModelParser;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Compiles tool definitions into Conductor workflow task structures.
 *
 * <p>Mirrors the Python {@code tool_compiler.py}. Produces raw Conductor {@link WorkflowTask}
 * objects for tool routing, dynamic forking, enrichment, and tool filtering.
 */
public class ToolCompiler {

    private static final Logger logger = LoggerFactory.getLogger(ToolCompiler.class);

    /** Result of building tool call routing, including any tool-level guardrail metadata. */
    @Data
    @AllArgsConstructor
    public static class ToolCallRoutingResult {
        private WorkflowTask routerTask;
        private List<String> toolGuardrailRetryRefs;
        private List<String[]> toolGuardrailRefs; // [refName, isInline]
    }

    /**
     * Matches a {@code ${IDENTIFIER}} credential placeholder (dots allowed — {@code
     * ${GCP_SVC.project_id}} addresses a JSON path inside a JSON-valued secret, which conductor's
     * secret resolution supports).
     */
    private static final Pattern CREDENTIAL_PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.]*)\\}");

    /**
     * Matches the inert {@code #{NAME}} marker form (see {@link #rewriteCredentialPlaceholders}).
     */
    private static final Pattern CREDENTIAL_MARKER =
            Pattern.compile("#\\{([A-Za-z_][A-Za-z0-9_.]*)\\}");

    private static Map<String, Object> escapeCredentialPlaceholders(Map<?, ?> headers) {
        Map<String, Object> escaped = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : headers.entrySet()) {
            String v = String.valueOf(e.getValue());
            escaped.put(String.valueOf(e.getKey()), rewriteCredentialPlaceholders(v));
        }
        return escaped;
    }

    /**
     * Rewrite a {@code ${NAME}} credential placeholder to the inert transport form {@code #{NAME}}.
     *
     * <p>Two-form design: header configs travel through INLINE enrich/prepare scripts (as script
     * text or structured INLINE input), and conductor's {@code ParametersUtils} substitutes any
     * contiguous {@code ${workflow.secrets.NAME}} in an INLINE task's input to PLAINTEXT at that
     * task's hand-off — which would persist the secret via the script's output into the forked
     * tasks' inputs. The {@code #{NAME}} marker is invisible to both ParametersUtils passes, so it
     * rides safely through every hop; it is converted to the real {@code ${workflow.secrets.NAME}}
     * reference only at final placement into a real task's {@code inputParameters} — {@link
     * #secretRefHeaders} for statically-built tasks, and the {@code _sec()} helper inside the
     * enrich scripts for dynamically forked tasks. There conductor defers it at binding and
     * resolves it wire-only at the task's own hand-off.
     */
    private static String rewriteCredentialPlaceholders(String value) {
        return CREDENTIAL_PLACEHOLDER.matcher(value).replaceAll("#{$1}");
    }

    /**
     * Convert {@code #{NAME}} markers in a headers map to {@code ${workflow.secrets.NAME}}
     * references. Apply ONLY when placing headers directly into a real (non-INLINE) task's {@code
     * inputParameters} — conductor defers the reference at input binding and resolves it wire-only
     * at the task's own hand-off, so plaintext is never persisted.
     */
    private static Object secretRefHeaders(Object headers) {
        if (!(headers instanceof Map<?, ?> m)) {
            return headers;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String v = String.valueOf(e.getValue());
            out.put(
                    String.valueOf(e.getKey()),
                    CREDENTIAL_MARKER.matcher(v).replaceAll("\\${workflow.secrets.$1}"));
        }
        return out;
    }

    /**
     * Return a copy of {@code cfg} with credential placeholders in its {@code headers} entry
     * rewritten from {@code ${NAME}} to {@code ${workflow.secrets.NAME}}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> escapeHeadersInConfig(Map<String, Object> cfg) {
        Object headers = cfg.get("headers");
        if (!(headers instanceof Map<?, ?>)) {
            return cfg;
        }
        Map<String, Object> result = new LinkedHashMap<>(cfg);
        result.put("headers", escapeCredentialPlaceholders((Map<?, ?>) headers));
        return result;
    }

    /** Tool types whose execution is handled server-side (not by a worker). */
    private static final Set<String> MEDIA_TOOL_TYPES =
            Set.of("generate_image", "generate_audio", "generate_video", "generate_pdf");

    /** RAG tool types that map to Conductor RAG system tasks. */
    private static final Set<String> RAG_TOOL_TYPES = Set.of("rag_index", "rag_search");

    /** Maps SDK tool type strings to Conductor task type strings. */
    private static final Map<String, String> TYPE_MAP =
            Map.ofEntries(
                    Map.entry("worker", "SIMPLE"),
                    Map.entry("http", "HTTP"),
                    Map.entry("api", "HTTP"), // API tools execute as HTTP tasks
                    Map.entry("mcp", "CALL_MCP_TOOL"),
                    Map.entry("agent_tool", "SUB_WORKFLOW"),
                    Map.entry("human", "HUMAN"),
                    Map.entry("generate_image", "GENERATE_IMAGE"),
                    Map.entry("generate_audio", "GENERATE_AUDIO"),
                    Map.entry("generate_video", "GENERATE_VIDEO"),
                    Map.entry("rag_index", "LLM_INDEX_TEXT"),
                    Map.entry("rag_search", "LLM_SEARCH_INDEX"),
                    Map.entry("pull_workflow_messages", "PULL_WORKFLOW_MESSAGES"));

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Compile a list of {@link ToolConfig} definitions into tool spec maps suitable for passing to
     * the LLM's {@code tools} parameter.
     *
     * <p>Each returned map contains: {@code name}, {@code type}, {@code description}, {@code
     * inputSchema}, {@code outputSchema}, and optionally {@code configParams} (for MCP tools).
     *
     * @param tools the tool definitions to compile
     * @return list of tool spec maps
     */
    public List<Map<String, Object>> compileToolSpecs(List<ToolConfig> tools) {
        List<Map<String, Object>> specs = new ArrayList<>();

        for (ToolConfig tool : tools) {
            String toolType = tool.getToolType() != null ? tool.getToolType() : "worker";
            String conductorType = TYPE_MAP.getOrDefault(toolType, "SIMPLE");

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("name", tool.getName());
            spec.put("type", conductorType);
            spec.put("description", tool.getDescription());
            // Every AgentSpan spec is complete as compiled (name + description
            // + inputSchema inline). The marker tells spec consumers — notably
            // orkes-conductor's OrkesLLM — to hand it to the LLM as-is and
            // never resolve/replace it by name against integration stores.
            // Kept top-level for a future first-class ToolSpec field, and
            // duplicated into configParams at the end of this loop — that
            // copy is the one that survives today: conductor-ai's ToolSpec
            // has no selfDescribing field, so this top-level key is dropped
            // at deserialization, while configParams (Map<String,Object>)
            // rides through intact.
            spec.put("selfDescribing", true);

            if (tool.getInputSchema() != null) {
                spec.put("inputSchema", tool.getInputSchema());
            }
            if (tool.getOutputSchema() != null) {
                spec.put("outputSchema", tool.getOutputSchema());
            }
            if (tool.getMaxCalls() != null) {
                spec.put("maxCalls", tool.getMaxCalls());
            }

            // MCP tools need configParams with server info
            if ("mcp".equals(toolType) && tool.getConfig() != null) {
                Map<String, Object> configParams = new LinkedHashMap<>();
                configParams.put("mcpServer", tool.getConfig().getOrDefault("server_url", ""));
                Object headers = tool.getConfig().get("headers");
                if (headers != null) {
                    configParams.put("headers", headers);
                }
                spec.put("configParams", configParams);
            }

            // API tools need configParams with base URL and routing info
            if ("api".equals(toolType) && tool.getConfig() != null) {
                Map<String, Object> configParams = new LinkedHashMap<>();
                configParams.put("baseUrl", tool.getConfig().getOrDefault("base_url", ""));
                configParams.put("method", tool.getConfig().getOrDefault("method", "POST"));
                configParams.put("path", tool.getConfig().getOrDefault("path", ""));
                Object headers = tool.getConfig().get("headers");
                if (headers != null) {
                    configParams.put("headers", headers);
                }
                spec.put("configParams", configParams);
            }

            // Merge (never clobber — the MCP/API blocks above populate it
            // first) the selfDescribing marker into configParams; see the
            // top-level marker comment for why this copy is the one that
            // survives deserialization.
            @SuppressWarnings("unchecked")
            Map<String, Object> markerParams = (Map<String, Object>) spec.get("configParams");
            if (markerParams == null) {
                markerParams = new LinkedHashMap<>();
                spec.put("configParams", markerParams);
            }
            markerParams.put("selfDescribing", true);

            specs.add(spec);
        }

        logger.debug("Compiled {} tool specs", specs.size());
        return specs;
    }

    /**
     * Build a SwitchTask that routes based on the LLM's native {@code toolCalls} output.
     *
     * <p>The switch has two paths:
     *
     * <ul>
     *   <li>{@code "tool_call"} — tool calls present: enrich + dynamic fork
     *   <li><em>default</em> — no tool calls (final answer): no-op
     * </ul>
     *
     * @param agentName name of the owning agent (used for task reference names)
     * @param llmRef task reference name of the LLM_CHAT_COMPLETE task
     * @param hasApproval if true, an approval check is inserted before tool execution
     * @param model model string (e.g. "openai/gpt-4o") for approval normalization
     * @return the configured SwitchTask
     */
    public WorkflowTask buildToolCallRouting(
            String agentName, String llmRef, boolean hasApproval, String model) {
        return buildToolCallRouting(agentName, llmRef, null, hasApproval, model);
    }

    /**
     * Build a SwitchTask that routes based on the LLM's native {@code toolCalls} output.
     *
     * <p>Overload that accepts tool definitions for enrichment.
     *
     * @param agentName name of the owning agent
     * @param llmRef task reference name of the LLM_CHAT_COMPLETE task
     * @param tools tool definitions (used for enrichment config); may be null
     * @param hasApproval if true, an approval check is inserted before tool execution
     * @param model model string for approval normalization
     * @return the configured SwitchTask
     */
    public WorkflowTask buildToolCallRouting(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            boolean hasApproval,
            String model) {
        return buildToolCallRoutingWithResult(agentName, llmRef, tools, hasApproval, model)
                .getRouterTask();
    }

    /**
     * Build a SwitchTask that routes based on the LLM's native {@code toolCalls} output, including
     * tool-level guardrails if any tools define them.
     *
     * @return A {@link ToolCallRoutingResult} containing the router task and any tool guardrail
     *     metadata for wiring into the DoWhile loop.
     */
    public ToolCallRoutingResult buildToolCallRoutingWithResult(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            boolean hasApproval,
            String model) {
        agentName = AgentCompiler.toRef(agentName);
        List<String> retryRefs = new ArrayList<>();
        List<String[]> guardrailRefs = new ArrayList<>();

        String switchExpr =
                "$.toolCalls != null && $.toolCalls.length > 0 " + "? 'tool_call' : 'none'";

        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setName("SWITCH");
        switchTask.setTaskReferenceName(agentName + "_tool_router");
        switchTask.setType("SWITCH");
        switchTask.setExpression(switchExpr);
        switchTask.setEvaluatorType("graaljs");

        Map<String, Object> switchInput = new LinkedHashMap<>();
        switchInput.put("toolCalls", "${" + llmRef + ".output.toolCalls}");
        switchTask.setInputParameters(switchInput);

        // "tool_call" case
        List<ToolConfig> effectiveTools = tools != null ? tools : Collections.emptyList();
        List<WorkflowTask> toolCallTasks =
                buildToolCallCase(
                        agentName,
                        llmRef,
                        hasApproval,
                        model,
                        effectiveTools,
                        retryRefs,
                        guardrailRefs);
        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();
        decisionCases.put("tool_call", toolCallTasks);
        switchTask.setDecisionCases(decisionCases);

        // default case: empty (final answer, no-op)
        switchTask.setDefaultCase(Collections.emptyList());

        logger.debug(
                "Built tool call routing switch for agent '{}' (toolGuardrails={})",
                agentName,
                guardrailRefs.size());
        return new ToolCallRoutingResult(switchTask, retryRefs, guardrailRefs);
    }

    /**
     * Build an InlineTask that enriches HTTP/MCP/media tool calls with server-side configuration
     * baked in at compile time.
     *
     * <p>Returns a two-element array: {@code [enrichTask, outputRef]} where {@code outputRef} is
     * the expression string pointing to the enriched dynamic tasks list.
     *
     * @param agentName name of the owning agent
     * @param llmRef task reference name of the LLM task
     * @param tools the tool definitions (used to build config maps)
     * @param prefix optional prefix for task reference names
     * @return {@code Object[]{WorkflowTask enrichTask, String outputRef}}
     */
    public Object[] buildEnrichTask(
            String agentName, String llmRef, List<ToolConfig> tools, String prefix) {
        agentName = AgentCompiler.toRef(agentName);
        String p = (prefix != null && !prefix.isEmpty()) ? prefix : "";

        // Build config maps from tool definitions at compile time
        Map<String, Object> httpConfig = new LinkedHashMap<>();
        Map<String, Object> mcpConfig = new LinkedHashMap<>();
        Map<String, Object> mediaConfig = new LinkedHashMap<>();
        Map<String, Object> agentToolConfig = new LinkedHashMap<>();
        Map<String, Object> ragConfig = new LinkedHashMap<>();
        Map<String, Object> cliConfig = new LinkedHashMap<>();
        Map<String, Object> humanConfig = new LinkedHashMap<>();
        Map<String, Object> wmqConfig = new LinkedHashMap<>();

        if (tools != null) {
            Set<String> serverSideTypes =
                    new HashSet<>(
                            Set.of(
                                    "http",
                                    "mcp",
                                    "agent_tool",
                                    "cli",
                                    "generate_image",
                                    "generate_audio",
                                    "generate_video",
                                    "generate_pdf",
                                    "rag_index",
                                    "rag_search",
                                    "human",
                                    "pull_workflow_messages"));

            for (ToolConfig tool : tools) {
                String toolType = tool.getToolType() != null ? tool.getToolType() : "worker";
                if (!serverSideTypes.contains(toolType)) {
                    continue;
                }

                Map<String, Object> cfg =
                        tool.getConfig() != null ? tool.getConfig() : Collections.emptyMap();

                if ("http".equals(toolType)) {
                    httpConfig.put(tool.getName(), escapeHeadersInConfig(cfg));
                } else if ("cli".equals(toolType)) {
                    Map<String, Object> cliEntry = new LinkedHashMap<>();
                    cliEntry.put(
                            "allowedCommands",
                            cfg.getOrDefault("allowedCommands", Collections.emptyList()));
                    cliConfig.put(tool.getName(), cliEntry);
                } else if ("mcp".equals(toolType)) {
                    Map<String, Object> mcpEntry = new LinkedHashMap<>();
                    mcpEntry.put("mcpServer", cfg.getOrDefault("server_url", ""));
                    Object mcpHeaders = cfg.getOrDefault("headers", Collections.emptyMap());
                    mcpEntry.put(
                            "headers",
                            mcpHeaders instanceof Map<?, ?>
                                    ? escapeCredentialPlaceholders((Map<?, ?>) mcpHeaders)
                                    : mcpHeaders);
                    mcpConfig.put(tool.getName(), mcpEntry);
                } else if ("agent_tool".equals(toolType)) {
                    // workflowName is set by AgentService.registerAgentToolWorkflows()
                    String workflowName =
                            (String) cfg.getOrDefault("workflowName", tool.getName() + "_agent_wf");
                    Map<String, Object> atEntry = new LinkedHashMap<>();
                    atEntry.put("workflowName", workflowName);
                    // Pass through retry/resilience overrides from SDK config
                    if (cfg.containsKey("retryCount"))
                        atEntry.put("retryCount", cfg.get("retryCount"));
                    if (cfg.containsKey("retryDelaySeconds"))
                        atEntry.put("retryDelaySeconds", cfg.get("retryDelaySeconds"));
                    if (cfg.containsKey("optional")) atEntry.put("optional", cfg.get("optional"));
                    agentToolConfig.put(tool.getName(), atEntry);
                } else if (MEDIA_TOOL_TYPES.contains(toolType)) {
                    Map<String, Object> cfgCopy = new LinkedHashMap<>(cfg);
                    String taskType =
                            cfgCopy.containsKey("taskType")
                                    ? (String) cfgCopy.remove("taskType")
                                    : toolType.toUpperCase();
                    Map<String, Object> mediaEntry = new LinkedHashMap<>();
                    mediaEntry.put("taskType", taskType);
                    mediaEntry.put("defaults", cfgCopy);
                    mediaConfig.put(tool.getName(), mediaEntry);
                } else if (RAG_TOOL_TYPES.contains(toolType)) {
                    Map<String, Object> cfgCopy = new LinkedHashMap<>(cfg);
                    String taskType =
                            cfgCopy.containsKey("taskType")
                                    ? (String) cfgCopy.remove("taskType")
                                    : TYPE_MAP.getOrDefault(toolType, toolType.toUpperCase());
                    Map<String, Object> ragEntry = new LinkedHashMap<>();
                    ragEntry.put("taskType", taskType);
                    ragEntry.put("defaults", cfgCopy);
                    ragConfig.put(tool.getName(), ragEntry);
                } else if ("human".equals(toolType)) {
                    Map<String, Object> humanEntry = new LinkedHashMap<>();
                    humanEntry.put("displayName", agentName + " — " + tool.getName());
                    humanEntry.put("description", tool.getDescription());
                    humanConfig.put(tool.getName(), humanEntry);
                } else if ("pull_workflow_messages".equals(toolType)) {
                    Map<String, Object> wmqEntry = new LinkedHashMap<>();
                    wmqEntry.put("batchSize", cfg.getOrDefault("batchSize", 1));
                    wmqConfig.put(tool.getName(), wmqEntry);
                }
            }
        }

        String httpJson = JavaScriptBuilder.toJson(httpConfig);
        String mcpJson = JavaScriptBuilder.toJson(mcpConfig);
        String mediaJson = JavaScriptBuilder.toJson(mediaConfig);
        String agentToolJson = JavaScriptBuilder.toJson(agentToolConfig);
        String ragJson = JavaScriptBuilder.toJson(ragConfig);
        String cliJson = JavaScriptBuilder.toJson(cliConfig);
        String humanJson = JavaScriptBuilder.toJson(humanConfig);
        String wmqJson = JavaScriptBuilder.toJson(wmqConfig);

        // Build the set of all known tool names so the enrich script can
        // catch hallucinated tool names (LLM emits e.g. "find" when only
        // "shell" + "list_files" are exposed) and turn them into INLINE
        // error tasks instead of SCHEDULED-with-no-poller hangs.
        Map<String, Object> knownToolNames = new LinkedHashMap<>();
        if (tools != null) {
            for (ToolConfig t : tools) {
                if (t.getName() != null) knownToolNames.put(t.getName(), Boolean.TRUE);
            }
        }
        String knownToolNamesJson = JavaScriptBuilder.toJson(knownToolNames);

        String script =
                JavaScriptBuilder.enrichToolsScript(
                        httpJson,
                        mcpJson,
                        mediaJson,
                        agentToolJson,
                        ragJson,
                        cliJson,
                        humanJson,
                        wmqJson,
                        knownToolNamesJson);

        String enrichRef = agentName + "_" + p + "enrich_tools";

        WorkflowTask enrichTask = new WorkflowTask();
        enrichTask.setTaskReferenceName(enrichRef);
        enrichTask.setType("INLINE");

        Map<String, Object> enrichInput = new LinkedHashMap<>();
        enrichInput.put("evaluatorType", "graaljs");
        enrichInput.put("expression", script);
        enrichInput.put("toolCalls", "${" + llmRef + ".output.toolCalls}");
        enrichInput.put("agentState", "${workflow.variables._agent_state}");
        enrichInput.put("userPrompt", "${workflow.input.prompt}");
        enrichTask.setInputParameters(enrichInput);

        // InlineTask output is at output.result.*
        String outputRef = "${" + enrichRef + ".output.result.dynamicTasks}";

        return new Object[] {enrichTask, outputRef};
    }

    /**
     * Build a FORK_JOIN_DYNAMIC task wired to the given tool calls reference.
     *
     * <p>Uses the new-style {@code dynamicForkTasksParam} so the server reads {@code
     * inputParameters} directly from each task object. Each dynamic task carries its own input, so
     * no separate inputs map is needed.
     *
     * @param agentName name of the owning agent
     * @param toolCallsRef expression string pointing to the enriched dynamic tasks list
     * @param prefix optional prefix for task reference names
     * @return the configured FORK_JOIN_DYNAMIC WorkflowTask (with JOIN as a child)
     */
    public WorkflowTask buildDynamicFork(String agentName, String toolCallsRef, String prefix) {
        agentName = AgentCompiler.toRef(agentName);
        String p = (prefix != null && !prefix.isEmpty()) ? prefix : "";

        // Fork task
        WorkflowTask fork = new WorkflowTask();
        fork.setName("FORK_JOIN_DYNAMIC");
        fork.setTaskReferenceName(agentName + "_" + p + "fork");
        fork.setType("FORK_JOIN_DYNAMIC");
        fork.setDynamicForkTasksParam("dynamicTasks");
        fork.setDynamicForkTasksInputParamName("dynamicTasksInputs");

        Map<String, Object> forkInput = new LinkedHashMap<>();
        forkInput.put("dynamicTasks", toolCallsRef);
        forkInput.put("dynamicTasksInputs", new HashMap<>());
        fork.setInputParameters(forkInput);

        // Join task
        WorkflowTask join = new WorkflowTask();
        join.setName("JOIN");
        join.setTaskReferenceName(agentName + "_" + p + "fork_join");
        join.setType("JOIN");

        // Wire join as the join task for the fork
        fork.getDefaultCase(); // ensure initialized
        // The fork and join are returned together; caller adds both to the task list.
        // By convention, Conductor expects fork followed by join in the task list.
        // We set the join on the fork so the server knows the join reference.

        logger.debug(
                "Built dynamic fork '{}' with join '{}'",
                fork.getTaskReferenceName(),
                join.getTaskReferenceName());

        // Return both fork and join as a single fork task with defaultCase containing join
        // Actually, for FORK_JOIN_DYNAMIC, fork and join must be sequential in the parent list.
        // We return the fork; the caller should also add the join.
        // Store the join reference so callers can retrieve it.
        // Use a wrapper approach: return fork, and add join to fork's output via convention.

        // Per Conductor convention, we embed the join in fork's defaultCase or
        // return it separately. The simplest pattern: return the fork and let
        // buildForkChain append the join.
        // Store the join in a temporary holder.
        fork.setDefaultCase(List.of(join));

        return fork;
    }

    /**
     * Build the complete chain of tasks for executing tool calls: enrich + dynamic fork + join.
     *
     * @param agentName name of the owning agent
     * @param llmRef task reference name of the LLM task
     * @param tools the tool definitions
     * @param prefix optional prefix for task reference names
     * @return ordered list of WorkflowTasks (enrich, fork, join)
     */
    public List<WorkflowTask> buildForkChain(
            String agentName, String llmRef, List<ToolConfig> tools, String prefix) {
        String p = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";

        Object[] enrichResult = buildEnrichTask(agentName, llmRef, tools, p);
        WorkflowTask enrichTask = (WorkflowTask) enrichResult[0];
        String toolCallsRef = (String) enrichResult[1];

        WorkflowTask forkTask = buildDynamicFork(agentName, toolCallsRef, p);

        // Extract the join from the fork's defaultCase
        List<WorkflowTask> joinList = forkTask.getDefaultCase();
        WorkflowTask joinTask = (joinList != null && !joinList.isEmpty()) ? joinList.get(0) : null;
        forkTask.setDefaultCase(Collections.emptyList());

        List<WorkflowTask> chain = new ArrayList<>();
        chain.add(enrichTask);
        chain.add(forkTask);
        if (joinTask != null) {
            chain.add(joinTask);
            // Merge _state_updates from tool outputs and persist to workflow variable
            chain.addAll(buildStateMergeTasks(agentName, joinTask.getTaskReferenceName(), p));
        }

        return chain;
    }

    /**
     * Build runtime LLM filtering tasks for large tool sets.
     *
     * <p>When an MCP server exposes more tools than {@code maxTools}, this creates a two-task
     * pre-loop sequence:
     *
     * <ol>
     *   <li><b>FilterLLM</b> — asks a lightweight LLM to select the most relevant tools given the
     *       user's prompt.
     *   <li><b>FilterInline</b> — filters the full tool spec list by the selected names
     *       (server-side JavaScript).
     * </ol>
     *
     * @param agentName name of the owning agent
     * @param toolSpecs the full list of tool spec maps
     * @param provider LLM provider name (e.g. "openai")
     * @param model LLM model name (e.g. "gpt-4o")
     * @param maxTools maximum number of tools to select
     * @return {@code Object[]{List<WorkflowTask> tasks, String toolsRef}}
     */
    public Object[] buildToolFilter(
            String agentName,
            List<Map<String, Object>> toolSpecs,
            String provider,
            String model,
            int maxTools) {
        agentName = AgentCompiler.toRef(agentName);
        // Build tool catalog for the FilterLLM prompt
        StringBuilder catalogBuilder = new StringBuilder();
        for (Map<String, Object> spec : toolSpecs) {
            String name = (String) spec.get("name");
            String desc = spec.get("description") != null ? spec.get("description").toString() : "";
            catalogBuilder.append("- ").append(name).append(": ").append(desc).append("\n");
        }
        String catalogText = catalogBuilder.toString();

        String systemPrompt =
                "You are a tool selection assistant. Given a user query and a catalog "
                        + "of available tools, select the most relevant tools the AI agent will "
                        + "need.  Select at most "
                        + maxTools
                        + " tools.\n\n"
                        + "TOOL CATALOG:\n"
                        + catalogText
                        + "\n"
                        + "Respond with ONLY a JSON object: {\"selected_tools\": [\"tool_name_1\", \"tool_name_2\", ...]}";

        // ── FilterLLM: LLM_CHAT_COMPLETE task ────────────────────────────
        String filterLlmRef = agentName + "_filter_llm";

        WorkflowTask filterLlm = new WorkflowTask();
        filterLlm.setName("LLM_CHAT_COMPLETE");
        filterLlm.setTaskReferenceName(filterLlmRef);
        filterLlm.setType("LLM_CHAT_COMPLETE");

        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("message", systemPrompt);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("message", "${workflow.input.prompt}\n\nRespond in json format.");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMsg);
        messages.add(userMsg);

        Map<String, Object> filterLlmInput = new LinkedHashMap<>();
        filterLlmInput.put("llmProvider", provider);
        filterLlmInput.put("model", model);
        filterLlmInput.put("maxTokens", 4096);
        filterLlmInput.put("messages", messages);
        filterLlmInput.put("temperature", 0);
        filterLlmInput.put("jsonOutput", true);
        filterLlm.setInputParameters(filterLlmInput);

        // ── FilterInline: InlineTask to filter specs by selected names ───
        String allSpecsJson = JavaScriptBuilder.toJson(toolSpecs);
        String filterScript = JavaScriptBuilder.filterToolsScript(allSpecsJson);

        String filterInlineRef = agentName + "_filter_inline";

        WorkflowTask filterInline = new WorkflowTask();
        filterInline.setTaskReferenceName(filterInlineRef);
        filterInline.setType("INLINE");

        Map<String, Object> filterInlineInput = new LinkedHashMap<>();
        filterInlineInput.put("evaluatorType", "graaljs");
        filterInlineInput.put("expression", filterScript);
        filterInlineInput.put("selectedNames", "${" + filterLlmRef + ".output.result}");
        filterInline.setInputParameters(filterInlineInput);

        // InlineTask output is at output.result.*
        String toolsRef = "${" + filterInlineRef + ".output.result.tools}";

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(filterLlm);
        tasks.add(filterInline);

        return new Object[] {tasks, toolsRef};
    }

    // ── MCP Discovery ────────────────────────────────────────────────────

    /** Result of building discovery tasks (MCP, API, or combined). */
    public static class DiscoveryResult {
        private final List<WorkflowTask> preTasks;
        private final String toolsRef;
        private final String mcpConfigRef;
        private final String apiConfigRef;

        public DiscoveryResult(
                List<WorkflowTask> preTasks,
                String toolsRef,
                String mcpConfigRef,
                String apiConfigRef) {
            this.preTasks = preTasks;
            this.toolsRef = toolsRef;
            this.mcpConfigRef = mcpConfigRef;
            this.apiConfigRef = apiConfigRef;
        }

        /** Convenience constructor for MCP-only discovery (no apiConfigRef). */
        public DiscoveryResult(List<WorkflowTask> preTasks, String toolsRef, String mcpConfigRef) {
            this(preTasks, toolsRef, mcpConfigRef, null);
        }

        public List<WorkflowTask> getPreTasks() {
            return preTasks;
        }

        public String getToolsRef() {
            return toolsRef;
        }

        public String getMcpConfigRef() {
            return mcpConfigRef;
        }

        public String getApiConfigRef() {
            return apiConfigRef;
        }
    }

    /**
     * @deprecated Use {@link DiscoveryResult} instead.
     */
    @Deprecated
    public static class McpDiscoveryResult extends DiscoveryResult {
        public McpDiscoveryResult(
                List<WorkflowTask> preTasks, String toolsRef, String mcpConfigRef) {
            super(preTasks, toolsRef, mcpConfigRef, null);
        }
    }

    /**
     * Build pre-loop workflow tasks for MCP tool discovery.
     *
     * <p>Creates:
     *
     * <ol>
     *   <li>One {@code LIST_MCP_TOOLS} task per unique MCP server
     *   <li>An {@code INLINE} prepare task that merges static specs with discovered MCP tools
     *   <li>A {@code SWITCH} task for threshold-based LLM filtering
     *   <li>An {@code INLINE} resolve task that normalizes the final tools reference
     * </ol>
     *
     * @param agentName agent name for task reference prefixes
     * @param mcpTools MCP tool configs (toolType="mcp")
     * @param staticToolSpecs compiled specs for non-MCP tools (baked into prepare script)
     * @param model model string for the filter LLM (e.g. "openai/gpt-4o")
     * @return discovery result with pre-tasks, tools ref, and mcpConfig ref
     */
    public DiscoveryResult buildMcpDiscoveryTasks(
            String agentName,
            List<ToolConfig> mcpTools,
            List<Map<String, Object>> staticToolSpecs,
            String model) {
        agentName = AgentCompiler.toRef(agentName);
        List<WorkflowTask> preTasks = new ArrayList<>();

        // ── 1. LIST_MCP_TOOLS tasks (one per unique server) ──────────
        // Deduplicate by server_url
        Map<String, Map<String, Object>> serverMap = new LinkedHashMap<>();
        int maxTools = 32; // default threshold
        for (ToolConfig tool : mcpTools) {
            Map<String, Object> cfg =
                    tool.getConfig() != null ? tool.getConfig() : Collections.emptyMap();
            String serverUrl = (String) cfg.getOrDefault("server_url", "");
            if (!serverUrl.isEmpty() && !serverMap.containsKey(serverUrl)) {
                Map<String, Object> serverInfo = new LinkedHashMap<>();
                serverInfo.put("serverUrl", serverUrl);
                Object mcpDiscH = cfg.getOrDefault("headers", Collections.emptyMap());
                serverInfo.put(
                        "headers",
                        mcpDiscH instanceof Map<?, ?>
                                ? escapeCredentialPlaceholders((Map<?, ?>) mcpDiscH)
                                : mcpDiscH);
                serverMap.put(serverUrl, serverInfo);
            }
            Object mt = cfg.get("max_tools");
            if (mt instanceof Number) {
                maxTools = ((Number) mt).intValue();
            }
        }

        List<Map<String, Object>> servers = new ArrayList<>(serverMap.values());
        List<String> listTaskRefs = new ArrayList<>();

        for (int i = 0; i < servers.size(); i++) {
            Map<String, Object> server = servers.get(i);
            String listRef = agentName + "_list_mcp_" + i;
            listTaskRefs.add(listRef);

            WorkflowTask listTask = new WorkflowTask();
            listTask.setName("LIST_MCP_TOOLS");
            listTask.setTaskReferenceName(listRef);
            listTask.setType("LIST_MCP_TOOLS");

            Map<String, Object> listInputs = new LinkedHashMap<>();
            listInputs.put("mcpServer", server.get("serverUrl"));
            // Direct task input — convert #{NAME} markers to wire-only-resolved secret refs.
            Object headers = secretRefHeaders(server.get("headers"));
            if (headers != null && !((Map<?, ?>) headers).isEmpty()) {
                listInputs.put("headers", headers);
            }
            listTask.setInputParameters(listInputs);
            preTasks.add(listTask);
        }

        // ── 2. INLINE prepare task ───────────────────────────────────
        String prepareRef = agentName + "_mcp_prepare";
        String staticSpecsJson = JavaScriptBuilder.toJson(staticToolSpecs);
        String serversJson = JavaScriptBuilder.toJson(servers);
        String prepareScript =
                JavaScriptBuilder.mcpPrepareScript(
                        staticSpecsJson, servers.size(), serversJson, maxTools);

        WorkflowTask prepareTask = new WorkflowTask();
        prepareTask.setTaskReferenceName(prepareRef);
        prepareTask.setType("INLINE");

        Map<String, Object> prepareInputs = new LinkedHashMap<>();
        prepareInputs.put("evaluatorType", "graaljs");
        prepareInputs.put("expression", prepareScript);
        for (int i = 0; i < listTaskRefs.size(); i++) {
            prepareInputs.put("discovered_" + i, "${" + listTaskRefs.get(i) + ".output.tools}");
        }
        prepareTask.setInputParameters(prepareInputs);
        preTasks.add(prepareTask);

        // ── 3. SWITCH task for threshold check ───────────────────────
        String switchRef = agentName + "_mcp_threshold";
        WorkflowTask thresholdSwitch = new WorkflowTask();
        thresholdSwitch.setType("SWITCH");
        thresholdSwitch.setTaskReferenceName(switchRef);
        thresholdSwitch.setEvaluatorType("graaljs");
        thresholdSwitch.setExpression("$.needsFilter == true ? 'filter' : 'direct'");

        Map<String, Object> switchInputs = new LinkedHashMap<>();
        switchInputs.put("needsFilter", "${" + prepareRef + ".output.result.needsFilter}");
        thresholdSwitch.setInputParameters(switchInputs);

        // "filter" case: catalog → filter LLM → filter inline
        List<WorkflowTask> filterTasks =
                buildDynamicFilterChain(agentName, prepareRef, model, maxTools);

        Map<String, List<WorkflowTask>> switchCases = new LinkedHashMap<>();
        switchCases.put("filter", filterTasks);
        thresholdSwitch.setDecisionCases(switchCases);

        // default: noop
        WorkflowTask noop = new WorkflowTask();
        noop.setType("SET_VARIABLE");
        noop.setTaskReferenceName(agentName + "_mcp_direct_noop");
        noop.setInputParameters(Map.of("_mcp_direct", true));
        thresholdSwitch.setDefaultCase(List.of(noop));

        preTasks.add(thresholdSwitch);

        // ── 4. INLINE resolve task ───────────────────────────────────
        String resolveRef = agentName + "_mcp_resolve";
        WorkflowTask resolveTask = new WorkflowTask();
        resolveTask.setTaskReferenceName(resolveRef);
        resolveTask.setType("INLINE");

        String filterInlineRef = agentName + "_mcp_filter_inline";
        Map<String, Object> resolveInputs = new LinkedHashMap<>();
        resolveInputs.put("evaluatorType", "graaljs");
        resolveInputs.put("expression", JavaScriptBuilder.mcpResolveScript());
        resolveInputs.put("filtered_tools", "${" + filterInlineRef + ".output.result.tools}");
        resolveInputs.put("prepared_tools", "${" + prepareRef + ".output.result.tools}");
        resolveInputs.put("mcpConfig", "${" + prepareRef + ".output.result.mcpConfig}");
        resolveInputs.put("apiConfig", "${" + prepareRef + ".output.result.apiConfig}");
        resolveTask.setInputParameters(resolveInputs);
        preTasks.add(resolveTask);

        String toolsRef = "${" + resolveRef + ".output.result.tools}";
        String mcpConfigRef = "${" + resolveRef + ".output.result.mcpConfig}";
        String apiConfigRef = "${" + resolveRef + ".output.result.apiConfig}";

        logger.debug(
                "Built MCP discovery tasks for agent '{}': {} servers, threshold={}",
                agentName,
                servers.size(),
                maxTools);

        return new DiscoveryResult(preTasks, toolsRef, mcpConfigRef, apiConfigRef);
    }

    // ── API Discovery ─────────────────────────────────────────────────────

    /**
     * Build pre-loop workflow tasks for API tool discovery.
     *
     * <p>Creates:
     *
     * <ol>
     *   <li>One {@code LIST_API_TOOLS} task per unique spec URL
     *   <li>An {@code INLINE} prepare task that merges static specs with discovered API tools
     *   <li>A {@code SWITCH} task for threshold-based LLM filtering
     *   <li>An {@code INLINE} resolve task that normalizes the final tools reference
     * </ol>
     *
     * @param agentName agent name for task reference prefixes
     * @param apiTools API tool configs (toolType="api")
     * @param staticToolSpecs compiled specs for non-API tools (baked into prepare script)
     * @param model model string for the filter LLM (e.g. "openai/gpt-4o")
     * @return discovery result with pre-tasks, tools ref, and apiConfig ref
     */
    public DiscoveryResult buildApiDiscoveryTasks(
            String agentName,
            List<ToolConfig> apiTools,
            List<Map<String, Object>> staticToolSpecs,
            String model) {
        agentName = AgentCompiler.toRef(agentName);
        List<WorkflowTask> preTasks = new ArrayList<>();

        // ── 1. LIST_API_TOOLS tasks (one per unique spec_url) ──────────
        // Deduplicate by spec_url
        Map<String, Map<String, Object>> specMap = new LinkedHashMap<>();
        int maxTools = 32; // default threshold
        for (ToolConfig tool : apiTools) {
            Map<String, Object> cfg =
                    tool.getConfig() != null ? tool.getConfig() : Collections.emptyMap();
            String specUrl = (String) cfg.getOrDefault("url", cfg.getOrDefault("spec_url", ""));
            if (!specUrl.isEmpty() && !specMap.containsKey(specUrl)) {
                Map<String, Object> specInfo = new LinkedHashMap<>();
                specInfo.put("specUrl", specUrl);
                Object apiHeaders = cfg.getOrDefault("headers", Collections.emptyMap());
                specInfo.put(
                        "headers",
                        apiHeaders instanceof Map<?, ?>
                                ? escapeCredentialPlaceholders((Map<?, ?>) apiHeaders)
                                : apiHeaders);
                specMap.put(specUrl, specInfo);
            }
            Object mt = cfg.get("max_tools");
            if (mt instanceof Number) {
                maxTools = ((Number) mt).intValue();
            }
        }

        List<Map<String, Object>> apiServers = new ArrayList<>(specMap.values());
        List<String> listTaskRefs = new ArrayList<>();

        for (int i = 0; i < apiServers.size(); i++) {
            Map<String, Object> server = apiServers.get(i);
            String listRef = agentName + "_list_api_" + i;
            listTaskRefs.add(listRef);

            WorkflowTask listTask = new WorkflowTask();
            listTask.setName("LIST_API_TOOLS");
            listTask.setTaskReferenceName(listRef);
            listTask.setType("LIST_API_TOOLS");

            Map<String, Object> listInputs = new LinkedHashMap<>();
            listInputs.put("specUrl", server.get("specUrl"));
            Object headers = secretRefHeaders(server.get("headers"));
            if (headers != null && !((Map<?, ?>) headers).isEmpty()) {
                listInputs.put("headers", headers);
            }
            listTask.setInputParameters(listInputs);
            preTasks.add(listTask);
        }

        // ── 2. INLINE prepare task ───────────────────────────────────
        String prepareRef = agentName + "_api_prepare";
        String staticSpecsJson = JavaScriptBuilder.toJson(staticToolSpecs);
        String apiServersJson = JavaScriptBuilder.toJson(apiServers);
        // Use apiPrepareScript with 0 MCP servers — API-only discovery
        String prepareScript =
                JavaScriptBuilder.apiPrepareScript(
                        staticSpecsJson, 0, "[]", apiServers.size(), apiServersJson, maxTools);

        WorkflowTask prepareTask = new WorkflowTask();
        prepareTask.setTaskReferenceName(prepareRef);
        prepareTask.setType("INLINE");

        Map<String, Object> prepareInputs = new LinkedHashMap<>();
        prepareInputs.put("evaluatorType", "graaljs");
        prepareInputs.put("expression", prepareScript);
        for (int i = 0; i < listTaskRefs.size(); i++) {
            prepareInputs.put("api_discovered_" + i, "${" + listTaskRefs.get(i) + ".output}");
        }
        prepareTask.setInputParameters(prepareInputs);
        preTasks.add(prepareTask);

        // ── 3. SWITCH task for threshold check ───────────────────────
        String switchRef = agentName + "_api_threshold";
        WorkflowTask thresholdSwitch = new WorkflowTask();
        thresholdSwitch.setType("SWITCH");
        thresholdSwitch.setTaskReferenceName(switchRef);
        thresholdSwitch.setEvaluatorType("graaljs");
        thresholdSwitch.setExpression("$.needsFilter == true ? 'filter' : 'direct'");

        Map<String, Object> switchInputs = new LinkedHashMap<>();
        switchInputs.put("needsFilter", "${" + prepareRef + ".output.result.needsFilter}");
        thresholdSwitch.setInputParameters(switchInputs);

        // "filter" case: catalog → filter LLM → filter inline
        List<WorkflowTask> filterTasks =
                buildApiDynamicFilterChain(agentName, prepareRef, model, maxTools);

        Map<String, List<WorkflowTask>> switchCases = new LinkedHashMap<>();
        switchCases.put("filter", filterTasks);
        thresholdSwitch.setDecisionCases(switchCases);

        // default: noop
        WorkflowTask noop = new WorkflowTask();
        noop.setType("SET_VARIABLE");
        noop.setTaskReferenceName(agentName + "_api_direct_noop");
        noop.setInputParameters(Map.of("_api_direct", true));
        thresholdSwitch.setDefaultCase(List.of(noop));

        preTasks.add(thresholdSwitch);

        // ── 4. INLINE resolve task ───────────────────────────────────
        String resolveRef = agentName + "_api_resolve";
        WorkflowTask resolveTask = new WorkflowTask();
        resolveTask.setTaskReferenceName(resolveRef);
        resolveTask.setType("INLINE");

        String filterInlineRef = agentName + "_api_filter_inline";
        Map<String, Object> resolveInputs = new LinkedHashMap<>();
        resolveInputs.put("evaluatorType", "graaljs");
        resolveInputs.put("expression", JavaScriptBuilder.mcpResolveScript());
        resolveInputs.put("filtered_tools", "${" + filterInlineRef + ".output.result.tools}");
        resolveInputs.put("prepared_tools", "${" + prepareRef + ".output.result.tools}");
        resolveInputs.put("mcpConfig", "${" + prepareRef + ".output.result.mcpConfig}");
        resolveInputs.put("apiConfig", "${" + prepareRef + ".output.result.apiConfig}");
        resolveTask.setInputParameters(resolveInputs);
        preTasks.add(resolveTask);

        String toolsRef = "${" + resolveRef + ".output.result.tools}";
        String apiConfigRef = "${" + resolveRef + ".output.result.apiConfig}";

        logger.debug(
                "Built API discovery tasks for agent '{}': {} spec URLs, threshold={}",
                agentName,
                apiServers.size(),
                maxTools);

        return new DiscoveryResult(preTasks, toolsRef, null, apiConfigRef);
    }

    /**
     * Build pre-loop workflow tasks for combined MCP + API tool discovery.
     *
     * <p>When an agent has both MCP and API tools, this method creates a unified discovery pipeline
     * that discovers tools from both MCP servers and API specs, merges them together with static
     * tools, and applies a single threshold filter.
     *
     * @param agentName agent name for task reference prefixes
     * @param mcpTools MCP tool configs (toolType="mcp")
     * @param apiTools API tool configs (toolType="api")
     * @param staticToolSpecs compiled specs for static tools (baked into prepare script)
     * @param model model string for the filter LLM
     * @return discovery result with pre-tasks, tools ref, mcpConfig ref, and apiConfig ref
     */
    public DiscoveryResult buildDiscoveryTasks(
            String agentName,
            List<ToolConfig> mcpTools,
            List<ToolConfig> apiTools,
            List<Map<String, Object>> staticToolSpecs,
            String model) {

        agentName = AgentCompiler.toRef(agentName);
        List<WorkflowTask> preTasks = new ArrayList<>();
        int maxTools = 32;

        // ── 1a. LIST_MCP_TOOLS tasks (one per unique MCP server) ─────
        Map<String, Map<String, Object>> mcpServerMap = new LinkedHashMap<>();
        for (ToolConfig tool : mcpTools) {
            Map<String, Object> cfg =
                    tool.getConfig() != null ? tool.getConfig() : Collections.emptyMap();
            String serverUrl = (String) cfg.getOrDefault("server_url", "");
            if (!serverUrl.isEmpty() && !mcpServerMap.containsKey(serverUrl)) {
                Map<String, Object> serverInfo = new LinkedHashMap<>();
                serverInfo.put("serverUrl", serverUrl);
                Object mcpH = cfg.getOrDefault("headers", Collections.emptyMap());
                serverInfo.put(
                        "headers",
                        mcpH instanceof Map<?, ?>
                                ? escapeCredentialPlaceholders((Map<?, ?>) mcpH)
                                : mcpH);
                mcpServerMap.put(serverUrl, serverInfo);
            }
            Object mt = cfg.get("max_tools");
            if (mt instanceof Number) maxTools = ((Number) mt).intValue();
        }

        List<Map<String, Object>> mcpServers = new ArrayList<>(mcpServerMap.values());
        List<String> mcpListRefs = new ArrayList<>();

        for (int i = 0; i < mcpServers.size(); i++) {
            Map<String, Object> server = mcpServers.get(i);
            String listRef = agentName + "_list_mcp_" + i;
            mcpListRefs.add(listRef);

            WorkflowTask listTask = new WorkflowTask();
            listTask.setName("LIST_MCP_TOOLS");
            listTask.setTaskReferenceName(listRef);
            listTask.setType("LIST_MCP_TOOLS");

            Map<String, Object> listInputs = new LinkedHashMap<>();
            listInputs.put("mcpServer", server.get("serverUrl"));
            // Direct task input — convert #{NAME} markers to wire-only-resolved secret refs.
            Object headers = secretRefHeaders(server.get("headers"));
            if (headers != null && !((Map<?, ?>) headers).isEmpty()) {
                listInputs.put("headers", headers);
            }
            listTask.setInputParameters(listInputs);
            preTasks.add(listTask);
        }

        // ── 1b. LIST_API_TOOLS tasks (one per unique spec URL) ───────
        Map<String, Map<String, Object>> apiSpecMap = new LinkedHashMap<>();
        for (ToolConfig tool : apiTools) {
            Map<String, Object> cfg =
                    tool.getConfig() != null ? tool.getConfig() : Collections.emptyMap();
            String specUrl = (String) cfg.getOrDefault("url", cfg.getOrDefault("spec_url", ""));
            if (!specUrl.isEmpty() && !apiSpecMap.containsKey(specUrl)) {
                Map<String, Object> specInfo = new LinkedHashMap<>();
                specInfo.put("specUrl", specUrl);
                Object apiH = cfg.getOrDefault("headers", Collections.emptyMap());
                specInfo.put(
                        "headers",
                        apiH instanceof Map<?, ?>
                                ? escapeCredentialPlaceholders((Map<?, ?>) apiH)
                                : apiH);
                apiSpecMap.put(specUrl, specInfo);
            }
            Object mt = cfg.get("max_tools");
            if (mt instanceof Number) maxTools = ((Number) mt).intValue();
        }

        List<Map<String, Object>> apiServers = new ArrayList<>(apiSpecMap.values());
        List<String> apiListRefs = new ArrayList<>();

        for (int i = 0; i < apiServers.size(); i++) {
            Map<String, Object> server = apiServers.get(i);
            String listRef = agentName + "_list_api_" + i;
            apiListRefs.add(listRef);

            WorkflowTask listTask = new WorkflowTask();
            listTask.setName("LIST_API_TOOLS");
            listTask.setTaskReferenceName(listRef);
            listTask.setType("LIST_API_TOOLS");

            Map<String, Object> listInputs = new LinkedHashMap<>();
            listInputs.put("specUrl", server.get("specUrl"));
            Object headers = secretRefHeaders(server.get("headers"));
            if (headers != null && !((Map<?, ?>) headers).isEmpty()) {
                listInputs.put("headers", headers);
            }
            listTask.setInputParameters(listInputs);
            preTasks.add(listTask);
        }

        // ── 2. INLINE prepare task (combined MCP + API) ─────────────
        String prepareRef = agentName + "_discovery_prepare";
        String staticSpecsJson = JavaScriptBuilder.toJson(staticToolSpecs);
        String mcpServersJson = JavaScriptBuilder.toJson(mcpServers);
        String apiServersJson = JavaScriptBuilder.toJson(apiServers);
        String prepareScript =
                JavaScriptBuilder.apiPrepareScript(
                        staticSpecsJson,
                        mcpServers.size(),
                        mcpServersJson,
                        apiServers.size(),
                        apiServersJson,
                        maxTools);

        WorkflowTask prepareTask = new WorkflowTask();
        prepareTask.setTaskReferenceName(prepareRef);
        prepareTask.setType("INLINE");

        Map<String, Object> prepareInputs = new LinkedHashMap<>();
        prepareInputs.put("evaluatorType", "graaljs");
        prepareInputs.put("expression", prepareScript);
        for (int i = 0; i < mcpListRefs.size(); i++) {
            prepareInputs.put("mcp_discovered_" + i, "${" + mcpListRefs.get(i) + ".output.tools}");
        }
        for (int i = 0; i < apiListRefs.size(); i++) {
            prepareInputs.put("api_discovered_" + i, "${" + apiListRefs.get(i) + ".output}");
        }
        prepareTask.setInputParameters(prepareInputs);
        preTasks.add(prepareTask);

        // ── 3. SWITCH task for threshold check ───────────────────────
        String switchRef = agentName + "_discovery_threshold";
        WorkflowTask thresholdSwitch = new WorkflowTask();
        thresholdSwitch.setType("SWITCH");
        thresholdSwitch.setTaskReferenceName(switchRef);
        thresholdSwitch.setEvaluatorType("graaljs");
        thresholdSwitch.setExpression("$.needsFilter == true ? 'filter' : 'direct'");

        Map<String, Object> switchInputs = new LinkedHashMap<>();
        switchInputs.put("needsFilter", "${" + prepareRef + ".output.result.needsFilter}");
        thresholdSwitch.setInputParameters(switchInputs);

        List<WorkflowTask> filterTasks =
                buildDynamicFilterChain(agentName, prepareRef, model, maxTools);

        Map<String, List<WorkflowTask>> switchCases = new LinkedHashMap<>();
        switchCases.put("filter", filterTasks);
        thresholdSwitch.setDecisionCases(switchCases);

        WorkflowTask noop = new WorkflowTask();
        noop.setType("SET_VARIABLE");
        noop.setTaskReferenceName(agentName + "_discovery_direct_noop");
        noop.setInputParameters(Map.of("_discovery_direct", true));
        thresholdSwitch.setDefaultCase(List.of(noop));

        preTasks.add(thresholdSwitch);

        // ── 4. INLINE resolve task ───────────────────────────────────
        String resolveRef = agentName + "_discovery_resolve";
        WorkflowTask resolveTask = new WorkflowTask();
        resolveTask.setTaskReferenceName(resolveRef);
        resolveTask.setType("INLINE");

        String filterInlineRef = agentName + "_mcp_filter_inline";
        Map<String, Object> resolveInputs = new LinkedHashMap<>();
        resolveInputs.put("evaluatorType", "graaljs");
        resolveInputs.put("expression", JavaScriptBuilder.mcpResolveScript());
        resolveInputs.put("filtered_tools", "${" + filterInlineRef + ".output.result.tools}");
        resolveInputs.put("prepared_tools", "${" + prepareRef + ".output.result.tools}");
        resolveInputs.put("mcpConfig", "${" + prepareRef + ".output.result.mcpConfig}");
        resolveInputs.put("apiConfig", "${" + prepareRef + ".output.result.apiConfig}");
        resolveTask.setInputParameters(resolveInputs);
        preTasks.add(resolveTask);

        String toolsRef = "${" + resolveRef + ".output.result.tools}";
        String mcpConfigRef = "${" + resolveRef + ".output.result.mcpConfig}";
        String apiConfigRef = "${" + resolveRef + ".output.result.apiConfig}";

        logger.debug(
                "Built combined discovery tasks for agent '{}': {} MCP servers, {} API specs, threshold={}",
                agentName,
                mcpServers.size(),
                apiServers.size(),
                maxTools);

        return new DiscoveryResult(preTasks, toolsRef, mcpConfigRef, apiConfigRef);
    }

    /** Build a dynamic filter chain for API discovery (uses _api_ prefixed task refs). */
    private List<WorkflowTask> buildApiDynamicFilterChain(
            String agentName, String prepareRef, String model, int maxTools) {

        List<WorkflowTask> tasks = new ArrayList<>();

        // 1. Catalog inline
        String catalogRef = agentName + "_api_filter_catalog";
        WorkflowTask catalogTask = new WorkflowTask();
        catalogTask.setTaskReferenceName(catalogRef);
        catalogTask.setType("INLINE");
        Map<String, Object> catalogInputs = new LinkedHashMap<>();
        catalogInputs.put("evaluatorType", "graaljs");
        catalogInputs.put("expression", JavaScriptBuilder.filterCatalogScript(maxTools));
        catalogInputs.put("tools", "${" + prepareRef + ".output.result.tools}");
        catalogTask.setInputParameters(catalogInputs);
        tasks.add(catalogTask);

        // 2. Filter LLM
        String filterLlmRef = agentName + "_api_filter_llm";
        WorkflowTask filterLlm = new WorkflowTask();
        filterLlm.setName("LLM_CHAT_COMPLETE");
        filterLlm.setTaskReferenceName(filterLlmRef);
        filterLlm.setType("LLM_CHAT_COMPLETE");

        Map<String, Object> filterLlmInputs = new LinkedHashMap<>();
        if (model != null && !model.isEmpty()) {
            ModelParser.ParsedModel parsed = ModelParser.parse(model);
            filterLlmInputs.put("llmProvider", parsed.getProvider());
            filterLlmInputs.put("model", parsed.getModel());
        }

        String systemPrompt =
                "You are a tool selection assistant. Given a user query and a catalog "
                        + "of available tools, select the most relevant tools the AI agent will "
                        + "need. Select at most "
                        + maxTools
                        + " tools.\n\n"
                        + "TOOL CATALOG:\n${"
                        + catalogRef
                        + ".output.result.catalog}\n\n"
                        + "Respond with ONLY a JSON object: {\"selected_tools\": [\"tool_name_1\", \"tool_name_2\", ...]}";

        filterLlmInputs.put("maxTokens", 4096);
        filterLlmInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", systemPrompt),
                        Map.of(
                                "role",
                                "user",
                                "message",
                                "${workflow.input.prompt}\n\nRespond in json format.")));
        filterLlmInputs.put("temperature", 0);
        filterLlmInputs.put("jsonOutput", true);
        filterLlm.setInputParameters(filterLlmInputs);
        tasks.add(filterLlm);

        // 3. Filter inline
        String filterInlineRef = agentName + "_api_filter_inline";
        WorkflowTask filterInline = new WorkflowTask();
        filterInline.setTaskReferenceName(filterInlineRef);
        filterInline.setType("INLINE");
        Map<String, Object> filterInlineInputs = new LinkedHashMap<>();
        filterInlineInputs.put("evaluatorType", "graaljs");
        filterInlineInputs.put("expression", JavaScriptBuilder.filterToolsScriptDynamic());
        filterInlineInputs.put("selectedNames", "${" + filterLlmRef + ".output.result}");
        filterInlineInputs.put("allTools", "${" + prepareRef + ".output.result.tools}");
        filterInline.setInputParameters(filterInlineInputs);
        tasks.add(filterInline);

        return tasks;
    }

    /** Build a dynamic filter chain that reads tools from a runtime reference. */
    private List<WorkflowTask> buildDynamicFilterChain(
            String agentName, String prepareRef, String model, int maxTools) {

        List<WorkflowTask> tasks = new ArrayList<>();

        // 1. Catalog inline: build catalog text from runtime tools
        String catalogRef = agentName + "_mcp_filter_catalog";
        WorkflowTask catalogTask = new WorkflowTask();
        catalogTask.setTaskReferenceName(catalogRef);
        catalogTask.setType("INLINE");
        Map<String, Object> catalogInputs = new LinkedHashMap<>();
        catalogInputs.put("evaluatorType", "graaljs");
        catalogInputs.put("expression", JavaScriptBuilder.filterCatalogScript(maxTools));
        catalogInputs.put("tools", "${" + prepareRef + ".output.result.tools}");
        catalogTask.setInputParameters(catalogInputs);
        tasks.add(catalogTask);

        // 2. Filter LLM
        String filterLlmRef = agentName + "_mcp_filter_llm";
        WorkflowTask filterLlm = new WorkflowTask();
        filterLlm.setName("LLM_CHAT_COMPLETE");
        filterLlm.setTaskReferenceName(filterLlmRef);
        filterLlm.setType("LLM_CHAT_COMPLETE");

        Map<String, Object> filterLlmInputs = new LinkedHashMap<>();
        if (model != null && !model.isEmpty()) {
            ModelParser.ParsedModel parsed = ModelParser.parse(model);
            filterLlmInputs.put("llmProvider", parsed.getProvider());
            filterLlmInputs.put("model", parsed.getModel());
        }

        String systemPrompt =
                "You are a tool selection assistant. Given a user query and a catalog "
                        + "of available tools, select the most relevant tools the AI agent will "
                        + "need. Select at most "
                        + maxTools
                        + " tools.\n\n"
                        + "TOOL CATALOG:\n${"
                        + catalogRef
                        + ".output.result.catalog}\n\n"
                        + "Respond with ONLY a JSON object: {\"selected_tools\": [\"tool_name_1\", \"tool_name_2\", ...]}";

        filterLlmInputs.put("maxTokens", 4096);
        filterLlmInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", systemPrompt),
                        Map.of(
                                "role",
                                "user",
                                "message",
                                "${workflow.input.prompt}\n\nRespond in json format.")));
        filterLlmInputs.put("temperature", 0);
        filterLlmInputs.put("jsonOutput", true);
        filterLlm.setInputParameters(filterLlmInputs);
        tasks.add(filterLlm);

        // 3. Filter inline: filter tools by selected names
        String filterInlineRef = agentName + "_mcp_filter_inline";
        WorkflowTask filterInline = new WorkflowTask();
        filterInline.setTaskReferenceName(filterInlineRef);
        filterInline.setType("INLINE");
        Map<String, Object> filterInlineInputs = new LinkedHashMap<>();
        filterInlineInputs.put("evaluatorType", "graaljs");
        filterInlineInputs.put("expression", JavaScriptBuilder.filterToolsScriptDynamic());
        filterInlineInputs.put("selectedNames", "${" + filterLlmRef + ".output.result}");
        filterInlineInputs.put("allTools", "${" + prepareRef + ".output.result.tools}");
        filterInline.setInputParameters(filterInlineInputs);
        tasks.add(filterInline);

        return tasks;
    }

    /**
     * Build tool call routing with dynamic MCP config from a runtime reference.
     *
     * <p>Like {@link #buildToolCallRouting} but the enrichment task reads {@code mcpConfig} from a
     * runtime reference instead of baking it in.
     */
    public WorkflowTask buildToolCallRoutingDynamic(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            boolean hasApproval,
            String model,
            String mcpConfigRef) {
        return buildToolCallRoutingDynamicWithResult(
                        agentName, llmRef, tools, hasApproval, model, mcpConfigRef, null)
                .getRouterTask();
    }

    /** Build tool call routing with dynamic MCP and API config. */
    public WorkflowTask buildToolCallRoutingDynamic(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            boolean hasApproval,
            String model,
            String mcpConfigRef,
            String apiConfigRef) {
        return buildToolCallRoutingDynamicWithResult(
                        agentName, llmRef, tools, hasApproval, model, mcpConfigRef, apiConfigRef)
                .getRouterTask();
    }

    /**
     * Build tool call routing with dynamic MCP config, returning guardrail metadata.
     *
     * @deprecated Use {@link #buildToolCallRoutingDynamicWithResult(String, String, List, boolean,
     *     String, String, String)} instead.
     */
    @Deprecated
    public ToolCallRoutingResult buildToolCallRoutingDynamicWithResult(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            boolean hasApproval,
            String model,
            String mcpConfigRef) {
        return buildToolCallRoutingDynamicWithResult(
                agentName, llmRef, tools, hasApproval, model, mcpConfigRef, null);
    }

    /** Build tool call routing with dynamic MCP and API config, returning guardrail metadata. */
    public ToolCallRoutingResult buildToolCallRoutingDynamicWithResult(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            boolean hasApproval,
            String model,
            String mcpConfigRef,
            String apiConfigRef) {
        agentName = AgentCompiler.toRef(agentName);
        List<String> retryRefs = new ArrayList<>();
        List<String[]> guardrailRefs = new ArrayList<>();

        String switchExpr =
                "$.toolCalls != null && $.toolCalls.length > 0 " + "? 'tool_call' : 'none'";

        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setName("SWITCH");
        switchTask.setTaskReferenceName(agentName + "_tool_router");
        switchTask.setType("SWITCH");
        switchTask.setExpression(switchExpr);
        switchTask.setEvaluatorType("graaljs");

        Map<String, Object> switchInput = new LinkedHashMap<>();
        switchInput.put("toolCalls", "${" + llmRef + ".output.toolCalls}");
        switchTask.setInputParameters(switchInput);

        // Build tool guardrail gate + approval + fork chain
        List<WorkflowTask> toolCallTasks = new ArrayList<>();

        // Tool guardrails (before approval, before fork)
        List<GuardrailConfig> toolGuardrails =
                collectToolGuardrails(tools != null ? tools : Collections.emptyList());
        if (!toolGuardrails.isEmpty()) {
            toolCallTasks.addAll(
                    buildToolGuardrailGate(
                            agentName, llmRef, toolGuardrails, retryRefs, guardrailRefs));
        }

        if (hasApproval) {
            toolCallTasks.addAll(
                    buildToolCallWithApprovalDynamic(
                            agentName, llmRef, model, tools, mcpConfigRef, apiConfigRef));
        } else {
            toolCallTasks.addAll(
                    buildForkChainDynamic(
                            agentName, llmRef, tools, "", mcpConfigRef, apiConfigRef));
        }

        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();
        decisionCases.put("tool_call", toolCallTasks);
        switchTask.setDecisionCases(decisionCases);
        switchTask.setDefaultCase(Collections.emptyList());

        return new ToolCallRoutingResult(switchTask, retryRefs, guardrailRefs);
    }

    /** Build fork chain with dynamic MCP config. */
    public List<WorkflowTask> buildForkChainDynamic(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            String prefix,
            String mcpConfigRef) {
        return buildForkChainDynamic(agentName, llmRef, tools, prefix, mcpConfigRef, null);
    }

    /** Build fork chain with dynamic MCP and API config. */
    public List<WorkflowTask> buildForkChainDynamic(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            String prefix,
            String mcpConfigRef,
            String apiConfigRef) {
        String p = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";

        Object[] enrichResult =
                buildEnrichTaskDynamic(agentName, llmRef, tools, p, mcpConfigRef, apiConfigRef);
        WorkflowTask enrichTask = (WorkflowTask) enrichResult[0];
        String toolCallsRef = (String) enrichResult[1];

        WorkflowTask forkTask = buildDynamicFork(agentName, toolCallsRef, p);

        List<WorkflowTask> joinList = forkTask.getDefaultCase();
        WorkflowTask joinTask = (joinList != null && !joinList.isEmpty()) ? joinList.get(0) : null;
        forkTask.setDefaultCase(Collections.emptyList());

        List<WorkflowTask> chain = new ArrayList<>();
        chain.add(enrichTask);
        chain.add(forkTask);
        if (joinTask != null) {
            chain.add(joinTask);
            chain.addAll(buildStateMergeTasks(agentName, joinTask.getTaskReferenceName(), p));
        }
        return chain;
    }

    /** Build enrich task with dynamic MCP config from a runtime reference. */
    public Object[] buildEnrichTaskDynamic(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            String p,
            String mcpConfigRef) {
        return buildEnrichTaskDynamic(agentName, llmRef, tools, p, mcpConfigRef, null);
    }

    /** Build enrich task with dynamic MCP and API config from runtime references. */
    public Object[] buildEnrichTaskDynamic(
            String agentName,
            String llmRef,
            List<ToolConfig> tools,
            String p,
            String mcpConfigRef,
            String apiConfigRef) {
        agentName = AgentCompiler.toRef(agentName);
        // Build static configs (HTTP, media, agent_tool, RAG) at compile time — same as
        // buildEnrichTask
        Map<String, Object> httpConfig = new LinkedHashMap<>();
        Map<String, Object> mediaConfig = new LinkedHashMap<>();
        Map<String, Object> agentToolConfig = new LinkedHashMap<>();
        Map<String, Object> ragConfig = new LinkedHashMap<>();
        Map<String, Object> humanConfig = new LinkedHashMap<>();
        Map<String, Object> wmqConfig = new LinkedHashMap<>();

        if (tools != null) {
            for (ToolConfig tool : tools) {
                String toolType = tool.getToolType() != null ? tool.getToolType() : "worker";
                Map<String, Object> cfg =
                        tool.getConfig() != null ? tool.getConfig() : Collections.emptyMap();

                if ("http".equals(toolType)) {
                    httpConfig.put(tool.getName(), escapeHeadersInConfig(cfg));
                } else if ("agent_tool".equals(toolType)) {
                    String workflowName =
                            (String) cfg.getOrDefault("workflowName", tool.getName() + "_agent_wf");
                    Map<String, Object> atEntry = new LinkedHashMap<>();
                    atEntry.put("workflowName", workflowName);
                    // Pass through retry/resilience overrides from SDK config
                    if (cfg.containsKey("retryCount"))
                        atEntry.put("retryCount", cfg.get("retryCount"));
                    if (cfg.containsKey("retryDelaySeconds"))
                        atEntry.put("retryDelaySeconds", cfg.get("retryDelaySeconds"));
                    if (cfg.containsKey("optional")) atEntry.put("optional", cfg.get("optional"));
                    agentToolConfig.put(tool.getName(), atEntry);
                } else if (MEDIA_TOOL_TYPES.contains(toolType)) {
                    Map<String, Object> cfgCopy = new LinkedHashMap<>(cfg);
                    String taskType =
                            cfgCopy.containsKey("taskType")
                                    ? (String) cfgCopy.remove("taskType")
                                    : toolType.toUpperCase();
                    Map<String, Object> mediaEntry = new LinkedHashMap<>();
                    mediaEntry.put("taskType", taskType);
                    mediaEntry.put("defaults", cfgCopy);
                    mediaConfig.put(tool.getName(), mediaEntry);
                } else if (RAG_TOOL_TYPES.contains(toolType)) {
                    Map<String, Object> cfgCopy = new LinkedHashMap<>(cfg);
                    String taskType =
                            cfgCopy.containsKey("taskType")
                                    ? (String) cfgCopy.remove("taskType")
                                    : TYPE_MAP.getOrDefault(toolType, toolType.toUpperCase());
                    Map<String, Object> ragEntry = new LinkedHashMap<>();
                    ragEntry.put("taskType", taskType);
                    ragEntry.put("defaults", cfgCopy);
                    ragConfig.put(tool.getName(), ragEntry);
                } else if ("human".equals(toolType)) {
                    Map<String, Object> humanEntry = new LinkedHashMap<>();
                    humanEntry.put("displayName", agentName + " — " + tool.getName());
                    humanEntry.put("description", tool.getDescription());
                    humanConfig.put(tool.getName(), humanEntry);
                } else if ("pull_workflow_messages".equals(toolType)) {
                    Map<String, Object> wmqEntry = new LinkedHashMap<>();
                    wmqEntry.put("batchSize", cfg.getOrDefault("batchSize", 1));
                    wmqConfig.put(tool.getName(), wmqEntry);
                }
                // MCP config comes from runtime — skip here
            }
        }

        String httpJson = JavaScriptBuilder.toJson(httpConfig);
        String mediaJson = JavaScriptBuilder.toJson(mediaConfig);
        String agentToolJson = JavaScriptBuilder.toJson(agentToolConfig);
        String ragJson = JavaScriptBuilder.toJson(ragConfig);
        String humanJson = JavaScriptBuilder.toJson(humanConfig);
        String wmqJson = JavaScriptBuilder.toJson(wmqConfig);
        Map<String, Object> knownToolNames = new LinkedHashMap<>();
        if (tools != null) {
            for (ToolConfig t : tools) {
                if (t.getName() != null) knownToolNames.put(t.getName(), Boolean.TRUE);
            }
        }
        String knownToolNamesJson = JavaScriptBuilder.toJson(knownToolNames);
        String script =
                JavaScriptBuilder.enrichToolsScriptDynamic(
                        httpJson,
                        mediaJson,
                        agentToolJson,
                        ragJson,
                        humanJson,
                        wmqJson,
                        knownToolNamesJson);

        String enrichRef = agentName + "_" + p + "enrich_tools";

        WorkflowTask enrichTask = new WorkflowTask();
        enrichTask.setTaskReferenceName(enrichRef);
        enrichTask.setType("INLINE");

        Map<String, Object> enrichInput = new LinkedHashMap<>();
        enrichInput.put("evaluatorType", "graaljs");
        enrichInput.put("expression", script);
        enrichInput.put("toolCalls", "${" + llmRef + ".output.toolCalls}");
        enrichInput.put("mcpConfig", mcpConfigRef);
        if (apiConfigRef != null) {
            enrichInput.put("apiConfig", apiConfigRef);
        }
        enrichInput.put("agentState", "${workflow.variables._agent_state}");
        enrichInput.put("userPrompt", "${workflow.input.prompt}");
        enrichTask.setInputParameters(enrichInput);

        String outputRef = "${" + enrichRef + ".output.result.dynamicTasks}";
        return new Object[] {enrichTask, outputRef};
    }

    /** Build tool-call case with approval gate using dynamic MCP and API config. */
    private List<WorkflowTask> buildToolCallWithApprovalDynamic(
            String agentName,
            String llmRef,
            String model,
            List<ToolConfig> tools,
            String mcpConfigRef,
            String apiConfigRef) {
        // Reuse the same approval logic but with dynamic fork chains
        // (simplified: delegate to existing approval logic, replacing fork chain builders)

        StringBuilder approvalJson = new StringBuilder("{");
        boolean first = true;
        for (ToolConfig tool : tools) {
            if (tool.isApprovalRequired()) {
                if (!first) approvalJson.append(",");
                approvalJson.append("\"").append(tool.getName()).append("\":true");
                first = false;
            }
        }
        approvalJson.append("}");

        String checkRef = agentName + "_check_approval";
        WorkflowTask checkTask = new WorkflowTask();
        checkTask.setTaskReferenceName(checkRef);
        checkTask.setType("INLINE");
        Map<String, Object> checkInputs = new LinkedHashMap<>();
        checkInputs.put("evaluatorType", "graaljs");
        checkInputs.put(
                "expression",
                "(function() {"
                        + "var approvalTools = "
                        + approvalJson
                        + ";"
                        + "var tcs = $.tool_calls || [];"
                        + "for (var i = 0; i < tcs.length; i++) {"
                        + "  if (approvalTools[tcs[i].name]) return {needs_approval: true};"
                        + "}"
                        + "return {needs_approval: false};"
                        + "})()");
        checkInputs.put("tool_calls", "${" + llmRef + ".output.toolCalls}");
        checkTask.setInputParameters(checkInputs);

        WorkflowTask approvalSwitch = new WorkflowTask();
        approvalSwitch.setType("SWITCH");
        approvalSwitch.setTaskReferenceName(agentName + "_approval_gate");
        approvalSwitch.setEvaluatorType("graaljs");
        approvalSwitch.setExpression("$.needs_approval == true ? 'needs_approval' : 'direct'");
        Map<String, Object> gateInputs = new LinkedHashMap<>();
        gateInputs.put("needs_approval", "${" + checkRef + ".output.result.needs_approval}");
        approvalSwitch.setInputParameters(gateInputs);

        Map<String, List<WorkflowTask>> gateCases = new LinkedHashMap<>();

        // needs_approval case: same as buildToolCallWithApproval but with dynamic fork chains
        List<WorkflowTask> approvalCaseTasks =
                buildApprovalCaseTasksDynamic(
                        agentName, llmRef, model, tools, mcpConfigRef, apiConfigRef);
        gateCases.put("needs_approval", approvalCaseTasks);
        approvalSwitch.setDecisionCases(gateCases);

        List<WorkflowTask> directChain =
                buildForkChainDynamic(
                        agentName, llmRef, tools, "direct", mcpConfigRef, apiConfigRef);
        approvalSwitch.setDefaultCase(directChain);

        return List.of(checkTask, approvalSwitch);
    }

    /**
     * Build the approval case tasks with dynamic MCP and API config (human task -> validate ->
     * normalize -> check -> route).
     */
    private List<WorkflowTask> buildApprovalCaseTasksDynamic(
            String agentName,
            String llmRef,
            String model,
            List<ToolConfig> tools,
            String mcpConfigRef,
            String apiConfigRef) {
        String humanRef = agentName + "_approval_human";
        HumanTaskBuilder.Pipeline pipeline =
                HumanTaskBuilder.create(humanRef, agentName + " Tool Approval")
                        .contextInput("tool_calls", "${" + llmRef + ".output.toolCalls}")
                        .approvalValidation(model)
                        .build();

        List<WorkflowTask> tasks = new ArrayList<>(pipeline.getTasks());

        // Approval routing SWITCH (approve → execute tools, reject → terminate)
        String outputRef = pipeline.getOutputRef();
        WorkflowTask approvalRoute = new WorkflowTask();
        approvalRoute.setType("SWITCH");
        approvalRoute.setTaskReferenceName(agentName + "_approval_route");
        approvalRoute.setEvaluatorType("graaljs");
        approvalRoute.setExpression("$.approved == true ? 'approved' : 'rejected'");
        approvalRoute.setInputParameters(Map.of("approved", "${" + outputRef + ".approved}"));

        List<WorkflowTask> approvedChain =
                buildForkChainDynamic(
                        agentName, llmRef, tools, "approved", mcpConfigRef, apiConfigRef);
        approvalRoute.setDecisionCases(Map.of("approved", approvedChain));

        WorkflowTask setRejectionOutput = new WorkflowTask();
        setRejectionOutput.setType("SET_VARIABLE");
        setRejectionOutput.setTaskReferenceName(agentName + "_approval_reject_output");
        setRejectionOutput.setInputParameters(
                Map.of(
                        "rejectedToolCall", "${" + outputRef + ".rejected_tool}",
                        "rejectionReason", "${" + outputRef + ".reason}",
                        "finishReason", "rejected"));

        WorkflowTask rejectTerminate = new WorkflowTask();
        rejectTerminate.setType("TERMINATE");
        rejectTerminate.setTaskReferenceName(agentName + "_approval_reject");
        rejectTerminate.setInputParameters(
                Map.of(
                        "terminationReason",
                        "${" + outputRef + ".reason}",
                        "terminationStatus",
                        "COMPLETED"));
        approvalRoute.setDefaultCase(List.of(setRejectionOutput, rejectTerminate));
        tasks.add(approvalRoute);

        return tasks;
    }

    // ── State merge helpers ─────────────────────────────────────────────

    /**
     * Build merge + persist tasks for ToolContext.state.
     *
     * <p>After a FORK_JOIN_DYNAMIC + JOIN, this creates:
     *
     * <ol>
     *   <li>An INLINE task that merges {@code _state_updates} from all forked tool outputs
     *   <li>A SET_VARIABLE task that persists the merged state to {@code
     *       workflow.variables._agent_state}
     * </ol>
     *
     * @param agentName name of the owning agent
     * @param joinRef task reference name of the JOIN task
     * @param prefix optional prefix for task reference names
     * @return list of two tasks: [mergeInline, setVariable]
     */
    public List<WorkflowTask> buildStateMergeTasks(
            String agentName, String joinRef, String prefix) {
        agentName = AgentCompiler.toRef(agentName);
        String p = (prefix != null && !prefix.isEmpty()) ? prefix : "";

        // 1. INLINE merge task
        String mergeRef = agentName + "_" + p + "merge_state";
        WorkflowTask mergeTask = new WorkflowTask();
        mergeTask.setTaskReferenceName(mergeRef);
        mergeTask.setType("INLINE");

        Map<String, Object> mergeInput = new LinkedHashMap<>();
        mergeInput.put("evaluatorType", "graaljs");
        mergeInput.put("expression", JavaScriptBuilder.stateMergeScript());
        mergeInput.put("currentState", "${workflow.variables._agent_state}");
        mergeInput.put("previousToolResults", "${workflow.variables._last_tool_results}");
        mergeInput.put("joinOutput", "${" + joinRef + ".output}");
        mergeTask.setInputParameters(mergeInput);

        // 2. SET_VARIABLE to persist merged state
        String setRef = agentName + "_" + p + "set_state";
        WorkflowTask setTask = new WorkflowTask();
        setTask.setType("SET_VARIABLE");
        setTask.setTaskReferenceName(setRef);
        setTask.setInputParameters(
                Map.of(
                        "_agent_state", "${" + mergeRef + ".output.result.mergedState}",
                        "_last_tool_results", "${" + mergeRef + ".output.result.toolResults}"));

        return List.of(mergeTask, setTask);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Build the task chain for the "tool_call" case of the routing switch.
     *
     * <p>If tools have guardrails, prepends guardrail gate tasks. If {@code hasApproval} is true,
     * inserts a check-approval worker and a nested SwitchTask that gates execution behind a
     * HumanTask.
     *
     * @param outRetryRefs mutable list — tool guardrail retry refs are appended here
     * @param outGuardrailRefs mutable list — tool guardrail refs are appended here
     */
    private List<WorkflowTask> buildToolCallCase(
            String agentName,
            String llmRef,
            boolean hasApproval,
            String model,
            List<ToolConfig> tools,
            List<String> outRetryRefs,
            List<String[]> outGuardrailRefs) {
        List<WorkflowTask> tasks = new ArrayList<>();

        // Tool guardrails (before approval, before fork)
        List<GuardrailConfig> toolGuardrails = collectToolGuardrails(tools);
        if (!toolGuardrails.isEmpty()) {
            tasks.addAll(
                    buildToolGuardrailGate(
                            agentName, llmRef, toolGuardrails, outRetryRefs, outGuardrailRefs));
        }

        // Existing approval + fork chain
        if (hasApproval) {
            tasks.addAll(buildToolCallWithApproval(agentName, llmRef, model, tools));
        } else {
            tasks.addAll(buildForkChain(agentName, llmRef, tools, ""));
        }
        return tasks;
    }

    /** Collect all guardrail configs from tools that have them. */
    private List<GuardrailConfig> collectToolGuardrails(List<ToolConfig> tools) {
        if (tools == null) return Collections.emptyList();
        return tools.stream()
                .filter(t -> t.getGuardrails() != null && !t.getGuardrails().isEmpty())
                .flatMap(t -> t.getGuardrails().stream())
                .toList();
    }

    /**
     * Build the tool guardrail gate: format tool calls, run guardrails, route results.
     *
     * @param outRetryRefs mutable list — retry refs appended here for DoWhile wiring
     * @param outGuardrailRefs mutable list — guardrail refs appended here for DoWhile wiring
     * @return ordered list of tasks to prepend to the tool_call case
     */
    private List<WorkflowTask> buildToolGuardrailGate(
            String agentName,
            String llmRef,
            List<GuardrailConfig> toolGuardrails,
            List<String> outRetryRefs,
            List<String[]> outGuardrailRefs) {

        List<WorkflowTask> tasks = new ArrayList<>();

        // 1. Format tool calls into readable text for guardrail evaluation
        String formatRef = agentName + "_format_tool_calls";
        WorkflowTask formatTask = new WorkflowTask();
        formatTask.setTaskReferenceName(formatRef);
        formatTask.setType("INLINE");

        Map<String, Object> formatInputs = new LinkedHashMap<>();
        formatInputs.put("evaluatorType", "graaljs");
        formatInputs.put("expression", JavaScriptBuilder.formatToolCallsScript());
        formatInputs.put("tool_calls", "${" + llmRef + ".output.toolCalls}");
        formatTask.setInputParameters(formatInputs);
        tasks.add(formatTask);

        // 2. Compile guardrail tasks
        String contentRef = "${" + formatRef + ".output.result.formatted}";
        GuardrailCompiler gc = new GuardrailCompiler();
        List<GuardrailCompiler.GuardrailTaskResult> guardrailResults =
                gc.compileToolGuardrailTasks(toolGuardrails, agentName, contentRef);

        // 3. Build routing for each guardrail
        for (int idx = 0; idx < guardrailResults.size(); idx++) {
            GuardrailCompiler.GuardrailTaskResult gr = guardrailResults.get(idx);
            String suffix = guardrailResults.size() > 1 ? "_tg_" + idx : "_tg";
            GuardrailCompiler.GuardrailRoutingResult routing =
                    gc.compileGuardrailRouting(
                            toolGuardrails.get(idx),
                            gr.getRefName(),
                            contentRef,
                            agentName,
                            suffix,
                            gr.isInline());
            tasks.addAll(gr.getTasks());
            tasks.add(routing.getSwitchTask());
            outGuardrailRefs.add(new String[] {gr.getRefName(), String.valueOf(gr.isInline())});
            outRetryRefs.add(routing.getRetryRef());
        }

        logger.debug(
                "Built tool guardrail gate for agent '{}' with {} guardrails",
                agentName,
                toolGuardrails.size());
        return tasks;
    }

    /**
     * Build tool-call case with an approval gate. Flow: check_approval → SwitchTask:
     * "needs_approval": HumanTask → validate → normalize → approval_check → route default: enrich →
     * DynamicFork+Join
     */
    private List<WorkflowTask> buildToolCallWithApproval(
            String agentName, String llmRef, String model, List<ToolConfig> tools) {
        // 1. check_approval INLINE task (server-side JS)
        StringBuilder approvalJson = new StringBuilder("{");
        boolean first = true;
        for (ToolConfig tool : tools) {
            if (tool.isApprovalRequired()) {
                if (!first) approvalJson.append(",");
                approvalJson.append("\"").append(tool.getName()).append("\":true");
                first = false;
            }
        }
        approvalJson.append("}");

        String checkRef = agentName + "_check_approval";
        WorkflowTask checkTask = new WorkflowTask();
        checkTask.setTaskReferenceName(checkRef);
        checkTask.setType("INLINE");
        Map<String, Object> checkInputs = new LinkedHashMap<>();
        checkInputs.put("evaluatorType", "graaljs");
        checkInputs.put(
                "expression",
                "(function() {"
                        + "var approvalTools = "
                        + approvalJson
                        + ";"
                        + "var tcs = $.tool_calls || [];"
                        + "for (var i = 0; i < tcs.length; i++) {"
                        + "  if (approvalTools[tcs[i].name]) return {needs_approval: true};"
                        + "}"
                        + "return {needs_approval: false};"
                        + "})()");
        checkInputs.put("tool_calls", "${" + llmRef + ".output.toolCalls}");
        checkTask.setInputParameters(checkInputs);

        // 2. Outer SwitchTask: needs_approval vs direct execution
        WorkflowTask approvalSwitch = new WorkflowTask();
        approvalSwitch.setType("SWITCH");
        approvalSwitch.setTaskReferenceName(agentName + "_approval_gate");
        approvalSwitch.setEvaluatorType("graaljs");
        approvalSwitch.setExpression("$.needs_approval == true ? 'needs_approval' : 'direct'");
        Map<String, Object> gateInputs = new LinkedHashMap<>();
        gateInputs.put("needs_approval", "${" + checkRef + ".output.result.needs_approval}");
        approvalSwitch.setInputParameters(gateInputs);

        Map<String, List<WorkflowTask>> gateCases = new LinkedHashMap<>();

        // ── "needs_approval" case ──
        String humanRef = agentName + "_approval_human";
        HumanTaskBuilder.Pipeline pipeline =
                HumanTaskBuilder.create(humanRef, agentName + " Tool Approval")
                        .responseSchema(HumanTaskBuilder.approvalResponseSchema())
                        .responseUiSchema(HumanTaskBuilder.approvalResponseUiSchema())
                        .contextInput("tool_calls", "${" + llmRef + ".output.toolCalls}")
                        .approvalValidation(model)
                        .build();

        List<WorkflowTask> approvalCaseTasks = new ArrayList<>(pipeline.getTasks());
        String outputRef = pipeline.getOutputRef();

        // Approval routing SwitchTask
        WorkflowTask approvalRoute = new WorkflowTask();
        approvalRoute.setType("SWITCH");
        approvalRoute.setTaskReferenceName(agentName + "_approval_route");
        approvalRoute.setEvaluatorType("graaljs");
        approvalRoute.setExpression("$.approved == true ? 'approved' : 'rejected'");
        approvalRoute.setInputParameters(Map.of("approved", "${" + outputRef + ".approved}"));

        // "approved" case: store human feedback, then execute tools
        List<WorkflowTask> approvedTasks = new ArrayList<>();

        // Format and store custom data from human response in workflow variable.
        String formatFeedbackRef = agentName + "_approval_format_feedback";
        WorkflowTask formatFeedback = new WorkflowTask();
        formatFeedback.setTaskReferenceName(formatFeedbackRef);
        formatFeedback.setType("INLINE");
        Map<String, Object> formatInputs = new LinkedHashMap<>();
        formatInputs.put("evaluatorType", "graaljs");
        formatInputs.put("expression", JavaScriptBuilder.formatHumanFeedbackScript());
        formatInputs.put("extra", "${" + outputRef + ".extra}");
        formatInputs.put("reason", "${" + outputRef + ".reason}");
        formatFeedback.setInputParameters(formatInputs);
        approvedTasks.add(formatFeedback);

        WorkflowTask storeHumanFeedback = new WorkflowTask();
        storeHumanFeedback.setType("SET_VARIABLE");
        storeHumanFeedback.setTaskReferenceName(agentName + "_approval_store_feedback");
        storeHumanFeedback.setInputParameters(
                Map.of("_human_feedback", "${" + formatFeedbackRef + ".output.result}"));
        approvedTasks.add(storeHumanFeedback);

        approvedTasks.addAll(buildForkChain(agentName, llmRef, tools, "approved"));
        Map<String, List<WorkflowTask>> routeCases = new LinkedHashMap<>();
        routeCases.put("approved", approvedTasks);
        approvalRoute.setDecisionCases(routeCases);

        // default (rejected): set rejection output then terminate as COMPLETED
        WorkflowTask setRejectionOutput2 = new WorkflowTask();
        setRejectionOutput2.setType("SET_VARIABLE");
        setRejectionOutput2.setTaskReferenceName(agentName + "_approval_reject_output");
        setRejectionOutput2.setInputParameters(
                Map.of(
                        "rejectedToolCall", "${" + outputRef + ".rejected_tool}",
                        "rejectionReason", "${" + outputRef + ".reason}",
                        "finishReason", "rejected"));

        WorkflowTask rejectTerminate = new WorkflowTask();
        rejectTerminate.setType("TERMINATE");
        rejectTerminate.setTaskReferenceName(agentName + "_approval_reject");
        rejectTerminate.setInputParameters(
                Map.of(
                        "terminationReason",
                        "${" + outputRef + ".reason}",
                        "terminationStatus",
                        "COMPLETED"));
        approvalRoute.setDefaultCase(List.of(setRejectionOutput2, rejectTerminate));
        approvalCaseTasks.add(approvalRoute);

        gateCases.put("needs_approval", approvalCaseTasks);
        approvalSwitch.setDecisionCases(gateCases);

        // ── default (no approval needed) case: direct execution ──
        List<WorkflowTask> directChain = buildForkChain(agentName, llmRef, tools, "direct");
        approvalSwitch.setDefaultCase(directChain);

        return List.of(checkTask, approvalSwitch);
    }
}
