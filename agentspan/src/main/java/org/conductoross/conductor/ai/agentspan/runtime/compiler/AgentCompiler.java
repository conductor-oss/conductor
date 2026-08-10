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

import java.util.*;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowTaskUtils;
import org.conductoross.conductor.common.metadata.agent.*;
import org.conductoross.conductor.common.metadata.agent.ModelParser.ParsedModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Compiles an AgentConfig into a Conductor WorkflowDef. Mirrors
 * python/src/conductor/agents/compiler/agent_compiler.py.
 */
@Component
public class AgentCompiler {

    private static final Logger log = LoggerFactory.getLogger(AgentCompiler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Default extended-thinking budget when {@code thinkingConfig.enabled} is set without an
     * explicit {@code budgetTokens}. Anthropic's minimum is 1024; must stay below the 16384 default
     * maxTokens emitted by {@link #buildLlmTask}.
     */
    static final int DEFAULT_THINKING_BUDGET_TOKENS = 8192;

    private static final List<String> WORKFLOW_INPUTS =
            List.of("prompt", "session_id", "media", "cwd");
    private static final Map<String, Object> USER_MESSAGE =
            Map.of(
                    "role", "user",
                    "message", "${workflow.input.prompt}",
                    "media", "${workflow.input.media}");

    private int timeoutSeconds = 0;
    private int llmRetryCount = 3;
    private int contextMaxSizeBytes = 32768;
    private int contextMaxValueSizeBytes = 4096;

    /**
     * Sanitizes an agent name for use as a Conductor task reference name.
     *
     * <p>Conductor DO_WHILE loop conditions evaluate task references as JavaScript identifiers
     * (e.g. {@code $.myAgent_loop['iteration']}). Hyphens and any other character outside {@code
     * [a-zA-Z0-9_]} are not valid in JS identifier position, so they are replaced with underscores
     * here.
     *
     * <p>This method is idempotent — already-sanitized names pass through unchanged.
     */
    static String toRef(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /** Reference to a single prefill tool call result for message injection. */
    record PrefillRef(String toolName, String refName, Map<String, Object> arguments) {}

    /**
     * Result of compiling prefill tool calls: tasks to add pre-loop + refs for message injection.
     */
    record PrefillCompilationResult(List<WorkflowTask> tasks, List<PrefillRef> refs) {
        static final PrefillCompilationResult EMPTY =
                new PrefillCompilationResult(List.of(), List.of());

        boolean hasRefs() {
            return !refs.isEmpty();
        }
    }

    static final class ResolvedInstructions {
        private final List<WorkflowTask> preTasks;
        private final String text;

        ResolvedInstructions(List<WorkflowTask> preTasks, String text) {
            this.preTasks = preTasks;
            this.text = text;
        }

        List<WorkflowTask> getPreTasks() {
            return preTasks;
        }

        String getText() {
            return text;
        }
    }

    /**
     * Public entry point: compile an {@link AgentConfig} into a {@link WorkflowDef}. An agent's
     * compiled tool list is exactly its declared tool list — capabilities are opted into explicitly
     * from the SDK, never injected here.
     */
    public WorkflowDef compile(AgentConfig config) {
        WorkflowDef wf;

        // Passthrough check MUST be first — passthrough configs have null model.
        // Any other branch would crash on null model.
        if (isFrameworkPassthrough(config)) {
            wf = compileFrameworkPassthrough(config);
        } else if (isGraphStructure(config)) {
            // Graph-structure: custom StateGraph with node/edge workflow
            wf = compileGraphStructure(config);
        } else if (config.isExternal()) {
            throw new IllegalArgumentException(
                    "Cannot compile external agent '"
                            + config.getName()
                            + "' directly. "
                            + "External agents are compiled as SubWorkflowTask references.");
        } else {
            // ``hasAgents`` covers any of the ways an agent can declare
            // sub-agents: the legacy ``agents=[…]`` list, OR the named
            // PLAN_EXECUTE slots (``planner=`` and/or ``fallback=``).
            // Without checking the named slots, a PLAN_EXECUTE coordinator
            // declared with ``planner=`` would have an empty agents list,
            // hasAgents=false, and dispatch would fall to compileWithTools
            // — silently dropping the strategy.
            boolean hasAgents =
                    (config.getAgents() != null && !config.getAgents().isEmpty())
                            || config.getPlanner() != null
                            || config.getFallback() != null;
            boolean hasTools = config.getTools() != null && !config.getTools().isEmpty();

            AgentConfig.Strategy strategy = config.getStrategy();

            // Named slots (``planner=``/``fallback=``) are PLAN_EXECUTE-only.
            // Every other strategy compiler iterates ``config.getAgents()``
            // directly without consulting the named slots; the dispatch fix
            // that broadened ``hasAgents`` admits planner-only configs into
            // those compilers, which then NPE on ``config.getAgents().size()``.
            // Reject the cross-product here with a clear migration message
            // rather than letting it die with an opaque stack trace deep
            // inside compileSequential / compileParallel / compileHandoff
            // / compileHybrid / etc.
            boolean hasNamedSlots = config.getPlanner() != null || config.getFallback() != null;
            boolean isPlanExecute = strategy == AgentConfig.Strategy.PLAN_EXECUTE;
            if (hasNamedSlots && !isPlanExecute) {
                throw new IllegalArgumentException(
                        "Named slots ``planner=`` and ``fallback=`` are only valid with "
                                + "``strategy=Strategy.PLAN_EXECUTE``. Agent '"
                                + config.getName()
                                + "' has strategy='"
                                + (strategy == null ? "(unset → handoff)" : strategy.toValue())
                                + "'. Either set ``strategy=Strategy.PLAN_EXECUTE`` or pass the "
                                + "sub-agents via ``agents=[…]`` instead.");
            }

            // Strategy-led dispatch: an explicit non-handoff multi-agent
            // strategy (PLAN_EXECUTE, SEQUENTIAL, PARALLEL, ROUTER, SWARM,
            // ROUND_ROBIN, RANDOM, MANUAL) always routes to MultiAgentCompiler.
            // Previously a non-empty ``tools`` field silently rerouted to
            // ``compileHybrid``, which only knows handoff semantics — the
            // declared strategy was dropped on the floor. Hybrid is reserved
            // for the handoff case (the only one it actually implements).
            boolean isMultiAgentStrategy =
                    strategy != null && strategy != AgentConfig.Strategy.HANDOFF;

            if (hasAgents && isMultiAgentStrategy) {
                if (hasTools) {
                    log.debug(
                            "Strategy '{}' on agent '{}': ignoring {} parent-level tools "
                                    + "(declare them on the relevant sub-agent instead).",
                            strategy,
                            config.getName(),
                            config.getTools().size());
                }
                wf = new MultiAgentCompiler(this).compile(config);
            } else if (hasAgents && !hasTools) {
                // Multi-agent (handoff, or unset → handoff) with NO tools.
                wf = new MultiAgentCompiler(this).compile(config);
            } else if (hasAgents && hasTools) {
                // Handoff strategy with parent-level tools → hybrid mode.
                int subAgentCount = config.getAgents() != null ? config.getAgents().size() : 0;
                log.debug(
                        "Hybrid mode: agent '{}' has {} tools and {} sub-agents",
                        config.getName(),
                        config.getTools().size(),
                        subAgentCount);
                wf = compileHybrid(config);
            } else if (!hasTools) {
                // No tools -> simple single LLM call
                wf = compileSimple(config);
            } else {
                // Tools -> unified native FC path
                wf = compileWithTools(config);
            }
        }

        // ── Post-processing: runs for ALL compilation paths ──────────────

        // Apply masked fields to the (user-visible) top-level workflow so that
        // Conductor redacts them in execution history / UI. This runs for every
        // compile shape (simple, tools/DoWhile, hybrid, multi-agent, router,
        // graph, passthrough) because all paths converge here on ``wf``.
        // Recursively-compiled sub-agents flow back through this same method, so
        // each sub-agent workflow carries its own config's masked fields too.
        if (config.getMaskedFields() != null && !config.getMaskedFields().isEmpty()) {
            wf.setMaskedFields(config.getMaskedFields());
        }

        // Stamp agent capability tags and agentDef into workflow metadata.
        // Done here (not only in AgentService) so sub-workflows compiled recursively
        // also carry their own agentDef — AgentService only stamps the top-level def.
        if (!isFrameworkPassthrough(config) && !isGraphStructure(config)) {
            Set<String> caps = collectCapabilities(config);
            Map<String, Object> metadata =
                    wf.getMetadata() != null
                            ? new LinkedHashMap<>(wf.getMetadata())
                            : new LinkedHashMap<>();
            metadata.put("agent_capabilities", new ArrayList<>(caps));
            try {
                metadata.put("agentDef", MAPPER.convertValue(config, Map.class));
            } catch (Exception e) {
                log.debug(
                        "Could not stamp agentDef for agent '{}': {}",
                        config.getName(),
                        e.getMessage());
            }
            wf.setMetadata(metadata);
        }

        // Ensure every task has a name (Conductor requires it for execution)
        if (wf.getTasks() != null) {
            wf.getTasks().forEach(AgentCompiler::ensureTaskNames);
        }

        // Ensure every taskReferenceName is unique within the workflow.
        // Finds duplicates, renames them (appending _2, _3, etc.), and updates
        // all ${oldRef...} expressions in inputParameters/outputParameters so
        // the workflow remains internally consistent.
        if (wf.getTasks() != null) {
            ensureUniqueRefNames(wf.getTasks(), wf);
        }

        return wf;
    }

    // ── Simple agent (no tools) ─────────────────────────────────────

    WorkflowDef compileSimple(AgentConfig config) {
        ParsedModel parsed = ModelParser.parse(config.getModel());
        String llmRef = toRef(config.getName()) + "_llm";
        String instructionsRef = toRef(config.getName()) + "_instructions";

        WorkflowDef wf = createWorkflow(config);
        ResolvedInstructions resolvedInstructions = resolveInstructions(config, instructionsRef);

        // Compile prefill tool calls (pre-loop tasks + message refs).
        // Done unconditionally so a no-tool agent (e.g. a planner that reads
        // contextbook via prefill_tools) sees its prefill content. Previously
        // this branch ignored prefill_tools entirely and the SDK had to add a
        // dummy tool just to route through compileWithTools.
        PrefillCompilationResult prefill = compilePrefillTasks(config);

        // Build LLM task with prefill refs threaded into messages.
        WorkflowTask llmTask = buildLlmTask(config, parsed, llmRef, null, prefill.refs());

        // Check for output guardrails
        List<GuardrailConfig> outputGuardrails = getOutputGuardrails(config);

        if (outputGuardrails.isEmpty()) {
            // Simple path: prefill tasks → single LLM call, no loop
            List<WorkflowTask> tasks = new ArrayList<>(resolvedInstructions.getPreTasks());
            tasks.addAll(prefill.tasks());
            tasks.add(llmTask);
            wf.setTasks(tasks);
            Map<String, Object> simpleOutput = new LinkedHashMap<>();
            simpleOutput.put("result", ref(llmRef + ".output.result"));
            simpleOutput.put("finishReason", ref(llmRef + ".output.finishReason"));
            simpleOutput.put("context", "${workflow.input.context}");
            wf.setOutputParameters(simpleOutput);
            return wf;
        }

        // Guarded path: LLM + guardrails in DoWhile loop
        String contentRef = ref(llmRef + ".output.result");
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;

        List<WorkflowTask> loopTasks = new ArrayList<>();
        loopTasks.add(llmTask);

        // Compile guardrails inside loop
        GuardrailCompiler gc = new GuardrailCompiler();
        List<GuardrailCompiler.GuardrailTaskResult> guardrailResults =
                gc.compileGuardrailTasks(outputGuardrails, config.getName(), contentRef);

        List<String[]> guardrailRefs = new ArrayList<>(); // [refName, isInline]
        List<String> retryRefs = new ArrayList<>();

        for (int idx = 0; idx < guardrailResults.size(); idx++) {
            GuardrailCompiler.GuardrailTaskResult gr = guardrailResults.get(idx);
            String suffix = guardrailResults.size() > 1 ? "_" + idx : "";
            GuardrailCompiler.GuardrailRoutingResult routing =
                    gc.compileGuardrailRouting(
                            outputGuardrails.get(idx),
                            gr.getRefName(),
                            contentRef,
                            config.getName(),
                            suffix,
                            gr.isInline(),
                            config.getModel());
            loopTasks.addAll(gr.getTasks());
            loopTasks.add(routing.getSwitchTask());
            guardrailRefs.add(new String[] {gr.getRefName(), String.valueOf(gr.isInline())});
            retryRefs.add(routing.getRetryRef());
        }

        // Wire retry feedback into LLM participants
        if (!retryRefs.isEmpty()) {
            Map<String, Object> participants = new LinkedHashMap<>();
            for (String rr : retryRefs) {
                participants.put(rr, "user");
            }
            llmTask.getInputParameters().put("participants", participants);
        }

        // Build termination condition
        String guardrailContinue = buildGuardrailContinue(guardrailRefs);
        String termCondition =
                String.format(
                        "if ( $.%s['iteration'] < %d && $._stop_requested != true && ($.%s['finishReason'] == 'LENGTH' || $.%s['finishReason'] == 'MAX_TOKENS' || (%s)) ) { true; } else { false; }",
                        loopRef, maxTurns, llmRef, llmRef, guardrailContinue);

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(llmRef, "${" + llmRef + "}");
        loopInputs.put("_stop_requested", "${workflow.variables._stop_requested}");
        addGuardrailInputs(loopInputs, guardrailRefs);
        WorkflowTask loop = buildDoWhile(loopRef, termCondition, loopTasks, loopInputs);

        // Post-loop: resolve output (guardrail fix or human edit may override LLM output)
        String resolveRef = toRef(config.getName()) + "_resolve_output";
        WorkflowTask resolveTask = buildResolveOutputTask(resolveRef, llmRef);

        List<WorkflowTask> tasks = new ArrayList<>(resolvedInstructions.getPreTasks());
        tasks.addAll(prefill.tasks());
        tasks.add(loop);
        tasks.add(resolveTask);
        wf.setTasks(tasks);
        Map<String, Object> hierOutput = new LinkedHashMap<>();
        hierOutput.put("result", ref(resolveRef + ".output.result.result"));
        hierOutput.put("finishReason", ref(resolveRef + ".output.result.finishReason"));
        hierOutput.put("context", "${workflow.input.context}");
        wf.setOutputParameters(hierOutput);
        applyTimeout(wf, config);
        return wf;
    }

    // ── Agent with tools ────────────────────────────────────────────

    /**
     * Collect {@code toolName -> [credentialNames]} for the agent's tools: each tool's own declared
     * credentials, falling back to the agent-level credential list. Used by {@code AgentService} to
     * declare each worker tool's {@code TaskDef.runtimeMetadata} (embedded), so the host resolves
     * the names at the SIMPLE task's poll and injects the values onto {@code Task.runtimeMetadata}.
     */
    public static Map<String, List<String>> collectToolCredentials(AgentConfig config) {
        List<String> agentCreds =
                config.getCredentials() != null ? config.getCredentials() : List.of();
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (config.getTools() != null) {
            for (ToolConfig tool : config.getTools()) {
                if (tool.getName() == null) continue;
                List<String> own = new ArrayList<>();
                if (tool.getConfig() != null
                        && tool.getConfig().get("credentials") instanceof List<?> cl) {
                    for (Object c : cl) {
                        if (c instanceof String s) own.add(s);
                    }
                }
                List<String> effective = own.isEmpty() ? agentCreds : own;
                if (!effective.isEmpty())
                    map.put(tool.getName(), new ArrayList<>(new LinkedHashSet<>(effective)));
            }
        }
        return map;
    }

    /**
     * Collect the agent-level credential names, deduped and order-preserving. Used by {@code
     * AgentService} to declare {@code TaskDef.runtimeMetadata} (embedded) on the non-worker SIMPLE
     * tasks that run user-authored code — guardrails, callbacks, stop_when, gates, instructions,
     * routers, graph node/edge workers — none of which carry their own per-item credential list, so
     * the agent-level list is their only source. The host resolves the names at each task's poll
     * and injects the values onto the wire-only {@code Task.runtimeMetadata}.
     *
     * <p><b>Note:</b> the SDK worker wrappers for these non-worker task kinds do not yet read
     * {@code Task.runtimeMetadata} (only the tool worker does), so declaring it here is currently
     * inert — the values ride the wire but {@code get_secret()} inside those user functions won't
     * resolve until the SDK wrappers are taught to route {@code runtimeMetadata} into the
     * credential context.
     */
    public static List<String> collectAgentCredentials(AgentConfig config) {
        if (config.getCredentials() == null || config.getCredentials().isEmpty()) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(config.getCredentials()));
    }

    WorkflowDef compileWithTools(AgentConfig config) {
        ParsedModel parsed = ModelParser.parse(config.getModel());
        String llmRef = toRef(config.getName()) + "_llm";
        String instructionsRef = toRef(config.getName()) + "_instructions";
        List<ToolConfig> tools = config.getTools();

        ToolCompiler tc = new ToolCompiler();
        boolean hasApproval = tools.stream().anyMatch(ToolConfig::isApprovalRequired);
        boolean hasMcp = tools.stream().anyMatch(t -> "mcp".equals(t.getToolType()));
        boolean hasApi = tools.stream().anyMatch(t -> "api".equals(t.getToolType()));

        WorkflowDef wf = createWorkflow(config);
        ResolvedInstructions resolvedInstructions = resolveInstructions(config, instructionsRef);

        // ── Discovery (pre-loop tasks) or static tool specs ──────────
        ToolCompiler.DiscoveryResult discoveryResult = null;
        List<Map<String, Object>> toolSpecs = null;

        if (hasMcp || hasApi) {
            List<ToolConfig> staticTools =
                    tools.stream()
                            .filter(
                                    t ->
                                            !"mcp".equals(t.getToolType())
                                                    && !"api".equals(t.getToolType()))
                            .toList();
            List<ToolConfig> mcpTools =
                    tools.stream().filter(t -> "mcp".equals(t.getToolType())).toList();
            List<ToolConfig> apiTools =
                    tools.stream().filter(t -> "api".equals(t.getToolType())).toList();

            List<Map<String, Object>> staticSpecs = tc.compileToolSpecs(staticTools);

            if (hasMcp && hasApi) {
                discoveryResult =
                        tc.buildDiscoveryTasks(
                                config.getName(),
                                mcpTools,
                                apiTools,
                                staticSpecs,
                                config.getModel());
            } else if (hasMcp) {
                discoveryResult =
                        tc.buildMcpDiscoveryTasks(
                                config.getName(), mcpTools, staticSpecs, config.getModel());
            } else {
                discoveryResult =
                        tc.buildApiDiscoveryTasks(
                                config.getName(), apiTools, staticSpecs, config.getModel());
            }
        } else {
            toolSpecs = tc.compileToolSpecs(tools);
        }

        // Compile prefill tool calls (pre-loop tasks + message refs)
        PrefillCompilationResult prefill = compilePrefillTasks(config);

        // Build LLM task
        WorkflowTask llmTask;
        if (discoveryResult != null) {
            // LLM task with null toolSpecs; wire dynamic tools ref after
            llmTask = buildLlmTask(config, parsed, llmRef, null, prefill.refs());
            llmTask.getInputParameters().put("tools", discoveryResult.getToolsRef());
        } else {
            llmTask = buildLlmTask(config, parsed, llmRef, toolSpecs, prefill.refs());
        }

        // Inject human feedback context for agents with approval-required tools.
        // When a human responds with custom data (e.g. {"approved": true, "department": "eng"}),
        // the extra fields are stored in workflow.variables._human_feedback.
        // This message makes those fields visible to the LLM on subsequent iterations.
        if (hasApproval) {
            @SuppressWarnings("unchecked")
            List<Object> msgs = (List<Object>) llmTask.getInputParameters().get("messages");
            msgs.add(
                    Map.of(
                            "role", "system",
                            "message", "${workflow.variables._human_feedback}"));
        }

        // Tool call routing SwitchTask (with tool-level guardrail metadata)
        ToolCompiler.ToolCallRoutingResult toolRoutingResult;
        if (discoveryResult != null) {
            toolRoutingResult =
                    tc.buildToolCallRoutingDynamicWithResult(
                            config.getName(),
                            llmRef,
                            tools,
                            hasApproval,
                            config.getModel(),
                            discoveryResult.getMcpConfigRef(),
                            discoveryResult.getApiConfigRef());
        } else {
            toolRoutingResult =
                    tc.buildToolCallRoutingWithResult(
                            config.getName(), llmRef, tools, hasApproval, config.getModel());
        }
        WorkflowTask toolRouter = toolRoutingResult.getRouterTask();

        // Build loop body
        List<WorkflowTask> loopTasks = new ArrayList<>();

        // Context injection: compute state/signals prefix (prompt is appended via template)
        String ctxInjectRef = toRef(config.getName()) + "_ctx_inject";
        WorkflowTask ctxInject = new WorkflowTask();
        ctxInject.setType("INLINE");
        ctxInject.setTaskReferenceName(ctxInjectRef);
        Map<String, Object> ctxInjectInputs = new LinkedHashMap<>();
        ctxInjectInputs.put("evaluatorType", "graaljs");
        ctxInjectInputs.put("state", "${workflow.variables._agent_state}");
        ctxInjectInputs.put("signals", "${workflow.variables._signal_injection}");
        ctxInjectInputs.put("maxSize", contextMaxSizeBytes);
        ctxInjectInputs.put("maxValueSize", contextMaxValueSizeBytes);
        ctxInjectInputs.put("expression", JavaScriptBuilder.contextInjectionScript());
        ctxInject.setInputParameters(ctxInjectInputs);
        loopTasks.add(ctxInject);

        // Replace user message prompt with context prefix + base prompt.
        // ctx_inject outputs only the state/signals prefix (small, changes per turn)
        // with its own trailing '\n\n' separator when non-empty, empty otherwise —
        // so concatenation never injects a leading-whitespace artifact when there's
        // no context to prepend. The base prompt is referenced once via
        // ${workflow.input.prompt} — Conductor resolves both ${} references but
        // only the prefix is stored per-turn.
        @SuppressWarnings("unchecked")
        List<Object> llmMessages = (List<Object>) llmTask.getInputParameters().get("messages");
        for (int mi = 0; mi < llmMessages.size(); mi++) {
            if (llmMessages.get(mi) instanceof Map<?, ?> msg && "user".equals(msg.get("role"))) {
                Map<String, Object> injectedMsg = new LinkedHashMap<>();
                injectedMsg.put("role", "user");
                injectedMsg.put(
                        "message", "${" + ctxInjectRef + ".output.result}${workflow.input.prompt}");
                injectedMsg.put("media", "${workflow.input.media}");
                llmMessages.set(mi, injectedMsg);
                break;
            }
        }

        // Callback: before_model (runs before each LLM call in the loop)
        CallbackConfig beforeModel = findCallback(config, "before_model");
        if (beforeModel != null) {
            loopTasks.add(buildCallbackTask(beforeModel, config.getName(), llmRef));
        }

        loopTasks.add(llmTask);

        // Callback: after_model (runs after each LLM call in the loop)
        CallbackConfig afterModel = findCallback(config, "after_model");
        if (afterModel != null) {
            loopTasks.add(buildCallbackTask(afterModel, config.getName(), llmRef));
        }

        // Output guardrails (inside loop, after LLM)
        List<GuardrailConfig> outputGuardrails = getOutputGuardrails(config);
        List<String[]> guardrailRefs = new ArrayList<>();
        List<String> retryRefs = new ArrayList<>();

        if (!outputGuardrails.isEmpty()) {
            String contentRef = ref(llmRef + ".output.result");
            GuardrailCompiler gc = new GuardrailCompiler();
            List<GuardrailCompiler.GuardrailTaskResult> guardrailResults =
                    gc.compileGuardrailTasks(outputGuardrails, config.getName(), contentRef);

            for (int idx = 0; idx < guardrailResults.size(); idx++) {
                GuardrailCompiler.GuardrailTaskResult gr = guardrailResults.get(idx);
                String suffix = guardrailResults.size() > 1 ? "_" + idx : "";
                GuardrailCompiler.GuardrailRoutingResult routing =
                        gc.compileGuardrailRouting(
                                outputGuardrails.get(idx),
                                gr.getRefName(),
                                contentRef,
                                config.getName(),
                                suffix,
                                gr.isInline(),
                                config.getModel());
                loopTasks.addAll(gr.getTasks());
                loopTasks.add(routing.getSwitchTask());
                guardrailRefs.add(new String[] {gr.getRefName(), String.valueOf(gr.isInline())});
                retryRefs.add(routing.getRetryRef());
            }
        }

        loopTasks.add(toolRouter);

        // Merge tool-level guardrail refs (from tool routing) into tracking lists
        guardrailRefs.addAll(toolRoutingResult.getToolGuardrailRefs());
        retryRefs.addAll(toolRoutingResult.getToolGuardrailRetryRefs());

        // Wire all retry refs (agent + tool guardrails) into LLM participants
        if (!retryRefs.isEmpty()) {
            Map<String, Object> participants = new LinkedHashMap<>();
            for (String rr : retryRefs) {
                participants.put(rr, "user");
            }
            llmTask.getInputParameters().put("participants", participants);
        }

        // Optional stop_when worker
        String stopWhenRef = null;
        if (config.getStopWhen() != null) {
            WorkflowTask stopWhenTask =
                    TerminationCompiler.compileStopWhen(
                            config.getStopWhen().getTaskName(), config.getName(), llmRef);
            loopTasks.add(stopWhenTask);
            stopWhenRef = toRef(config.getName()) + "_stop_when";
        }

        // Optional termination condition
        String terminationRef = null;
        if (config.getTermination() != null) {
            WorkflowTask termTask =
                    TerminationCompiler.compileTermination(
                            config.getTermination(), config.getName(), llmRef);
            loopTasks.add(termTask);
            terminationRef = toRef(config.getName()) + "_termination";
        }

        // DoWhile loop
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;

        String hasToolCalls =
                String.format(
                        "($.%s['toolCalls'] != null && $.%s['toolCalls'].length > 0)",
                        llmRef, llmRef);

        String loopReason;
        if (!guardrailRefs.isEmpty()) {
            String guardrailContinue = buildGuardrailContinue(guardrailRefs);
            loopReason = "(" + hasToolCalls + " || " + guardrailContinue + ")";
        } else {
            loopReason = hasToolCalls;
        }

        StringBuilder termCondition = new StringBuilder();
        termCondition.append(
                String.format(
                        "if ( $.%s['iteration'] < %d && $._stop_requested != true && ($.%s['finishReason'] == 'LENGTH' || $.%s['finishReason'] == 'MAX_TOKENS' || %s)",
                        loopRef, maxTurns, llmRef, llmRef, loopReason));
        // stop_when: always evaluate — user callbacks check external state (e.g.
        // file existence) that must be respected even on tool-call turns.
        if (stopWhenRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", stopWhenRef));
        }
        // termination: always evaluate. The TerminationCondition implementations
        // already handle tool-call turns correctly — text_mention/stop_message
        // return should_continue=true when the LLM result is empty (which is
        // what happens on tool-call turns), and count-based terminations
        // (max_message, token_usage) must fire regardless of LLM output. The
        // earlier ``finishReason == 'TOOL_CALLS' || …`` short-circuit broke
        // MaxMessage termination because the loop kept iterating past the
        // configured limit on every tool-call turn.
        if (terminationRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", terminationRef));
        }
        termCondition.append(" ) { true; } else { false; }");

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(llmRef, "${" + llmRef + "}");
        loopInputs.put("_stop_requested", "${workflow.variables._stop_requested}");
        if (stopWhenRef != null) loopInputs.put(stopWhenRef, "${" + stopWhenRef + "}");
        if (terminationRef != null) loopInputs.put(terminationRef, "${" + terminationRef + "}");
        addGuardrailInputs(loopInputs, guardrailRefs);
        WorkflowTask loop = buildDoWhile(loopRef, termCondition.toString(), loopTasks, loopInputs);

        // ── Final workflow tasks ─────────────────────────────────────
        List<WorkflowTask> allTasks = new ArrayList<>();

        // Callback: before_agent (runs once before the loop)
        CallbackConfig beforeAgent = findCallback(config, "before_agent");
        if (beforeAgent != null) {
            allTasks.add(buildCallbackTask(beforeAgent, config.getName(), null));
        }

        if (discoveryResult != null) {
            allTasks.addAll(discoveryResult.getPreTasks());
        }
        allTasks.addAll(resolvedInstructions.getPreTasks());

        // Resolve input context with null fallback (INLINE → SET_VARIABLE pattern)
        String ctxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask ctxResolve = new WorkflowTask();
        ctxResolve.setType("INLINE");
        ctxResolve.setTaskReferenceName(ctxResolveRef);
        ctxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));
        allTasks.add(ctxResolve);

        // Initialize workflow variables
        Map<String, Object> initVars = new LinkedHashMap<>();
        initVars.put("_agent_state", "${" + ctxResolveRef + ".output.result}");
        initVars.put("_stop_requested", false);
        initVars.put("_signal_injection", "");
        if (hasApproval) {
            // Pre-initialize to empty string so the system message doesn't
            // have null content on the first loop iteration.
            initVars.put("_human_feedback", "");
        }
        WorkflowTask initState = new WorkflowTask();
        initState.setType("SET_VARIABLE");
        initState.setTaskReferenceName(toRef(config.getName()) + "_init_state");
        initState.setInputParameters(initVars);
        allTasks.add(initState);

        // Prefill tool calls: execute before the loop so results are in LLM context
        allTasks.addAll(prefill.tasks());

        // Required tools enforcement: wrap loop + check in outer DO_WHILE
        if (config.getRequiredTools() != null && !config.getRequiredTools().isEmpty()) {
            String checkRef = toRef(config.getName()) + "_required_tools_check";
            WorkflowTask checkTask = new WorkflowTask();
            checkTask.setType("INLINE");
            checkTask.setTaskReferenceName(checkRef);
            checkTask.setInputParameters(
                    Map.of(
                            "evaluatorType", "graaljs",
                            "expression",
                                    JavaScriptBuilder.requiredToolsCheckScript(
                                            config.getRequiredTools()),
                            "completedTaskNames", ref(loopRef + ".output")));

            String outerLoopRef = toRef(config.getName()) + "_required_tools_loop";
            String outerCondition =
                    String.format(
                            "if ( $.%s.output.satisfied == false && $.%s['iteration'] < 3 ) { true; } else { false; }",
                            checkRef, outerLoopRef);
            Map<String, Object> outerInputs = new LinkedHashMap<>();
            outerInputs.put(checkRef, "${" + checkRef + "}");
            outerInputs.put(outerLoopRef, "${" + outerLoopRef + "}");

            WorkflowTask outerLoop =
                    buildDoWhile(
                            outerLoopRef, outerCondition, List.of(loop, checkTask), outerInputs);
            allTasks.add(outerLoop);
        } else {
            allTasks.add(loop);
        }

        // Callback: after_agent (runs once after the loop)
        CallbackConfig afterAgent = findCallback(config, "after_agent");
        if (afterAgent != null) {
            allTasks.add(buildCallbackTask(afterAgent, config.getName(), llmRef));
        }

        // Post-loop: resolve output (guardrail fix or human edit may override LLM output)
        List<GuardrailConfig> outGuardrails = getOutputGuardrails(config);
        if (!outGuardrails.isEmpty()) {
            String resolveRef = toRef(config.getName()) + "_resolve_output";
            allTasks.add(buildResolveOutputTask(resolveRef, llmRef));

            Map<String, Object> outputParams = new LinkedHashMap<>();
            outputParams.put("result", ref(resolveRef + ".output.result.result"));
            outputParams.put("finishReason", ref(resolveRef + ".output.result.finishReason"));
            outputParams.put("rejectionReason", "${workflow.variables.rejectionReason}");
            outputParams.put("context", "${workflow.variables._agent_state}");
            wf.setOutputParameters(outputParams);
        } else {
            // Synthesize a non-empty workflow ``result`` even when the loop
            // terminated on a TOOL_CALLS turn (e.g. ``stop_when`` fired right
            // after the model called ``write_coder_plan``). Without this, the
            // LLM's empty text result becomes the agent's output and the
            // downstream stage sees nothing useful. This INLINE task prefers
            // the LLM's text; if empty, falls back to a JSON dump of the last
            // turn's tool-call inputs (which is where ``write_*`` tools put
            // their content arg).
            String synthRef = toRef(config.getName()) + "_synth_output";
            allTasks.add(buildSynthesizeOutputTask(synthRef, llmRef));

            Map<String, Object> outputParams = new LinkedHashMap<>();
            outputParams.put("result", ref(synthRef + ".output.result"));
            outputParams.put("finishReason", ref(llmRef + ".output.finishReason"));
            outputParams.put("rejectionReason", "${workflow.variables.rejectionReason}");
            outputParams.put("context", "${workflow.variables._agent_state}");
            wf.setOutputParameters(outputParams);
        }

        wf.setTasks(allTasks);
        applyTimeout(wf, config);
        return wf;
    }

    // ── Hybrid: tools AND sub-agents ────────────────────────────────

    WorkflowDef compileHybrid(AgentConfig config) {
        ParsedModel parsed = ModelParser.parse(config.getModel());
        String llmRef = toRef(config.getName()) + "_llm";
        String instructionsRef = toRef(config.getName()) + "_instructions";

        // Build transfer tools for each sub-agent
        List<ToolConfig> allTools = new ArrayList<>(config.getTools());
        for (AgentConfig sub : config.getAgents()) {
            String subDesc =
                    sub.getDescription() != null && !sub.getDescription().isEmpty()
                            ? sub.getDescription()
                            : (sub.getInstructions() instanceof String
                                    ? (String) sub.getInstructions()
                                    : "Agent: " + sub.getName());
            ToolConfig transferTool =
                    ToolConfig.builder()
                            .name(toRef(config.getName()) + "_transfer_to_" + toRef(sub.getName()))
                            .description(
                                    "Transfer the conversation to "
                                            + sub.getName()
                                            + ". "
                                            + subDesc)
                            .inputSchema(
                                    Map.of(
                                            "type",
                                            "object",
                                            "properties",
                                            Map.of(),
                                            "required",
                                            List.of()))
                            .toolType("worker")
                            .build();
            allTools.add(transferTool);
        }

        ToolCompiler tc = new ToolCompiler();
        boolean hasApproval = allTools.stream().anyMatch(ToolConfig::isApprovalRequired);
        boolean hasMcp = allTools.stream().anyMatch(t -> "mcp".equals(t.getToolType()));
        boolean hasApi = allTools.stream().anyMatch(t -> "api".equals(t.getToolType()));

        WorkflowDef wf = createWorkflow(config);
        wf.setDescription("Hybrid agent: " + config.getName());
        ResolvedInstructions resolvedInstructions = resolveInstructions(config, instructionsRef);

        // ── Discovery or static tool specs ───────────────────────────
        ToolCompiler.DiscoveryResult discoveryResult = null;
        List<Map<String, Object>> toolSpecs = null;

        if (hasMcp || hasApi) {
            List<ToolConfig> staticTools =
                    allTools.stream()
                            .filter(
                                    t ->
                                            !"mcp".equals(t.getToolType())
                                                    && !"api".equals(t.getToolType()))
                            .toList();
            List<ToolConfig> mcpTools =
                    allTools.stream().filter(t -> "mcp".equals(t.getToolType())).toList();
            List<ToolConfig> apiTools =
                    allTools.stream().filter(t -> "api".equals(t.getToolType())).toList();
            List<Map<String, Object>> staticSpecs = tc.compileToolSpecs(staticTools);

            if (hasMcp && hasApi) {
                discoveryResult =
                        tc.buildDiscoveryTasks(
                                config.getName(),
                                mcpTools,
                                apiTools,
                                staticSpecs,
                                config.getModel());
            } else if (hasMcp) {
                discoveryResult =
                        tc.buildMcpDiscoveryTasks(
                                config.getName(), mcpTools, staticSpecs, config.getModel());
            } else {
                discoveryResult =
                        tc.buildApiDiscoveryTasks(
                                config.getName(), apiTools, staticSpecs, config.getModel());
            }
        } else {
            toolSpecs = tc.compileToolSpecs(allTools);
        }

        // Compile prefill tool calls (pre-loop tasks + message refs)
        PrefillCompilationResult hybridPrefill = compilePrefillTasks(config);

        // Build LLM task
        WorkflowTask llmTask;
        if (discoveryResult != null) {
            llmTask = buildLlmTask(config, parsed, llmRef, null, hybridPrefill.refs());
            llmTask.getInputParameters().put("tools", discoveryResult.getToolsRef());
        } else {
            llmTask = buildLlmTask(config, parsed, llmRef, toolSpecs, hybridPrefill.refs());
        }

        // Tool call routing (with tool-level guardrail metadata)
        ToolCompiler.ToolCallRoutingResult toolRoutingResult;
        if (discoveryResult != null) {
            toolRoutingResult =
                    tc.buildToolCallRoutingDynamicWithResult(
                            config.getName(),
                            llmRef,
                            allTools,
                            hasApproval,
                            config.getModel(),
                            discoveryResult.getMcpConfigRef(),
                            discoveryResult.getApiConfigRef());
        } else {
            toolRoutingResult =
                    tc.buildToolCallRoutingWithResult(
                            config.getName(), llmRef, allTools, hasApproval, config.getModel());
        }
        WorkflowTask toolRouter = toolRoutingResult.getRouterTask();

        // Check-transfer worker
        String checkTransferRef = toRef(config.getName()) + "_check_transfer";
        WorkflowTask checkTransferTask = new WorkflowTask();
        checkTransferTask.setName(toRef(config.getName()) + "_check_transfer");
        checkTransferTask.setTaskReferenceName(checkTransferRef);
        checkTransferTask.setType("SIMPLE");
        Map<String, Object> ctInputs = new LinkedHashMap<>();
        ctInputs.put("tool_calls", ref(llmRef + ".output.toolCalls"));
        checkTransferTask.setInputParameters(ctInputs);

        // Build loop body
        List<WorkflowTask> loopTasks = new ArrayList<>();

        // Context injection for hybrid loop (state/signals prefix only)
        String hybridCtxInjectRef = toRef(config.getName()) + "_ctx_inject";
        WorkflowTask hybridCtxInject = new WorkflowTask();
        hybridCtxInject.setType("INLINE");
        hybridCtxInject.setTaskReferenceName(hybridCtxInjectRef);
        Map<String, Object> hybridCtxInjectInputs = new LinkedHashMap<>();
        hybridCtxInjectInputs.put("evaluatorType", "graaljs");
        hybridCtxInjectInputs.put("state", "${workflow.variables._agent_state}");
        hybridCtxInjectInputs.put("signals", "${workflow.variables._signal_injection}");
        hybridCtxInjectInputs.put("maxSize", contextMaxSizeBytes);
        hybridCtxInjectInputs.put("maxValueSize", contextMaxValueSizeBytes);
        hybridCtxInjectInputs.put("expression", JavaScriptBuilder.contextInjectionScript());
        hybridCtxInject.setInputParameters(hybridCtxInjectInputs);
        loopTasks.add(hybridCtxInject);

        // Replace user message with context prefix + base prompt.
        // Prefix carries its own trailing '\n\n' when non-empty, empty otherwise —
        // see contextInjectionScript() docstring for why the joiner can't be a
        // literal here (leading whitespace shifts LLM behavior at temperature 0).
        @SuppressWarnings("unchecked")
        List<Object> hybridLlmMessages =
                (List<Object>) llmTask.getInputParameters().get("messages");
        for (int mi = 0; mi < hybridLlmMessages.size(); mi++) {
            if (hybridLlmMessages.get(mi) instanceof Map<?, ?> msg
                    && "user".equals(msg.get("role"))) {
                Map<String, Object> injectedMsg = new LinkedHashMap<>();
                injectedMsg.put("role", "user");
                injectedMsg.put(
                        "message",
                        "${" + hybridCtxInjectRef + ".output.result}${workflow.input.prompt}");
                injectedMsg.put("media", "${workflow.input.media}");
                hybridLlmMessages.set(mi, injectedMsg);
                break;
            }
        }

        loopTasks.add(llmTask);

        // Output guardrails
        List<GuardrailConfig> outputGuardrails = getOutputGuardrails(config);
        List<String[]> guardrailRefs = new ArrayList<>();
        List<String> retryRefs = new ArrayList<>();

        if (!outputGuardrails.isEmpty()) {
            String contentRef = ref(llmRef + ".output.result");
            GuardrailCompiler gc = new GuardrailCompiler();
            List<GuardrailCompiler.GuardrailTaskResult> guardrailResults =
                    gc.compileGuardrailTasks(outputGuardrails, config.getName(), contentRef);
            for (int idx = 0; idx < guardrailResults.size(); idx++) {
                GuardrailCompiler.GuardrailTaskResult gr = guardrailResults.get(idx);
                String suffix = guardrailResults.size() > 1 ? "_" + idx : "";
                GuardrailCompiler.GuardrailRoutingResult routing =
                        gc.compileGuardrailRouting(
                                outputGuardrails.get(idx),
                                gr.getRefName(),
                                contentRef,
                                config.getName(),
                                suffix,
                                gr.isInline(),
                                config.getModel());
                loopTasks.addAll(gr.getTasks());
                loopTasks.add(routing.getSwitchTask());
                guardrailRefs.add(new String[] {gr.getRefName(), String.valueOf(gr.isInline())});
                retryRefs.add(routing.getRetryRef());
            }
        }

        loopTasks.add(toolRouter);

        // Merge tool-level guardrail refs
        guardrailRefs.addAll(toolRoutingResult.getToolGuardrailRefs());
        retryRefs.addAll(toolRoutingResult.getToolGuardrailRetryRefs());

        // Wire all retry refs into LLM participants
        if (!retryRefs.isEmpty()) {
            Map<String, Object> participants = new LinkedHashMap<>();
            for (String rr : retryRefs) participants.put(rr, "user");
            llmTask.getInputParameters().put("participants", participants);
        }
        loopTasks.add(checkTransferTask);

        // DoWhile loop
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;

        String hasToolCalls =
                String.format(
                        "($.%s['toolCalls'] != null && $.%s['toolCalls'].length > 0)",
                        llmRef, llmRef);
        String notTransfer = String.format("($.%s.is_transfer != true)", checkTransferRef);

        String loopReason;
        if (!guardrailRefs.isEmpty()) {
            String guardrailContinue = buildGuardrailContinue(guardrailRefs);
            loopReason = "(" + hasToolCalls + " || " + guardrailContinue + ")";
        } else {
            loopReason = hasToolCalls;
        }

        String termCondition =
                String.format(
                        "if ( $.%s['iteration'] < %d && $._stop_requested != true && ($.%s['finishReason'] == 'LENGTH' || $.%s['finishReason'] == 'MAX_TOKENS' || (%s && %s)) ) { true; } else { false; }",
                        loopRef, maxTurns, llmRef, llmRef, loopReason, notTransfer);

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(llmRef, "${" + llmRef + "}");
        loopInputs.put("_stop_requested", "${workflow.variables._stop_requested}");
        loopInputs.put(checkTransferRef, "${" + checkTransferRef + "}");
        addGuardrailInputs(loopInputs, guardrailRefs);
        WorkflowTask loop = buildDoWhile(loopRef, termCondition, loopTasks, loopInputs);

        // After loop: SwitchTask routing to sub-agents
        WorkflowTask transferSwitch = new WorkflowTask();
        transferSwitch.setType("SWITCH");
        transferSwitch.setTaskReferenceName(toRef(config.getName()) + "_transfer_check");
        transferSwitch.setEvaluatorType("value-param");
        transferSwitch.setExpression("switchCaseValue");
        transferSwitch.setInputParameters(
                Map.of("switchCaseValue", ref(checkTransferRef + ".output.transfer_to")));

        Map<String, List<WorkflowTask>> transferCases = new LinkedHashMap<>();
        for (AgentConfig sub : config.getAgents()) {
            String subTaskRef = toRef(config.getName()) + "_transfer_" + toRef(sub.getName());
            WorkflowTask subTask =
                    compileSubAgent(
                            sub,
                            subTaskRef,
                            "${workflow.input.prompt}",
                            "${workflow.input.media}",
                            "${workflow.variables._agent_state}");
            transferCases.put(sub.getName(), List.of(subTask));
        }
        transferSwitch.setDecisionCases(transferCases);

        // Resolve input context with null fallback (INLINE → SET_VARIABLE pattern)
        String hybridCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask hybridCtxResolve = new WorkflowTask();
        hybridCtxResolve.setType("INLINE");
        hybridCtxResolve.setTaskReferenceName(hybridCtxResolveRef);
        hybridCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        // Initialize workflow variables
        Map<String, Object> initHybridVars = new LinkedHashMap<>();
        initHybridVars.put("_agent_state", "${" + hybridCtxResolveRef + ".output.result}");
        initHybridVars.put("_stop_requested", false);
        initHybridVars.put("_signal_injection", "");
        if (hasApproval) {
            initHybridVars.put("_human_feedback", "");
        }
        WorkflowTask initStateHybrid = new WorkflowTask();
        initStateHybrid.setType("SET_VARIABLE");
        initStateHybrid.setTaskReferenceName(toRef(config.getName()) + "_init_state");
        initStateHybrid.setInputParameters(initHybridVars);

        if (discoveryResult != null) {
            List<WorkflowTask> allTasks = new ArrayList<>(discoveryResult.getPreTasks());
            allTasks.addAll(resolvedInstructions.getPreTasks());
            allTasks.add(hybridCtxResolve);
            allTasks.add(initStateHybrid);
            allTasks.addAll(hybridPrefill.tasks());
            allTasks.add(loop);
            allTasks.add(transferSwitch);
            wf.setTasks(allTasks);
        } else {
            List<WorkflowTask> allTasks = new ArrayList<>(resolvedInstructions.getPreTasks());
            allTasks.add(hybridCtxResolve);
            allTasks.add(initStateHybrid);
            allTasks.addAll(hybridPrefill.tasks());
            allTasks.add(loop);
            allTasks.add(transferSwitch);
            wf.setTasks(allTasks);
        }

        // Output: direct result or transfer result
        Map<String, Object> outputRefs = new LinkedHashMap<>();
        outputRefs.put("direct", ref(llmRef + ".output.result"));
        for (AgentConfig sub : config.getAgents()) {
            outputRefs.put(
                    sub.getName(),
                    ref(
                            toRef(config.getName())
                                    + "_transfer_"
                                    + toRef(sub.getName())
                                    + ".output.result"));
        }
        Map<String, Object> hybridOutput = new LinkedHashMap<>();
        hybridOutput.put("result", outputRefs);
        hybridOutput.put("finishReason", ref(llmRef + ".output.finishReason"));
        hybridOutput.put("context", "${workflow.variables._agent_state}");
        wf.setOutputParameters(hybridOutput);
        applyTimeout(wf, config);
        return wf;
    }

    // ── Sub-agent compilation ───────────────────────────────────────

    /**
     * Compile a sub-agent into a workflow task. External -> SUB_WORKFLOW referencing by name. Local
     * -> SUB_WORKFLOW with inline workflowDef.
     */
    WorkflowTask compileSubAgent(
            AgentConfig sub, String taskRef, String promptRef, String mediaRef, String contextRef) {
        // Force passthrough compilation for Claude Code sub-agents
        String subModel = sub.getModel();
        if (subModel != null && subModel.startsWith("claude-code")) {
            if (sub.getMetadata() == null) {
                sub.setMetadata(new LinkedHashMap<>());
            }
            sub.getMetadata().put("_framework_passthrough", true);

            // Ensure the sub-agent has a worker tool if not already set
            if (sub.getTools() == null || sub.getTools().isEmpty()) {
                ToolConfig worker =
                        ToolConfig.builder()
                                .name(sub.getName())
                                .description("Claude Agent SDK passthrough worker")
                                .toolType("worker")
                                .build();
                sub.setTools(List.of(worker));
            }
        }

        WorkflowTask task = new WorkflowTask();
        task.setTaskReferenceName(taskRef);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("prompt", promptRef);
        inputs.put("media", mediaRef);
        inputs.put("session_id", "${workflow.input.session_id}");
        // Forward execution token to sub-workflows for credential resolution
        // Pass context to sub-workflow for pipeline state
        if (contextRef != null) {
            inputs.put("context", contextRef);
        }
        // When includeContents is "none", signal the sub-workflow to skip parent context
        if ("none".equalsIgnoreCase(sub.getIncludeContents())) {
            inputs.put("include_contents", "none");
        }

        if (sub.isExternal()) {
            task.setType("SUB_WORKFLOW");
            task.setName(sub.getName());
            task.setSubWorkflowParam(new SubWorkflowParams());
            task.getSubWorkflowParam().setName(sub.getName());
            task.setInputParameters(inputs);
        } else {
            WorkflowDef subWf = compile(sub);
            task.setType("SUB_WORKFLOW");
            task.setName(sub.getName());
            task.setSubWorkflowParam(new SubWorkflowParams());
            task.getSubWorkflowParam().setName(subWf.getName());
            task.getSubWorkflowParam().setWorkflowDef(subWf);
            task.setInputParameters(inputs);
        }

        return task;
    }

    /**
     * Return the Conductor expression for a sub-agent's string result. Sub-workflow tasks expose
     * the child workflow's outputParameters directly, so output.result is always the resolved
     * string value.
     */
    static String subAgentResultRef(AgentConfig sub, String taskRef) {
        return ref(taskRef + ".output.result");
    }

    /**
     * Create an INLINE task that coerces a sub-agent's result to a string. When a sub-agent's LLM
     * ends on a tool call (no text), output.result is null. This safely converts null → "", objects
     * → JSON string, anything else → String.
     */
    static WorkflowTask createCoerceTask(String rawRef, String coerceRefName) {
        WorkflowTask task = new WorkflowTask();
        task.setType("INLINE");
        task.setTaskReferenceName(coerceRefName);
        task.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function(){ var v = $.raw; "
                                + "return (v == null || v === undefined) ? '' : "
                                + "(typeof v === 'object' ? JSON.stringify(v) : String(v)); })()",
                        "raw",
                        rawRef));
        return task;
    }

    /** Return the Conductor expression for a coerced task's string result. */
    static String coercedRef(String coerceRefName) {
        return ref(coerceRefName + ".output.result");
    }

    // ── Shared helpers ──────────────────────────────────────────────

    WorkflowDef createWorkflow(AgentConfig config) {
        WorkflowDef wf = new WorkflowDef();
        wf.setName(config.getName());
        wf.setVersion(1);
        wf.setDescription("Agent workflow for " + config.getName());
        // Match Python SDK's ConductorWorkflow defaults
        wf.setTimeoutSeconds(60L);
        wf.setTimeoutPolicy(null);
        wf.setInputParameters(WORKFLOW_INPUTS);
        return wf;
    }

    /**
     * Compile prefill tool calls into pre-loop workflow tasks. Returns tasks to execute before the
     * DoWhile and refs for message injection.
     */
    PrefillCompilationResult compilePrefillTasks(AgentConfig config) {
        List<PrefillToolCallConfig> prefills = config.getPrefillTools();
        if (prefills == null || prefills.isEmpty()) return PrefillCompilationResult.EMPTY;

        // Map tool name -> ToolConfig for type lookup
        Map<String, ToolConfig> toolMap = new HashMap<>();
        if (config.getTools() != null) {
            for (ToolConfig tc : config.getTools()) toolMap.put(tc.getName(), tc);
        }

        List<WorkflowTask> tasks = new ArrayList<>();
        List<PrefillRef> refs = new ArrayList<>();

        for (int i = 0; i < prefills.size(); i++) {
            PrefillToolCallConfig ptc = prefills.get(i);
            String refName = toRef(config.getName()) + "_prefill_" + i;

            WorkflowTask task = new WorkflowTask();
            task.setName(ptc.getToolName());
            task.setTaskReferenceName(refName);
            task.setType("SIMPLE");

            Map<String, Object> inputs = new LinkedHashMap<>(ptc.getArguments());
            task.setInputParameters(inputs);

            tasks.add(task);
            refs.add(new PrefillRef(ptc.getToolName(), refName, ptc.getArguments()));
        }

        // Multiple prefill tools → static FORK_JOIN for parallel execution
        if (tasks.size() > 1) {
            List<List<WorkflowTask>> branches = tasks.stream().map(List::of).toList();
            WorkflowTask fork = new WorkflowTask();
            fork.setType("FORK_JOIN");
            fork.setTaskReferenceName(toRef(config.getName()) + "_prefill_fork");
            fork.setForkTasks(branches);

            WorkflowTask join = new WorkflowTask();
            join.setType("JOIN");
            join.setTaskReferenceName(toRef(config.getName()) + "_prefill_join");
            join.setJoinOn(tasks.stream().map(WorkflowTask::getTaskReferenceName).toList());

            return new PrefillCompilationResult(List.of(fork, join), refs);
        }
        return new PrefillCompilationResult(tasks, refs);
    }

    WorkflowTask buildLlmTask(
            AgentConfig config,
            ParsedModel parsed,
            String llmRef,
            List<Map<String, Object>> toolSpecs) {
        return buildLlmTask(config, parsed, llmRef, toolSpecs, List.of());
    }

    WorkflowTask buildLlmTask(
            AgentConfig config,
            ParsedModel parsed,
            String llmRef,
            List<Map<String, Object>> toolSpecs,
            List<PrefillRef> prefillRefs) {
        WorkflowTask llm = new WorkflowTask();
        llm.setName("LLM_CHAT_COMPLETE");
        llm.setTaskReferenceName(llmRef);
        llm.setType("LLM_CHAT_COMPLETE");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("llmProvider", parsed.getProvider());
        inputs.put("model", parsed.getModel());

        // Per-agent base URL override
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            inputs.put("baseUrl", config.getBaseUrl());
        }

        // Build messages
        List<Object> messages = new ArrayList<>();

        // Handle instructions
        Object instructions = config.getInstructions();
        boolean useTemplate =
                instructions instanceof Map
                        && ((Map<?, ?>) instructions).containsKey("name")
                        && ((Map<?, ?>) instructions).containsKey("type")
                        && "prompt_template".equals(((Map<?, ?>) instructions).get("type"));

        if (useTemplate) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tmpl = (Map<String, Object>) instructions;
            inputs.put("instructionsTemplate", tmpl.get("name"));
            if (tmpl.get("variables") != null) {
                inputs.put("templateVariables", tmpl.get("variables"));
            }
            if (tmpl.get("version") != null) {
                inputs.put("promptVersion", tmpl.get("version"));
            }
        } else {
            // Inline string instructions
            String instrText =
                    resolveInstructions(config, toRef(config.getName()) + "_instructions")
                            .getText();
            if (toolSpecs != null && instrText.isEmpty()) {
                instrText = "You are a helpful assistant.";
            }

            // Append structured output schema to system prompt (both tool and simple agents)
            if (config.getOutputType() != null && config.getOutputType().getSchema() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = config.getOutputType().getSchema();
                // Inline $ref references and simplify to a human-readable type description
                Map<String, Object> resolved = inlineRefs(schema);
                String schemaStr = simplifySchema(resolved);
                if (toolSpecs != null) {
                    instrText +=
                            "\n\nWhen providing your final answer, respond "
                                    + "with a JSON object matching this schema: "
                                    + schemaStr
                                    + ". "
                                    + "Output only valid JSON.";
                } else {
                    instrText +=
                            (instrText.isEmpty() ? "" : "\n\n")
                                    + "Respond with a JSON object matching this schema: "
                                    + schemaStr
                                    + ". Output only valid JSON, no other text.";
                    inputs.put("jsonOutput", true);
                }
            }

            // Append code execution instructions (both tool and simple agents)
            if (config.getCodeExecution() != null && config.getCodeExecution().isEnabled()) {
                instrText += "\n\n" + buildCodeExecInstructions(config);
            }

            // Append CLI command execution instructions
            if (config.getCliConfig() != null && config.getCliConfig().isEnabled()) {
                instrText += "\n\n" + buildCliInstructions(config);
            }

            // Plan-first preamble: enhance instructions with plan-then-execute prompt
            if (Boolean.TRUE.equals(config.getEnablePlanning())) {
                instrText +=
                        "\n\nBefore executing, create a step-by-step plan. "
                                + "Think through each step carefully, then execute the plan "
                                + "systematically using your available tools. After each step, "
                                + "verify progress before moving to the next.";
            }

            if (!instrText.isEmpty()) {
                messages.add(Map.of("role", "system", "message", instrText));
            }
        }

        // Memory messages
        if (config.getMemory() != null && config.getMemory().getMessages() != null) {
            messages.addAll(config.getMemory().getMessages());
        }

        // Prefill tool call results: inject as a SINGLE system message containing
        // all prefill outputs concatenated as labeled sections. Previously this
        // emitted one ``tool_call`` + one ``tool`` message per prefill, which
        // left those tool names visible in conversation history — the LLM kept
        // hallucinating calls to them (contextbook_read, list_directory,
        // git_status, git_diff) on every subsequent turn, wasting tool budgets
        // and flooding logs even though the dispatch guard rejected them. The
        // model can't hallucinate a call to something it's never seen as a
        // ``tool_call`` in history.
        //
        // Conductor's ``${refName.output.field}`` placeholders resolve inside
        // string values at task-scheduling time, so the single message body
        // here is dynamically filled with the actual prefill task outputs.
        if (prefillRefs != null && !prefillRefs.isEmpty()) {
            StringBuilder ctx = new StringBuilder();
            ctx.append("# Pre-loaded context\n\n")
                    .append("The following inputs were collected deterministically at the start ")
                    .append("of this run and are provided here as static context. They are NOT ")
                    .append("callable tools in this conversation — do not attempt to call any of ")
                    .append("them. If you need fresh information, use the tools advertised in ")
                    .append("your tool list.\n\n");
            for (PrefillRef pr : prefillRefs) {
                ctx.append("## ").append(pr.toolName());
                Map<String, Object> args = pr.arguments();
                if (args != null && !args.isEmpty()) {
                    String summary =
                            args.entrySet().stream()
                                    .map(e -> e.getKey() + "=" + e.getValue())
                                    .collect(Collectors.joining(", "));
                    if (!summary.isEmpty()) {
                        ctx.append("(").append(summary).append(")");
                    }
                }
                ctx.append("\n\n")
                        .append("${")
                        .append(pr.refName())
                        .append(".output.result}")
                        .append("\n\n");
            }
            messages.add(Map.of("role", "system", "message", ctx.toString()));
        }

        // User message
        messages.add(USER_MESSAGE);

        inputs.put("messages", messages);

        if (toolSpecs != null) {
            inputs.put("tools", toolSpecs);
        }

        // Default maxTokens to 16384 when not explicitly configured.
        // Without this, Spring AI defaults to 500 which is too low for agents
        // that need to generate tool calls with complex arguments.
        inputs.put("maxTokens", config.getMaxTokens() != null ? config.getMaxTokens() : 16384);

        // Context window budget for proactive condensation
        if (config.getContextWindowBudget() != null) {
            inputs.put("contextWindowBudget", config.getContextWindowBudget());
        }

        // Temperature: default 0 for tool agents, null otherwise
        if (config.getTemperature() != null) {
            inputs.put("temperature", config.getTemperature());
        } else if (toolSpecs != null) {
            inputs.put("temperature", 0);
        }

        // Thinking config: extended reasoning. ChatCompletion's wire key is
        // ``thinkingTokenLimit`` (an int) — there is NO ``thinkingConfig`` property on it, and
        // the task-input ObjectMapper ignores unknown keys, so emitting a
        // ``thinkingConfig: {enabled, budgetTokens}`` map here would be silently dropped and
        // thinking would never activate (that was a live bug). A positive limit turns thinking on
        // for both Anthropic (thinking block / adaptive) and Gemini (thinkingConfig.budget).
        boolean thinkingEnabled =
                config.getThinkingConfig() != null && config.getThinkingConfig().isEnabled();
        if (thinkingEnabled) {
            Integer budget = config.getThinkingConfig().getBudgetTokens();
            // enabled without an explicit budget → sensible default (Anthropic minimum is 1024;
            // must stay below the 16384 maxTokens default above).
            inputs.put(
                    "thinkingTokenLimit",
                    budget != null && budget > 0 ? budget : DEFAULT_THINKING_BUDGET_TOKENS);
        }

        // Reasoning effort — forwarded to ChatCompletion.reasoningEffort via
        // Jackson's convertValue in AgentChatCompleteTaskMapper. OpenAI
        // reasoning models (o1, gpt-5-codex) accept minimal|low|medium|high;
        // non-reasoning OpenAI models ignore it. Targets the failure mode where
        // codex spends all completion tokens on internal reasoning and emits
        // finishReason=STOP with empty content.
        //
        // Anthropic is NOT a silent no-op: AnthropicChatModel forwards it as
        // ``output_config.effort``, which modulates thinking on adaptive models (Opus 4.7+,
        // Fable). To keep the agent-level invariant "thinking is used ONLY when thinkingConfig
        // is set", effort is forwarded to Anthropic only when thinking is enabled.
        if (config.getReasoningEffort() != null && !config.getReasoningEffort().isBlank()) {
            boolean anthropic = "anthropic".equalsIgnoreCase(parsed.getProvider());
            if (!anthropic || thinkingEnabled) {
                inputs.put("reasoningEffort", config.getReasoningEffort());
                // OpenAI's Responses API only emits chain-of-thought summary text
                // on ``reasoning`` output items when ``reasoning.summary`` is set
                // on the request. Without it, the model burns reasoning tokens
                // but the summary blocks come back empty and conductor's
                // OpenAIResponsesChatModel has nothing to surface. Default to
                // ``auto`` so reasoning-effort callers get visible reasoning
                // output by default. Non-reasoning models silently ignore it.
                inputs.put("reasoningSummary", "auto");
            } else {
                log.debug(
                        "Dropping reasoningEffort for agent '{}': provider is anthropic and "
                                + "thinkingConfig is not enabled (effort would modulate thinking "
                                + "on adaptive models)",
                        config.getName());
            }
        }

        // Forward execution token so per-user credential resolution works in worker threads

        llm.setInputParameters(inputs);

        // Retry the LLM call on TRANSIENT provider failures (e.g. OpenAI
        // "503 upstream connect error / disconnect/reset before headers", or a
        // brief gateway blip). The LLM_CHAT_COMPLETE task fails as FAILED
        // (retryable — not FAILED_WITH_TERMINAL_ERROR), so an inline TaskDef
        // retry policy makes Conductor re-issue the call with exponential
        // backoff before the failure bubbles up and aborts the agent's turn
        // (which would otherwise kill a whole retrieval/reasoning round).
        TaskDef llmRetryDef = new TaskDef();
        llmRetryDef.setName("LLM_CHAT_COMPLETE");
        llmRetryDef.setRetryCount(3);
        llmRetryDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
        llmRetryDef.setRetryDelaySeconds(2);
        llmRetryDef.setBackoffScaleFactor(2);
        llm.setTaskDefinition(llmRetryDef);

        return llm;
    }

    ResolvedInstructions resolveInstructions(AgentConfig config, String refName) {
        Object instructions = config.getInstructions();
        if (!(instructions instanceof Map<?, ?> map) || !map.containsKey("_worker_ref")) {
            String text =
                    instructions instanceof String
                            ? (String) instructions
                            : instructions != null ? instructions.toString() : "";
            return new ResolvedInstructions(List.of(), text);
        }

        Object taskNameObj = map.get("_worker_ref");
        if (!(taskNameObj instanceof String taskName) || taskName.isBlank()) {
            return new ResolvedInstructions(List.of(), "");
        }

        String workerRef = refName + "_worker";

        WorkflowTask workerTask = new WorkflowTask();
        workerTask.setName(taskName);
        workerTask.setTaskReferenceName(workerRef);
        workerTask.setType("SIMPLE");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("prompt", "${workflow.input.prompt}");
        ctx.put("session_id", "${workflow.input.session_id}");
        ctx.put("media", "${workflow.input.media}");

        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("name", config.getName());
        if (config.getModel() != null) {
            agent.put("model", config.getModel());
        }
        if (config.getDescription() != null) {
            agent.put("description", config.getDescription());
        }
        if (config.getMetadata() != null && !config.getMetadata().isEmpty()) {
            agent.put("metadata", config.getMetadata());
        }

        Map<String, Object> workerInputs = new LinkedHashMap<>();
        workerInputs.put("ctx", ctx);
        workerInputs.put("context", ctx);
        workerInputs.put("agent", agent);
        workerInputs.put("prompt", "${workflow.input.prompt}");
        workerInputs.put("session_id", "${workflow.input.session_id}");
        workerInputs.put("sessionId", "${workflow.input.session_id}");
        workerInputs.put("media", "${workflow.input.media}");
        workerTask.setInputParameters(workerInputs);

        WorkflowTask normalizeTask = new WorkflowTask();
        normalizeTask.setTaskReferenceName(refName);
        normalizeTask.setType("INLINE");
        Map<String, Object> normalizeInputs = new LinkedHashMap<>();
        normalizeInputs.put("evaluatorType", "graaljs");
        normalizeInputs.put("expression", JavaScriptBuilder.normalizeInstructionsScript());
        normalizeInputs.put("worker_output", "${" + workerRef + ".output}");
        normalizeTask.setInputParameters(normalizeInputs);

        return new ResolvedInstructions(
                List.of(workerTask, normalizeTask), ref(refName + ".output.result"));
    }

    /**
     * Find the join point where all fork branches reconverge. Returns the first node reachable from
     * ALL branch targets, or null.
     */
    static String findJoinPoint(List<String> forkTargets, Map<String, List<String>> adjacency) {
        // BFS from each branch target to collect reachable node sets
        List<Set<String>> branchReachable = new ArrayList<>();
        for (String target : forkTargets) {
            Set<String> reachable = new LinkedHashSet<>();
            List<String> bfs = new ArrayList<>();
            bfs.add(target);
            while (!bfs.isEmpty()) {
                String node = bfs.remove(0);
                if (!reachable.add(node)) continue;
                List<String> nexts = adjacency.getOrDefault(node, List.of());
                bfs.addAll(nexts);
            }
            branchReachable.add(reachable);
        }
        // Intersection: nodes reachable from ALL branches
        Set<String> common = new LinkedHashSet<>(branchReachable.get(0));
        for (int i = 1; i < branchReachable.size(); i++) {
            common.retainAll(branchReachable.get(i));
        }
        common.remove("__end__");
        // Remove the fork targets themselves (they're branch starts, not join points)
        common.removeAll(forkTargets);
        return common.isEmpty() ? null : common.iterator().next();
    }

    /**
     * Apply LangGraph retry policy to a Conductor WorkflowTask. Maps max_attempts → retryCount
     * (WorkflowTask-level override).
     */
    static void applyRetryPolicy(WorkflowTask task, Map<String, Object> policy) {
        if (policy == null || policy.isEmpty()) return;

        Object maxAttempts = policy.get("max_attempts");
        if (maxAttempts instanceof Number) {
            // LangGraph counts total attempts, Conductor counts retries (attempts - 1)
            task.setRetryCount(Math.max(0, ((Number) maxAttempts).intValue() - 1));
        }

        // initial_interval and backoff_factor are task definition properties (not WorkflowTask).
        // Store them in inputParameters._retry_meta so the task registration layer can apply them
        // to the TaskDef when registering the worker.
        Map<String, Object> retryMeta = new LinkedHashMap<>();
        Object initialInterval = policy.get("initial_interval");
        if (initialInterval instanceof Number) {
            retryMeta.put(
                    "retryDelaySeconds",
                    Math.max(1, (int) Math.ceil(((Number) initialInterval).doubleValue())));
        }
        Object backoffFactor = policy.get("backoff_factor");
        if (backoffFactor instanceof Number) {
            retryMeta.put("backoffScaleFactor", ((Number) backoffFactor).intValue());
        }
        if (!retryMeta.isEmpty()) {
            task.getInputParameters().put("_retry_meta", retryMeta);
        }

        // Log unmapped params for visibility
        Set<String> mapped = Set.of("max_attempts", "initial_interval", "backoff_factor");
        for (String key : policy.keySet()) {
            if (!mapped.contains(key)) {
                log.warn(
                        "Retry policy param '{}' is not mapped to a Conductor retry setting and will be ignored",
                        key);
            }
        }
    }

    /**
     * Build a state-merge INLINE task that combines branch outputs respecting reducers.
     *
     * <p>For fields with an "add" reducer, lists/strings are concatenated across branches. For all
     * other fields, last-write-wins (the last branch with a non-null value wins).
     *
     * @param mergeRef unique taskReferenceName for the merge task
     * @param joinOnRefs list of last task refs from each branch (for JOIN)
     * @param branchStateExprs list of state expressions for each branch (e.g. "ref.output.state" or
     *     "ref.output.result.state" for INLINE tasks)
     * @param reducers field_name -> reducer_type map (e.g. "branch_outputs" -> "add")
     * @return a configured INLINE WorkflowTask
     */
    static WorkflowTask buildForkMergeTask(
            String mergeRef,
            List<String> joinOnRefs,
            List<String> branchStateExprs,
            Map<String, String> reducers) {
        WorkflowTask mergeTask = new WorkflowTask();
        mergeTask.setType("INLINE");
        mergeTask.setName(mergeRef);
        mergeTask.setTaskReferenceName(mergeRef);

        Map<String, Object> mergeInputs = new LinkedHashMap<>();
        mergeInputs.put("evaluatorType", "graaljs");

        // Build inputs: b0, b1, ... = each branch's state expression
        for (int i = 0; i < branchStateExprs.size(); i++) {
            mergeInputs.put("b" + i, "${" + branchStateExprs.get(i) + "}");
        }

        // Build merge expression with reducer awareness
        StringBuilder js = new StringBuilder("function e(){var m={};");

        // Collect reducer field names for the "add" reducer
        Set<String> addReducerFields = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : reducers.entrySet()) {
            if ("add".equals(entry.getValue())) {
                addReducerFields.add(entry.getKey());
            }
        }

        // Process each branch
        for (int i = 0; i < joinOnRefs.size(); i++) {
            String key = "b" + i;
            js.append("var ").append(key).append("=$.").append(key).append(";");
            js.append("if(").append(key).append("&&typeof ").append(key).append("==='object'){");
            js.append("for(var k in ").append(key).append("){");

            if (!addReducerFields.isEmpty()) {
                // Check if this field has an "add" reducer
                boolean firstField = true;
                for (String field : addReducerFields) {
                    if (!firstField) {
                        js.append("else ");
                    }
                    js.append("if(k==='").append(field).append("'){");
                    // Concatenate: if both are arrays, concat; if strings, concat
                    js.append("if(!m[k])m[k]=[];");
                    js.append("if(Array.isArray(").append(key).append("[k]))");
                    js.append("m[k]=m[k].concat(").append(key).append("[k]);");
                    js.append("else m[k]=m[k].concat([").append(key).append("[k]]);");
                    js.append("}");
                    firstField = false;
                }
                // Default: last-write-wins
                js.append("else{m[k]=").append(key).append("[k]}");
            } else {
                // No reducers: pure last-write-wins
                js.append("m[k]=").append(key).append("[k]");
            }

            js.append("}}");
        }

        js.append("return{state:m};}e();");
        mergeInputs.put("expression", js.toString());
        mergeTask.setInputParameters(mergeInputs);
        return mergeTask;
    }

    WorkflowTask buildDoWhile(
            String loopRef,
            String termCondition,
            List<WorkflowTask> loopTasks,
            Map<String, Object> inputParams) {
        WorkflowTask doWhile = new WorkflowTask();
        doWhile.setType("DO_WHILE");
        doWhile.setTaskReferenceName(loopRef);
        doWhile.setLoopCondition(termCondition);
        doWhile.setLoopOver(loopTasks);
        doWhile.setInputParameters(inputParams);
        return doWhile;
    }

    void addGuardrailInputs(Map<String, Object> inputs, List<String[]> guardrailRefs) {
        for (String[] gr : guardrailRefs) {
            String refName = gr[0];
            inputs.put(refName, "${" + refName + "}");
        }
    }

    /**
     * Build a post-loop InlineTask that resolves the final output. Checks workflow variables for
     * guardrail fix or human edit overrides.
     */
    WorkflowTask buildResolveOutputTask(String resolveRef, String llmRef) {
        WorkflowTask task = new WorkflowTask();
        task.setType("INLINE");
        task.setTaskReferenceName(resolveRef);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", "graaljs");
        inputs.put("expression", JavaScriptBuilder.resolveOutputScript());
        inputs.put("llm_result", ref(llmRef + ".output.result"));
        inputs.put("finish_reason", ref(llmRef + ".output.finishReason"));
        inputs.put("fixed_output", "${workflow.variables._fixed_output}");
        inputs.put("edited_output", "${workflow.variables._human_edited_output}");
        task.setInputParameters(inputs);

        return task;
    }

    /**
     * Build a post-loop INLINE task that ensures the workflow's ``result`` is non-empty even when
     * the loop terminated on a TOOL_CALLS turn.
     *
     * <p>Prefers the LLM's text result. If that is empty/null, falls back to a JSON-stringified
     * summary of the last turn's tool calls — this surfaces the {@code content} argument of
     * "writer" tools (e.g. {@code write_coder_plan(content=…)}) into the workflow output so a
     * downstream stage can read it without a contextbook re-fetch.
     */
    WorkflowTask buildSynthesizeOutputTask(String synthRef, String llmRef) {
        WorkflowTask task = new WorkflowTask();
        task.setType("INLINE");
        task.setTaskReferenceName(synthRef);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", "graaljs");
        // Self-contained inline so we don't depend on JavaScriptBuilder
        // for a one-off helper.
        inputs.put(
                "expression",
                "(function(){"
                        + "  var txt = $.llm_result;"
                        + "  if (txt !== null && txt !== undefined && String(txt).trim() !== '' && String(txt).trim() !== '[]') {"
                        + "    return txt;"
                        + "  }"
                        + "  var tcs = $.tool_calls;"
                        + "  if (Array.isArray(tcs) && tcs.length > 0) {"
                        + "    var summary = [];"
                        + "    for (var i = 0; i < tcs.length; i++) {"
                        + "      var tc = tcs[i] || {};"
                        + "      summary.push({name: tc.name, inputs: tc.inputParameters || tc.inputs || {}});"
                        + "    }"
                        + "    try { return JSON.stringify(summary); } catch (e) { return String(summary); }"
                        + "  }"
                        + "  return txt || '';"
                        + "})()");
        inputs.put("llm_result", ref(llmRef + ".output.result"));
        inputs.put("tool_calls", ref(llmRef + ".output.toolCalls"));
        task.setInputParameters(inputs);

        return task;
    }

    List<GuardrailConfig> getOutputGuardrails(AgentConfig config) {
        if (config.getGuardrails() == null) return List.of();
        return config.getGuardrails().stream()
                .filter(g -> "output".equals(g.getPosition()))
                .toList();
    }

    String buildGuardrailContinue(List<String[]> guardrailRefs) {
        // Null-guard each ref. When the LLM doesn't call the guardrailed
        // tool in this iteration (a turn that ended on plain text — STOP —
        // or finished by calling a different tool), the per-tool guardrail
        // task ref is null in the workflow context. Without the null guard,
        // ``$.X.result.should_continue`` throws ``TypeError: Cannot read
        // property 'result' of null`` and the entire DO_WHILE condition
        // crashes — which Conductor surfaces as FAILED_WITH_TERMINAL_ERROR
        // even though the LLM finished cleanly.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guardrailRefs.size(); i++) {
            if (i > 0) sb.append(" || ");
            String refName = guardrailRefs.get(i)[0];
            boolean isInline = Boolean.parseBoolean(guardrailRefs.get(i)[1]);
            if (isInline) {
                sb.append("($.")
                        .append(refName)
                        .append(" != null && $.")
                        .append(refName)
                        .append(".result != null && $.")
                        .append(refName)
                        .append(".result.should_continue == true)");
            } else {
                sb.append("($.")
                        .append(refName)
                        .append(" != null && $.")
                        .append(refName)
                        .append(".should_continue == true)");
            }
        }
        return sb.toString();
    }

    // ── Callback helpers ───────────────────────────────────────────

    /** Find a callback by position from the agent's callback list. */
    CallbackConfig findCallback(AgentConfig config, String position) {
        if (config.getCallbacks() == null) return null;
        return config.getCallbacks().stream()
                .filter(cb -> position.equals(cb.getPosition()))
                .findFirst()
                .orElse(null);
    }

    /** Build a SIMPLE worker task for a callback. */
    WorkflowTask buildCallbackTask(CallbackConfig callback, String agentName, String llmRef) {
        WorkflowTask task = new WorkflowTask();
        task.setName(callback.getTaskName());
        task.setTaskReferenceName(agentName + "_" + callback.getPosition());
        task.setType("SIMPLE");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("callback_position", callback.getPosition());
        inputs.put("agent_name", agentName);
        if (llmRef != null) {
            inputs.put("llm_result", ref(llmRef + ".output.result"));
            inputs.put("tool_calls", ref(llmRef + ".output.toolCalls"));
        }
        task.setInputParameters(inputs);
        return task;
    }

    void applyTimeout(WorkflowDef wf, AgentConfig config) {
        int timeout = config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : timeoutSeconds;
        if (timeout > 0) {
            wf.setTimeoutSeconds((long) timeout);
            wf.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        } else {
            // Explicitly clear the base workflow timeout (60s from createBaseWorkflow)
            // so that timeout_seconds=0 means "no timeout"
            wf.setTimeoutSeconds(0L);
            wf.setTimeoutPolicy(null);
        }
    }

    static String ref(String path) {
        return "${" + path + "}";
    }

    /**
     * Recursively set task names to match the Python compiler's convention: - LLM_CHAT_COMPLETE:
     * name = "llm_chat_complete" (lowercase type) - All other tasks: name = taskReferenceName
     *
     * <p>This is called after compilation to ensure consistent naming.
     */
    /**
     * Backfill missing task names in the agent's workflow tree, including any inline {@link
     * WorkflowDef}s embedded via {@code SubWorkflowParam}. Delegates the bulk of the work to {@link
     * WorkflowTaskUtils#ensureTaskName} (the shared helper used by PAC's dynamic SUB_WORKFLOW
     * emission too) and adds the SUB_WORKFLOW recursion that's specific to compile-time embedding.
     */
    static void ensureTaskNames(WorkflowTask task) {
        if (task == null) return;
        WorkflowTaskUtils.ensureTaskName(task);
        // Recurse into sub-workflow's inline workflowDef.
        // Use getWorkflowDefinition() (returns Object) and instanceof check —
        // getWorkflowDef() casts to WorkflowDef and throws if it's a runtime expression String
        // (e.g. "${parse_wf.output.result}") used for inline plan-execute sub-workflows.
        if (task.getSubWorkflowParam() != null
                && task.getSubWorkflowParam().getWorkflowDefinition() instanceof WorkflowDef wfDef
                && wfDef.getTasks() != null) {
            wfDef.getTasks().forEach(AgentCompiler::ensureTaskNames);
        }
    }

    // ── LLM node pipeline builder ────────────────────────────────────

    /**
     * Result of building an LLM node pipeline (prep → conditional LLM → finish).
     *
     * @param tasks the tasks to add to the workflow
     * @param lastTaskRef reference name of the coalesce task (last task in the pipeline)
     * @param lastStateRef expression for the output state (e.g. "coalRef.output.state")
     */
    private record LlmNodeResult(
            List<WorkflowTask> tasks, String lastTaskRef, String lastStateRef) {}

    private record SubgraphNodeResult(
            List<WorkflowTask> tasks, String lastTaskRef, String lastStateRef) {}

    /**
     * Build the task pipeline for a subgraph node: prep → SUB_WORKFLOW → finish.
     *
     * <p>The prep worker captures the subgraph.invoke() input from the parent state. The
     * SUB_WORKFLOW runs the compiled subgraph workflow with that input. The finish worker maps the
     * subgraph output back to the parent state.
     */
    @SuppressWarnings("unchecked")
    private SubgraphNodeResult buildSubgraphNodeTasks(
            AgentConfig config,
            Map<String, Object> nodeSpec,
            Object stateExpr,
            Set<String> usedRefs,
            String nodeName) {
        String prepName = (String) nodeSpec.get("_subgraph_prep_ref");
        String finishName = (String) nodeSpec.get("_subgraph_finish_ref");
        String prepRef = allocRef(usedRefs, prepName);
        String finishRef = allocRef(usedRefs, finishName);

        List<WorkflowTask> result = new ArrayList<>();

        // 1. Prep SIMPLE task — captures subgraph input
        WorkflowTask prepTask = new WorkflowTask();
        prepTask.setType("SIMPLE");
        prepTask.setName(prepName);
        prepTask.setTaskReferenceName(prepRef);
        prepTask.setInputParameters(new LinkedHashMap<>(Map.of("state", stateExpr)));
        result.add(prepTask);

        // 2. SWITCH: skip subgraph when prep sets _skip_subgraph=true
        String switchRef = allocRef(usedRefs, "_sg_skip_" + nodeName);
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setName("_sg_skip_" + nodeName);
        switchTask.setTaskReferenceName(switchRef);
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(
                new LinkedHashMap<>(
                        Map.of("switchCaseValue", "${" + prepRef + ".output._skip_subgraph}")));

        // Case "true": passthrough — use pre-computed state/result from prep
        String ptRef = allocRef(usedRefs, "_sg_pt_" + nodeName);
        WorkflowTask ptTask = new WorkflowTask();
        ptTask.setType("INLINE");
        ptTask.setName("_sg_pt_" + nodeName);
        ptTask.setTaskReferenceName(ptRef);
        Map<String, Object> ptInputs = new LinkedHashMap<>();
        ptInputs.put("evaluatorType", "graaljs");
        ptInputs.put("expression", "(function(){return{state:$.state,result:$.result};})()");
        ptInputs.put("state", "${" + prepRef + ".output.state}");
        ptInputs.put("result", "${" + prepRef + ".output.result}");
        ptTask.setInputParameters(ptInputs);

        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        cases.put("true", List.of(ptTask));
        switchTask.setDecisionCases(cases);

        // Default case: SUB_WORKFLOW → finish
        // Look up the pre-normalized subgraph AgentConfig
        Map<String, AgentConfig> subgraphConfigs =
                config.getMetadata() != null
                        ? (Map<String, AgentConfig>) config.getMetadata().get("_subgraph_configs")
                        : null;
        AgentConfig subAgent = subgraphConfigs != null ? subgraphConfigs.get(nodeName) : null;

        List<WorkflowTask> defaultTasks = new ArrayList<>();

        if (subAgent != null) {
            // Compile the subgraph into a WorkflowDef.
            WorkflowDef subWf = compile(subAgent);
            String subRef = allocRef(usedRefs, "_sg_sub_" + nodeName);

            WorkflowTask subTask = new WorkflowTask();
            subTask.setType("SUB_WORKFLOW");
            subTask.setName(subWf.getName());
            subTask.setTaskReferenceName(subRef);
            SubWorkflowParams subParams = new SubWorkflowParams();
            subParams.setName(subWf.getName());
            subParams.setWorkflowDef(subWf);
            subTask.setSubWorkflowParam(subParams);
            // Pass subgraph input from prep output + execution token
            Map<String, Object> subInputs = new LinkedHashMap<>();
            subInputs.put("state", "${" + prepRef + ".output.subgraph_input}");
            subTask.setInputParameters(subInputs);
            defaultTasks.add(subTask);

            // Finish SIMPLE task — maps subgraph output back to parent state
            WorkflowTask finishTask = new WorkflowTask();
            finishTask.setType("SIMPLE");
            finishTask.setName(finishName);
            finishTask.setTaskReferenceName(finishRef);
            Map<String, Object> finishInputs = new LinkedHashMap<>();
            finishInputs.put("state", "${" + prepRef + ".output.state}");
            finishInputs.put("subgraph_result", "${" + subRef + ".output.state}");
            finishTask.setInputParameters(finishInputs);
            defaultTasks.add(finishTask);
        }

        switchTask.setDefaultCase(defaultTasks);
        result.add(switchTask);

        // 3. Coalesce INLINE — unify output from either SWITCH branch
        String coalRef = allocRef(usedRefs, "_sg_out_" + nodeName);
        WorkflowTask coalTask = new WorkflowTask();
        coalTask.setType("INLINE");
        coalTask.setName("_sg_out_" + nodeName);
        coalTask.setTaskReferenceName(coalRef);
        Map<String, Object> coalInputs = new LinkedHashMap<>();
        coalInputs.put("evaluatorType", "graaljs");
        coalInputs.put(
                "expression",
                "(function(){"
                        + "if($.skip===true||$.skip==='true'){return{state:$.pt_state,result:$.pt_result};}"
                        + "return{state:$.fin_state,result:$.fin_result};"
                        + "})()");
        coalInputs.put("skip", "${" + prepRef + ".output._skip_subgraph}");
        coalInputs.put("pt_state", "${" + prepRef + ".output.state}");
        coalInputs.put("pt_result", "${" + prepRef + ".output.result}");
        coalInputs.put("fin_state", "${" + finishRef + ".output.state}");
        coalInputs.put("fin_result", "${" + finishRef + ".output.result}");
        coalTask.setInputParameters(coalInputs);
        result.add(coalTask);

        return new SubgraphNodeResult(result, coalRef, coalRef + ".output.result.state");
    }

    /**
     * Build the task pipeline for an LLM-intercepted graph node.
     *
     * <p>Generates: prep → SWITCH(_skip_llm) → [passthrough | LLM → finish]. When the prep worker
     * detects that the node function completed without calling llm.invoke() (conditional early
     * return), it sets {@code _skip_llm=true} and includes the pre-computed result. The SWITCH
     * skips the LLM_CHAT_COMPLETE task in that case.
     */
    private LlmNodeResult buildLlmNodeTasks(
            AgentConfig config,
            Map<String, Object> nodeSpec,
            Object stateExpr,
            Set<String> usedRefs,
            String workerRef,
            String nodeName,
            Map<String, Object> retryPolicy) {
        String prepRef = allocRef(usedRefs, (String) nodeSpec.get("_llm_prep_ref"));
        String llmRef = allocRef(usedRefs, workerRef + "_llm");
        String finishRef = allocRef(usedRefs, (String) nodeSpec.get("_llm_finish_ref"));
        ParsedModel parsed = ModelParser.parse(config.getModel());

        List<WorkflowTask> result = new ArrayList<>();

        // 1. Prep SIMPLE task
        WorkflowTask prepTask = new WorkflowTask();
        prepTask.setType("SIMPLE");
        prepTask.setName((String) nodeSpec.get("_llm_prep_ref"));
        prepTask.setTaskReferenceName(prepRef);
        prepTask.setInputParameters(new LinkedHashMap<>(Map.of("state", stateExpr)));
        if (retryPolicy != null) applyRetryPolicy(prepTask, retryPolicy);
        result.add(prepTask);

        // 2. SWITCH: skip LLM when prep sets _skip_llm=true
        String switchRef = allocRef(usedRefs, "_llm_skip_" + nodeName);
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setName("_llm_skip_" + nodeName);
        switchTask.setTaskReferenceName(switchRef);
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(
                new LinkedHashMap<>(
                        Map.of("switchCaseValue", "${" + prepRef + ".output._skip_llm}")));

        // Case "true": passthrough — use pre-computed state/result from prep
        String ptRef = allocRef(usedRefs, "_llm_pt_" + nodeName);
        WorkflowTask ptTask = new WorkflowTask();
        ptTask.setType("INLINE");
        ptTask.setName("_llm_pt_" + nodeName);
        ptTask.setTaskReferenceName(ptRef);
        Map<String, Object> ptInputs = new LinkedHashMap<>();
        ptInputs.put("evaluatorType", "graaljs");
        ptInputs.put("expression", "(function(){return{state:$.state,result:$.result};})()");
        ptInputs.put("state", "${" + prepRef + ".output.state}");
        ptInputs.put("result", "${" + prepRef + ".output.result}");
        ptTask.setInputParameters(ptInputs);

        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        cases.put("true", List.of(ptTask));
        switchTask.setDecisionCases(cases);

        // Default case: LLM_CHAT_COMPLETE → finish
        WorkflowTask llmTask = new WorkflowTask();
        llmTask.setType("LLM_CHAT_COMPLETE");
        llmTask.setName("llm_chat_complete");
        llmTask.setTaskReferenceName(llmRef);
        Map<String, Object> llmInputs = new LinkedHashMap<>();
        llmInputs.put("llmProvider", parsed.getProvider());
        llmInputs.put("model", parsed.getModel());
        llmInputs.put("maxTokens", config.getMaxTokens() != null ? config.getMaxTokens() : 16384);
        llmInputs.put("messages", "${" + prepRef + ".output.messages}");
        llmTask.setInputParameters(llmInputs);

        WorkflowTask finishTask = new WorkflowTask();
        finishTask.setType("SIMPLE");
        finishTask.setName((String) nodeSpec.get("_llm_finish_ref"));
        finishTask.setTaskReferenceName(finishRef);
        Map<String, Object> finishInputs = new LinkedHashMap<>();
        finishInputs.put("state", "${" + prepRef + ".output.state}");
        finishInputs.put("llm_result", "${" + llmRef + ".output.result}");
        finishTask.setInputParameters(finishInputs);

        switchTask.setDefaultCase(List.of(llmTask, finishTask));
        result.add(switchTask);

        // 3. Coalesce INLINE — unify output from either SWITCH branch.
        // Use _skip_llm flag from prep (reliable) instead of null-checking
        // non-executed branch outputs (Conductor may resolve them to empty objects).
        String coalRef = allocRef(usedRefs, "_llm_out_" + nodeName);
        WorkflowTask coalTask = new WorkflowTask();
        coalTask.setType("INLINE");
        coalTask.setName("_llm_out_" + nodeName);
        coalTask.setTaskReferenceName(coalRef);
        Map<String, Object> coalInputs = new LinkedHashMap<>();
        coalInputs.put("evaluatorType", "graaljs");
        coalInputs.put(
                "expression",
                "(function(){"
                        + "if($.skip===true||$.skip==='true'){return{state:$.pt_state,result:$.pt_result};}"
                        + "return{state:$.fin_state,result:$.fin_result};"
                        + "})()");
        coalInputs.put("skip", "${" + prepRef + ".output._skip_llm}");
        // Both prep and finish are SIMPLE tasks → .output.{field} directly
        coalInputs.put("pt_state", "${" + prepRef + ".output.state}");
        coalInputs.put("pt_result", "${" + prepRef + ".output.result}");
        coalInputs.put("fin_state", "${" + finishRef + ".output.state}");
        coalInputs.put("fin_result", "${" + finishRef + ".output.result}");
        coalTask.setInputParameters(coalInputs);
        result.add(coalTask);

        return new LlmNodeResult(result, coalRef, coalRef + ".output.result.state");
    }

    /**
     * Allocate a unique taskReferenceName. If {@code desired} is not yet in {@code usedRefs} it is
     * returned as-is; otherwise {@code _2}, {@code _3}, etc. are appended until a unique name is
     * found. The result is added to {@code usedRefs} before returning.
     */
    private static String allocRef(Set<String> usedRefs, String desired) {
        if (usedRefs.add(desired)) return desired;
        int n = 2;
        while (!usedRefs.add(desired + "_" + n)) n++;
        return desired + "_" + n;
    }

    /**
     * Ensure every taskReferenceName in the workflow is unique.
     *
     * <p>Walks the entire task tree (including forks, loops, switch cases, and inline
     * sub-workflows). When a duplicate is found, it is renamed by appending {@code _2}, {@code _3},
     * etc. All {@code ${oldRef...}} expressions in the workflow's input/output parameters are
     * updated to use the new name.
     *
     * <p>This is a safety net that runs for ALL compilation paths. Individual compilers (e.g.
     * {@code compileGraphStructure}) may also use {@link #allocRef} to prevent duplicates at
     * construction time.
     */
    static void ensureUniqueRefNames(List<WorkflowTask> tasks, WorkflowDef wf) {
        // Pass 1: collect all tasks and detect duplicates
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> renames = new LinkedHashMap<>(); // oldRef -> newRef
        deduplicateRefs(tasks, seen, renames);

        // Pass 2: if any renames occurred, update ${oldRef...} references everywhere
        if (!renames.isEmpty()) {
            log.info("Renaming {} duplicate taskReferenceName(s): {}", renames.size(), renames);
            updateRefExpressions(tasks, renames);
            // Also update workflow-level outputParameters
            if (wf.getOutputParameters() != null) {
                Map<String, Object> updated = replaceRefsInMap(wf.getOutputParameters(), renames);
                wf.setOutputParameters(updated);
            }
        }
    }

    /** Walk the task tree, renaming duplicate refs and recording the renames. */
    private static void deduplicateRefs(
            List<WorkflowTask> tasks, Set<String> seen, Map<String, String> renames) {
        if (tasks == null) return;
        for (WorkflowTask task : tasks) {
            if (task == null) continue;

            String ref = task.getTaskReferenceName();
            if (ref != null && !ref.isEmpty()) {
                if (!seen.add(ref)) {
                    // Duplicate — allocate a unique name
                    String newRef = ref;
                    int n = 2;
                    while (!seen.add(newRef + "_" + n)) n++;
                    newRef = ref + "_" + n;
                    log.warn("Duplicate taskReferenceName '{}' renamed to '{}'", ref, newRef);
                    task.setTaskReferenceName(newRef);
                    renames.put(ref, newRef);
                }
            }

            // Recurse into nested structures
            deduplicateRefs(task.getLoopOver(), seen, renames);
            if (task.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : task.getDecisionCases().values()) {
                    deduplicateRefs(branch, seen, renames);
                }
            }
            deduplicateRefs(task.getDefaultCase(), seen, renames);
            if (task.getForkTasks() != null) {
                for (List<WorkflowTask> branch : task.getForkTasks()) {
                    deduplicateRefs(branch, seen, renames);
                }
            }
            // Skip sub-workflows whose workflowDefinition is a runtime expression String
            // (e.g. "${parse_wf.output.result}") used by plan-execute inline sub-workflows.
            if (task.getSubWorkflowParam() != null
                    && task.getSubWorkflowParam().getWorkflowDefinition()
                            instanceof WorkflowDef nestedWfDef
                    && nestedWfDef.getTasks() != null) {
                // Sub-workflows have their own ref namespace
                ensureUniqueRefNames(nestedWfDef.getTasks(), nestedWfDef);
            }
        }
    }

    /**
     * Walk the task tree and update all {@code ${oldRef...}} expressions in inputParameters to use
     * the new ref names.
     */
    private static void updateRefExpressions(
            List<WorkflowTask> tasks, Map<String, String> renames) {
        if (tasks == null) return;
        for (WorkflowTask task : tasks) {
            if (task == null) continue;
            if (task.getInputParameters() != null) {
                Map<String, Object> updated = replaceRefsInMap(task.getInputParameters(), renames);
                task.setInputParameters(updated);
            }
            // Also update expression field (used by SWITCH evaluators, INLINE expressions, etc.)
            if (task.getExpression() instanceof String expr) {
                String replaced = replaceRefsInString(expr, renames);
                if (!replaced.equals(expr)) {
                    task.setExpression(replaced);
                }
            }
            // Recurse
            updateRefExpressions(task.getLoopOver(), renames);
            if (task.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : task.getDecisionCases().values()) {
                    updateRefExpressions(branch, renames);
                }
            }
            updateRefExpressions(task.getDefaultCase(), renames);
            if (task.getForkTasks() != null) {
                for (List<WorkflowTask> branch : task.getForkTasks()) {
                    updateRefExpressions(branch, renames);
                }
            }
        }
    }

    /**
     * Replace {@code ${oldRef.xxx}} → {@code ${newRef.xxx}} in all string values of a parameter
     * map. Recurses into nested maps and lists.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> replaceRefsInMap(
            Map<String, Object> params, Map<String, String> renames) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result.put(entry.getKey(), replaceRefsInValue(entry.getValue(), renames));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object replaceRefsInValue(Object value, Map<String, String> renames) {
        if (value instanceof String s) {
            return replaceRefsInString(s, renames);
        } else if (value instanceof Map) {
            return replaceRefsInMap((Map<String, Object>) value, renames);
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(replaceRefsInValue(item, renames));
            }
            return result;
        }
        return value;
    }

    /**
     * In a string, replace all occurrences of {@code ${oldRef.} and {@code ${oldRef}}
     * with the new ref name.
     */
    private static String replaceRefsInString(String s, Map<String, String> renames) {
        if (s == null || !s.contains("${")) return s;
        String result = s;
        for (Map.Entry<String, String> entry : renames.entrySet()) {
            String oldRef = entry.getKey();
            String newRef = entry.getValue();
            // Replace ${oldRef.xxx} → ${newRef.xxx}  and  ${oldRef} → ${newRef}
            result = result.replace("${" + oldRef + ".", "${" + newRef + ".");
            result = result.replace("${" + oldRef + "}", "${" + newRef + "}");
        }
        return result;
    }

    /** Build code execution instruction text matching the Python compiler output. */
    private String buildCodeExecInstructions(AgentConfig config) {
        List<String> languages = config.getCodeExecution().getAllowedLanguages();
        String langs =
                (languages != null && !languages.isEmpty())
                        ? String.join(", ", languages)
                        : "python, javascript, bash";
        String msg =
                "You have code execution capabilities. Use the execute_code tool to write and run code. Supported languages: "
                        + langs
                        + "."
                        + " Each execution runs in an isolated environment — no state, variables, or imports persist between calls."
                        + " Always include all necessary imports at the top of every code block (e.g. import subprocess, import os, import json).";
        if (config.getCodeExecution().getAllowedCommands() != null
                && !config.getCodeExecution().getAllowedCommands().isEmpty()) {
            String cmds = String.join(", ", config.getCodeExecution().getAllowedCommands());
            msg += " Allowed shell commands: " + cmds + ". Do not use other commands.";
        }
        return msg;
    }

    /** Build CLI command execution instruction text for the system prompt. */
    private String buildCliInstructions(AgentConfig config) {
        String msg =
                "You have CLI command execution capabilities. "
                        + "Use the run_command tool to execute shell commands directly. "
                        + "By default commands run without a shell interpreter (safer). "
                        + "Set shell=True only when you need pipes, redirects, or glob expansion.";
        if (config.getCliConfig().getAllowedCommands() != null
                && !config.getCliConfig().getAllowedCommands().isEmpty()) {
            String cmds = String.join(", ", config.getCliConfig().getAllowedCommands());
            msg += " Allowed commands: " + cmds + ". Do not use other commands.";
        }
        if (!config.getCliConfig().isAllowShell()) {
            msg += " Shell mode is disabled — do not set shell=True.";
        }
        return msg;
    }

    /**
     * Format a Java object as Python dict repr: {'key': 'value', ...} This matches Python's str()
     * on a dict for system prompt embedding.
     */
    /**
     * Convert an inlined JSON Schema map to a compact Python-style type string.
     *
     * <p>JSON Schema's structural keywords ({@code type}, {@code items}, {@code properties}) are
     * translated into idiomatic Python type notation:
     *
     * <ul>
     *   <li>{@code {"type":"string"}} → {@code str}
     *   <li>{@code {"type":"integer"}} → {@code int}
     *   <li>{@code {"type":"number"}} → {@code float}
     *   <li>{@code {"type":"boolean"}} → {@code bool}
     *   <li>{@code {"type":"array","items":{...}}} → {@code [item_type]}
     *   <li>{@code {"type":"object","properties":{...}}} → {@code {key: type, ...}}
     * </ul>
     *
     * This avoids passing raw JSON Schema keywords like {@code items} and {@code title} to the LLM,
     * which would otherwise interpret them as data field names.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String simplifySchema(Map<String, Object> schema) {
        return simplifyNode(schema);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String simplifyNode(Object node) {
        if (!(node instanceof Map)) {
            return String.valueOf(node);
        }
        Map<String, Object> m = (Map<String, Object>) node;
        String type = m.containsKey("type") ? String.valueOf(m.get("type")) : null;

        if ("array".equals(type)) {
            Object items = m.get("items");
            if (items instanceof Map) {
                return "[" + simplifyNode(items) + "]";
            }
            return "list";
        }

        if ("object".equals(type) || m.containsKey("properties")) {
            Object propsObj = m.get("properties");
            if (propsObj instanceof Map) {
                Map<String, Object> props = (Map<String, Object>) propsObj;
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append("'")
                            .append(entry.getKey())
                            .append("': ")
                            .append(simplifyNode(entry.getValue()));
                }
                sb.append("}");
                return sb.toString();
            }
            return "object";
        }

        if ("string".equals(type)) return "str";
        if ("integer".equals(type)) return "int";
        if ("number".equals(type)) return "float";
        if ("boolean".equals(type)) return "bool";
        if ("null".equals(type)) return "None";

        // anyOf: Pydantic uses this for Optional[T] → [T, null] → render as "T | None"
        if (m.containsKey("anyOf")) {
            List<Object> variants = (List<Object>) m.get("anyOf");
            List<String> parts = new ArrayList<>();
            for (Object v : variants) {
                String s = simplifyNode(v);
                if (!"None".equals(s)) parts.add(s); // put None last
            }
            parts.add("None");
            // deduplicate
            LinkedHashSet<String> unique = new LinkedHashSet<>(parts);
            return String.join(" | ", unique);
        }

        // enum: render as a list of allowed values
        if (m.containsKey("enum")) {
            return String.valueOf(m.get("enum"));
        }

        // Fallback: render as dict
        if (m.containsKey("properties")) return simplifyNode(m);
        return type != null ? type : "any";
    }

    /**
     * Recursively inline all {@code $ref} references in a JSON Schema map.
     *
     * <p>Pydantic's {@code model_json_schema()} produces schemas with a top-level {@code $defs}
     * section and {@code $ref: "#/$defs/Foo"} pointers inside {@code properties}. This method
     * resolves every {@code $ref} by substituting the referenced definition in-place, so the
     * resulting map contains no unresolved references and can be understood by an LLM without
     * needing JSON-Schema-aware tooling.
     *
     * @param schema the raw JSON Schema map (may contain {@code $defs} and {@code $ref})
     * @return a new map with all references fully inlined
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<String, Object> inlineRefs(Map<String, Object> schema) {
        Map<String, Object> defs =
                schema.containsKey("$defs")
                        ? (Map<String, Object>) schema.get("$defs")
                        : Collections.emptyMap();
        return (Map<String, Object>) resolveNode(schema, defs);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolveNode(Object node, Map<String, Object> defs) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            // Resolve $ref first
            if (m.containsKey("$ref")) {
                String ref = (String) m.get("$ref");
                // Format: "#/$defs/TypeName"
                if (ref != null && ref.startsWith("#/$defs/")) {
                    String typeName = ref.substring("#/$defs/".length());
                    Object definition = defs.get(typeName);
                    if (definition instanceof Map<?, ?>) {
                        // Recursively resolve the referenced definition
                        return resolveNode(definition, defs);
                    }
                }
                return m; // unresolvable ref — pass through
            }
            // Recurse into all values, skip $defs (not part of the instance schema)
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                String key = (String) entry.getKey();
                if ("$defs".equals(key)) continue; // drop the definitions section
                result.put(key, resolveNode(entry.getValue(), defs));
            }
            return result;
        }
        if (node instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(resolveNode(item, defs));
            }
            return result;
        }
        return node; // primitive — pass through as-is
    }

    static String pythonDictRepr(Object obj) {
        if (obj == null) return "None";
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append("'")
                        .append(entry.getKey())
                        .append("': ")
                        .append(pythonDictRepr(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(pythonDictRepr(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof String) {
            return "'" + obj + "'";
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? "True" : "False";
        }
        if (obj instanceof Number) {
            return obj.toString();
        }
        return "'" + obj + "'";
    }

    /** Recursively walk the config tree and collect capability tags. */
    static Set<String> collectCapabilities(AgentConfig config) {
        Set<String> caps = new LinkedHashSet<>();
        // Mirror the dispatch-site definition of ``hasAgents`` — named
        // PLAN_EXECUTE slots count as sub-agents for capability purposes
        // too. Without this, a PLAN_EXECUTE coordinator built with
        // ``planner=`` got tagged ``simple`` in workflow metadata and its
        // planner/fallback children were invisible to the recursion.
        boolean hasAgents =
                (config.getAgents() != null && !config.getAgents().isEmpty())
                        || config.getPlanner() != null
                        || config.getFallback() != null;
        boolean hasTools = config.getTools() != null && !config.getTools().isEmpty();

        if (hasAgents && hasTools) {
            caps.add("tool-calling");
            caps.add("multi-agent-hybrid");
        } else if (hasAgents) {
            AgentConfig.Strategy strategy =
                    config.getStrategy() != null
                            ? config.getStrategy()
                            : AgentConfig.Strategy.HANDOFF;
            caps.add("multi-agent-" + strategy.toValue().replace("_", "-"));
        } else if (hasTools) {
            caps.add("tool-calling");
        } else {
            caps.add("simple");
        }

        // Recurse into every sub-agent reachable from this config —
        // legacy ``agents=[…]`` AND named ``planner``/``fallback`` slots.
        if (config.getAgents() != null) {
            for (AgentConfig sub : config.getAgents()) {
                caps.addAll(collectCapabilities(sub));
            }
        }
        if (config.getPlanner() != null) {
            caps.addAll(collectCapabilities(config.getPlanner()));
        }
        if (config.getFallback() != null) {
            caps.addAll(collectCapabilities(config.getFallback()));
        }
        return caps;
    }

    // Setters for configuration
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setLlmRetryCount(int llmRetryCount) {
        this.llmRetryCount = llmRetryCount;
    }

    public void setContextMaxSizeBytes(int contextMaxSizeBytes) {
        this.contextMaxSizeBytes = contextMaxSizeBytes;
    }

    public void setContextMaxValueSizeBytes(int contextMaxValueSizeBytes) {
        this.contextMaxValueSizeBytes = contextMaxValueSizeBytes;
    }

    int getContextMaxSizeBytes() {
        return contextMaxSizeBytes;
    }

    int getContextMaxValueSizeBytes() {
        return contextMaxValueSizeBytes;
    }

    private boolean isGraphStructure(AgentConfig config) {
        return config.getMetadata() != null
                && config.getMetadata().get("_graph_structure") instanceof Map;
    }

    @SuppressWarnings("unchecked")
    private WorkflowDef compileGraphStructure(AgentConfig config) {
        log.debug("Compiling graph-structure workflow: {}", config.getName());

        Map<String, Object> graph =
                (Map<String, Object>) config.getMetadata().get("_graph_structure");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        List<Map<String, Object>> conditionalEdges =
                (List<Map<String, Object>>) graph.getOrDefault("conditional_edges", List.of());
        boolean inputIsMessages = Boolean.TRUE.equals(graph.get("_input_is_messages"));
        String inputKey =
                (String) graph.getOrDefault("input_key", inputIsMessages ? "messages" : "request");

        // Extract reducer metadata: field_name -> reducer_type ("add", "extend", etc.)
        // Used in FORK_JOIN merge to correctly combine parallel branch state
        @SuppressWarnings("unchecked")
        Map<String, String> reducers = new LinkedHashMap<>();
        Object rawReducers = config.getMetadata().get("_reducers");
        if (rawReducers instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawReducers).entrySet()) {
                reducers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        // Extract retry policies: node_name -> {max_attempts, initial_interval, backoff_factor,
        // ...}
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> retryPolicies = new LinkedHashMap<>();
        Object rawRetry = config.getMetadata().get("_retry_policies");
        if (rawRetry instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawRetry).entrySet()) {
                if (entry.getValue() instanceof Map) {
                    retryPolicies.put(
                            String.valueOf(entry.getKey()), (Map<String, Object>) entry.getValue());
                }
            }
        }

        // Extract recursion limit for DO_WHILE iteration cap
        int recursionLimit = 25; // default
        Object rawLimit = config.getMetadata().get("_recursion_limit");
        if (rawLimit instanceof Number) {
            recursionLimit = ((Number) rawLimit).intValue();
        }

        // Build lookups: node_name -> worker_ref, node_name -> full node spec
        Map<String, String> nodeWorkerRef = new LinkedHashMap<>();
        Map<String, Map<String, Object>> nodeSpecs = new LinkedHashMap<>();
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                String nodeName = (String) node.get("name");
                String workerRef = (String) node.get("_worker_ref");
                if (nodeName != null && workerRef != null) {
                    nodeWorkerRef.put(nodeName, workerRef);
                    nodeSpecs.put(nodeName, node);
                }
            }
        }

        // Build adjacency: source -> list of targets (for simple edges)
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String src = (String) edge.get("source");
                String tgt = (String) edge.get("target");
                if (src != null && tgt != null) {
                    adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
                }
            }
        }

        // Build conditional edges: source -> {_router_ref, targets}
        // If multiple conditional edges share the same source, merge their targets
        // and log a warning (the last router_ref wins).
        Map<String, Map<String, Object>> conditionalMap = new LinkedHashMap<>();
        for (Map<String, Object> ce : conditionalEdges) {
            String src = (String) ce.get("source");
            if (src != null) {
                if (conditionalMap.containsKey(src)) {
                    log.warn(
                            "Multiple conditional edges from '{}' — merging targets. "
                                    + "Last router function wins.",
                            src);
                    Map<String, Object> existing = conditionalMap.get(src);
                    // Merge targets from both edges
                    @SuppressWarnings("unchecked")
                    Map<String, String> existingTargets =
                            (Map<String, String>) existing.get("targets");
                    @SuppressWarnings("unchecked")
                    Map<String, String> newTargets = (Map<String, String>) ce.get("targets");
                    if (existingTargets != null && newTargets != null) {
                        Map<String, String> merged = new LinkedHashMap<>(existingTargets);
                        merged.putAll(newTargets);
                        Map<String, Object> mergedCe = new LinkedHashMap<>(ce);
                        mergedCe.put("targets", merged);
                        conditionalMap.put(src, mergedCe);
                    } else {
                        conditionalMap.put(src, ce);
                    }
                } else {
                    conditionalMap.put(src, ce);
                }
            }
        }

        // Topological walk from __start__ to build the task list
        List<WorkflowTask> tasks = new ArrayList<>();

        // Track all allocated taskReferenceNames to guarantee uniqueness.
        // allocRef() checks this set and appends _2, _3, etc. if needed.
        Set<String> usedRefs = new LinkedHashSet<>();

        // Track where each node's tasks start in the tasks list (for cycle extraction)
        Map<String, Integer> nodeTaskStartIndex = new LinkedHashMap<>();
        // Track what lastStateRef was when each node started (for state-bridge pre-loop state)
        Map<String, String> nodeEntryStateRef = new LinkedHashMap<>();

        // Subgraph mode: input is a full state dict, not a single prompt string
        boolean isSubgraphWorkflow = Boolean.TRUE.equals(graph.get("_is_subgraph"));

        // Build initial state map
        // For subgraph workflows, the state is passed directly via workflow.input.state
        // For regular workflows, the state starts as {inputKey: prompt}
        // For messages-based graphs, wrap the prompt as a message object list
        Object initialState;
        if (isSubgraphWorkflow) {
            initialState = "${workflow.input.state}";
        } else if (inputIsMessages) {
            // Messages-based state: wrap prompt as [{"role": "user", "content": prompt}]
            Map<String, Object> stateMap = new LinkedHashMap<>();
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", "${workflow.input.prompt}");
            stateMap.put(inputKey, List.of(userMessage));
            initialState = stateMap;
        } else {
            Map<String, Object> stateMap = new LinkedHashMap<>();
            stateMap.put(inputKey, "${workflow.input.prompt}");
            initialState = stateMap;
        }

        // Walk the graph from __start__
        // The first node receives the initial state directly (no init task needed).
        // lastStateExpr holds the Conductor expression for the current accumulated state.
        boolean isFirstNode = true;
        Set<String> visited = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();

        // Track the last task ref for output extraction and state threading
        String lastTaskRef = null;
        String lastStateRef = null;

        // Find what __start__ connects to
        List<String> startTargets = adjacency.getOrDefault("__start__", List.of());
        if (startTargets.size() > 1) {
            // Fan-out from START: compile as FORK_JOIN
            String joinPoint = findJoinPoint(startTargets, adjacency);
            log.debug(
                    "Fan-out from START to {} branches, join at '{}'",
                    startTargets.size(),
                    joinPoint);

            List<List<WorkflowTask>> forkBranches = new ArrayList<>();
            List<String> joinOnRefs = new ArrayList<>();
            List<String> branchStateExprs = new ArrayList<>();

            for (String branchTarget : startTargets) {
                List<WorkflowTask> branchTasks = new ArrayList<>();
                String branchNode = branchTarget;
                String branchLastStateRef = null;

                while (branchNode != null
                        && !branchNode.equals(joinPoint)
                        && !"__end__".equals(branchNode)
                        && !visited.contains(branchNode)) {
                    visited.add(branchNode);
                    String wRef = nodeWorkerRef.get(branchNode);
                    if (wRef == null) break;

                    Object stateInput =
                            branchLastStateRef != null
                                    ? "${" + branchLastStateRef + "}"
                                    : initialState;
                    Map<String, Object> bNodeSpec = nodeSpecs.get(branchNode);
                    boolean bIsLlm =
                            bNodeSpec != null && Boolean.TRUE.equals(bNodeSpec.get("_llm_node"));

                    if (bIsLlm) {
                        LlmNodeResult llmRes =
                                buildLlmNodeTasks(
                                        config,
                                        bNodeSpec,
                                        stateInput,
                                        usedRefs,
                                        wRef,
                                        branchNode,
                                        null);
                        branchTasks.addAll(llmRes.tasks());
                        branchLastStateRef = llmRes.lastStateRef();
                    } else {
                        String nodeRef = allocRef(usedRefs, wRef);
                        WorkflowTask nodeTask = new WorkflowTask();
                        nodeTask.setType("SIMPLE");
                        nodeTask.setName(wRef);
                        nodeTask.setTaskReferenceName(nodeRef);
                        nodeTask.setInputParameters(
                                new LinkedHashMap<>(Map.of("state", stateInput)));
                        branchTasks.add(nodeTask);
                        branchLastStateRef = nodeRef + ".output.state";
                    }

                    // Move to next node in this branch (stop at join point)
                    List<String> nexts = adjacency.getOrDefault(branchNode, List.of());
                    branchNode =
                            (nexts.size() == 1
                                            && !nexts.get(0).equals(joinPoint)
                                            && !"__end__".equals(nexts.get(0)))
                                    ? nexts.get(0)
                                    : null;
                }

                if (!branchTasks.isEmpty()) {
                    forkBranches.add(branchTasks);
                    joinOnRefs.add(branchTasks.get(branchTasks.size() - 1).getTaskReferenceName());
                    branchStateExprs.add(branchLastStateRef);
                }
            }

            // FORK_JOIN task
            String forkRef = allocRef(usedRefs, "_fork");
            WorkflowTask forkTask = new WorkflowTask();
            forkTask.setType("FORK_JOIN");
            forkTask.setTaskReferenceName(forkRef);
            forkTask.setForkTasks(forkBranches);
            tasks.add(forkTask);

            // JOIN task
            String joinRef = allocRef(usedRefs, "_join");
            WorkflowTask joinTask = new WorkflowTask();
            joinTask.setType("JOIN");
            joinTask.setTaskReferenceName(joinRef);
            joinTask.setJoinOn(joinOnRefs);
            tasks.add(joinTask);

            // State-merge INLINE: combine all branch states (reducer-aware)
            String mergeRef = allocRef(usedRefs, "_fork_merge");
            tasks.add(buildForkMergeTask(mergeRef, joinOnRefs, branchStateExprs, reducers));

            isFirstNode = false;
            lastTaskRef = mergeRef;
            lastStateRef = mergeRef + ".output.result.state";

            // Queue join point for further processing
            if (joinPoint != null) {
                queue.add(joinPoint);
            }
        } else if (!startTargets.isEmpty()) {
            queue.addAll(startTargets);
        }

        while (!queue.isEmpty()) {
            String nodeName = queue.remove(0);
            if (visited.contains(nodeName) || "__end__".equals(nodeName)) continue;
            visited.add(nodeName);

            // Record where this node's tasks start and what state it receives
            nodeTaskStartIndex.put(nodeName, tasks.size());
            nodeEntryStateRef.put(nodeName, lastStateRef);

            String workerRef = nodeWorkerRef.get(nodeName);
            if (workerRef == null) continue;

            // Determine the state expression for this node
            String stateExpr;
            if (isFirstNode) {
                isFirstNode = false;
                // First node receives initial state directly from workflow input
                // We need to serialize initialState as a map expression
                stateExpr = null; // sentinel: use initialState map directly
            } else {
                stateExpr = "${" + lastStateRef + "}";
            }

            // Check node type: LLM, HUMAN, subgraph, or regular
            Map<String, Object> nodeSpec = nodeSpecs.get(nodeName);
            boolean isLlmNode = nodeSpec != null && Boolean.TRUE.equals(nodeSpec.get("_llm_node"));
            boolean isHumanNode =
                    nodeSpec != null && Boolean.TRUE.equals(nodeSpec.get("_human_node"));
            boolean isSubgraphNode =
                    nodeSpec != null && Boolean.TRUE.equals(nodeSpec.get("_subgraph_node"));

            if (isHumanNode) {
                // Human node: HUMAN → validate → normalize → process (state merge)
                String humanRef = allocRef(usedRefs, "human_" + nodeName);
                String humanPrompt =
                        nodeSpec.get("_human_prompt") instanceof String
                                ? (String) nodeSpec.get("_human_prompt")
                                : "";
                Object currentState = stateExpr != null ? stateExpr : initialState;

                HumanTaskBuilder.Pipeline humanPipeline =
                        HumanTaskBuilder.create(humanRef, config.getName() + " — " + nodeName)
                                .contextInput("state", currentState)
                                .contextInput(
                                        "_prompt", !humanPrompt.isEmpty() ? humanPrompt : null)
                                .graphNodeValidation(config.getModel(), humanPrompt, currentState)
                                .build();
                // Register all pipeline refs as used
                for (WorkflowTask pt : humanPipeline.getTasks()) {
                    usedRefs.add(pt.getTaskReferenceName());
                }
                tasks.addAll(humanPipeline.getTasks());

                // The process task output has {state, result} — use it as next state
                String processRef = humanRef + "_process";
                lastTaskRef = processRef;
                lastStateRef = processRef + ".output.result.state";

                log.debug(
                        "Graph node '{}' compiled as HUMAN node with validation: {}",
                        nodeName,
                        humanRef);
            } else if (isLlmNode) {
                Object llmStateExpr = stateExpr != null ? stateExpr : initialState;
                LlmNodeResult llmResult =
                        buildLlmNodeTasks(
                                config,
                                nodeSpec,
                                llmStateExpr,
                                usedRefs,
                                workerRef,
                                nodeName,
                                retryPolicies.get(nodeName));
                tasks.addAll(llmResult.tasks());
                lastTaskRef = llmResult.lastTaskRef();
                lastStateRef = llmResult.lastStateRef();

                log.debug("Graph node '{}' compiled as LLM node (with skip-llm SWITCH)", nodeName);
            } else if (isSubgraphNode) {
                // Subgraph node: prep → SUB_WORKFLOW → finish
                Object sgStateExpr = stateExpr != null ? stateExpr : initialState;
                SubgraphNodeResult sgResult =
                        buildSubgraphNodeTasks(config, nodeSpec, sgStateExpr, usedRefs, nodeName);
                tasks.addAll(sgResult.tasks());
                lastTaskRef = sgResult.lastTaskRef();
                lastStateRef = sgResult.lastStateRef();

                log.debug("Graph node '{}' compiled as subgraph node (SUB_WORKFLOW)", nodeName);
            } else {
                // Regular non-LLM node: single SIMPLE task
                String nodeRef = allocRef(usedRefs, workerRef);
                WorkflowTask nodeTask = new WorkflowTask();
                nodeTask.setType("SIMPLE");
                nodeTask.setName(workerRef);
                nodeTask.setTaskReferenceName(nodeRef);
                Map<String, Object> nodeInputs = new LinkedHashMap<>();
                nodeInputs.put("state", stateExpr != null ? stateExpr : initialState);
                nodeTask.setInputParameters(nodeInputs);
                applyRetryPolicy(nodeTask, retryPolicies.get(nodeName));
                tasks.add(nodeTask);
                lastTaskRef = nodeRef;
                lastStateRef = nodeRef + ".output.state";
            }

            // Check for conditional edge from this node
            if (conditionalMap.containsKey(nodeName)) {
                Map<String, Object> ce = conditionalMap.get(nodeName);
                String routerRef = (String) ce.get("_router_ref");
                Map<String, String> targets = (Map<String, String>) ce.get("targets");
                boolean isDynamicFanout = Boolean.TRUE.equals(ce.get("_dynamic_fanout"));

                if (isDynamicFanout && routerRef != null && targets != null && !targets.isEmpty()) {
                    // === DYNAMIC FAN-OUT: Send API → FORK_JOIN_DYNAMIC ===
                    log.debug("Dynamic fan-out at '{}': compiling as FORK_JOIN_DYNAMIC", nodeName);

                    // 1. Router SIMPLE task: calls the router function, returns {dynamic_tasks:
                    // [...], state: {...}}
                    String allocRouterRef = allocRef(usedRefs, routerRef);
                    WorkflowTask routerTask = new WorkflowTask();
                    routerTask.setType("SIMPLE");
                    routerTask.setName(routerRef);
                    routerTask.setTaskReferenceName(allocRouterRef);
                    routerTask.setInputParameters(
                            new LinkedHashMap<>(Map.of("state", "${" + lastStateRef + "}")));
                    tasks.add(routerTask);

                    // 2. INLINE enrich task: convert dynamic_tasks into Conductor FORK_JOIN_DYNAMIC
                    // format
                    //    Input: router output dynamic_tasks: [{node: "...", input: {...}}, ...]
                    //    Output: {dynamicTasks: [{name, taskReferenceName, type, inputParameters},
                    // ...]}
                    String enrichRef = allocRef(usedRefs, "_dyn_enrich_" + nodeName);
                    WorkflowTask enrichTask = new WorkflowTask();
                    enrichTask.setType("INLINE");
                    enrichTask.setName("_dyn_enrich_" + nodeName);
                    enrichTask.setTaskReferenceName(enrichRef);

                    // Build worker ref mapping: {node_name: worker_ref} for target nodes
                    StringBuilder workerMapJs = new StringBuilder("{");
                    boolean first = true;
                    for (Map.Entry<String, String> entry : targets.entrySet()) {
                        String targetNode = entry.getValue();
                        String targetWorker = nodeWorkerRef.get(targetNode);
                        if (targetWorker != null) {
                            if (!first) workerMapJs.append(",");
                            workerMapJs
                                    .append("'")
                                    .append(targetNode)
                                    .append("':'")
                                    .append(targetWorker)
                                    .append("'");
                            first = false;
                        }
                    }
                    workerMapJs.append("}");

                    Map<String, Object> enrichInputs = new LinkedHashMap<>();
                    enrichInputs.put("evaluatorType", "graaljs");
                    enrichInputs.put(
                            "dynamic_tasks", "${" + allocRouterRef + ".output.dynamic_tasks}");
                    enrichInputs.put(
                            "expression",
                            "(function(){"
                                    + "var dt=$.dynamic_tasks;"
                                    + "var wm="
                                    + workerMapJs
                                    + ";"
                                    + "var tasks=[];"
                                    + "for(var i=0;i<dt.length;i++){"
                                    + "var t=dt[i];"
                                    + "var wref=wm[t.node]||t.node;"
                                    + "tasks.push({"
                                    + "name:wref,"
                                    + "taskReferenceName:wref+'__dyn_'+i,"
                                    + "type:'SIMPLE',"
                                    + "inputParameters:{state:t.input}"
                                    + "});"
                                    + "}"
                                    + "return{dynamicTasks:tasks,dynamicTasksInputs:{}};"
                                    + "})()");
                    enrichTask.setInputParameters(enrichInputs);
                    tasks.add(enrichTask);

                    // 3. FORK_JOIN_DYNAMIC task
                    String dynForkRef = allocRef(usedRefs, "_dyn_fork_" + nodeName);
                    WorkflowTask dynForkTask = new WorkflowTask();
                    dynForkTask.setType("FORK_JOIN_DYNAMIC");
                    dynForkTask.setName("FORK_JOIN_DYNAMIC");
                    dynForkTask.setTaskReferenceName(dynForkRef);
                    dynForkTask.setDynamicForkTasksParam("dynamicTasks");
                    dynForkTask.setDynamicForkTasksInputParamName("dynamicTasksInputs");
                    Map<String, Object> dynForkInputs = new LinkedHashMap<>();
                    dynForkInputs.put(
                            "dynamicTasks", "${" + enrichRef + ".output.result.dynamicTasks}");
                    dynForkInputs.put(
                            "dynamicTasksInputs",
                            "${" + enrichRef + ".output.result.dynamicTasksInputs}");
                    dynForkTask.setInputParameters(dynForkInputs);
                    tasks.add(dynForkTask);

                    // 4. JOIN task
                    String dynJoinRef = allocRef(usedRefs, "_dyn_join_" + nodeName);
                    WorkflowTask dynJoinTask = new WorkflowTask();
                    dynJoinTask.setType("JOIN");
                    dynJoinTask.setName("JOIN");
                    dynJoinTask.setTaskReferenceName(dynJoinRef);
                    tasks.add(dynJoinTask);

                    // 5. State merge INLINE: collect all dynamic branch outputs, merge with
                    // reducers
                    //    Dynamic branch refs are unknown at compile time, so iterate over join
                    // output keys
                    String dynMergeRef = allocRef(usedRefs, "_dyn_merge_" + nodeName);
                    WorkflowTask dynMergeTask = new WorkflowTask();
                    dynMergeTask.setType("INLINE");
                    dynMergeTask.setName("_dyn_merge_" + nodeName);
                    dynMergeTask.setTaskReferenceName(dynMergeRef);

                    Map<String, Object> dynMergeInputs = new LinkedHashMap<>();
                    dynMergeInputs.put("evaluatorType", "graaljs");
                    dynMergeInputs.put("joinOutput", "${" + dynJoinRef + ".output}");
                    dynMergeInputs.put("parentState", "${" + allocRouterRef + ".output.state}");

                    // Build reducer-aware merge JS
                    StringBuilder mergeJs = new StringBuilder("(function(){");
                    mergeJs.append("var jo=$.joinOutput;var ps=$.parentState||{};");
                    mergeJs.append("var m={};for(var k in ps)m[k]=ps[k];");
                    mergeJs.append("for(var ref in jo){");
                    mergeJs.append("var b=(jo[ref]&&jo[ref].state)?jo[ref].state:null;");
                    mergeJs.append("if(!b||typeof b!=='object')continue;");
                    mergeJs.append("for(var k in b){");

                    // Reducer-aware merge for each field
                    Set<String> addReducerFields = new LinkedHashSet<>();
                    for (Map.Entry<String, String> entry : reducers.entrySet()) {
                        if ("add".equals(entry.getValue())) {
                            addReducerFields.add(entry.getKey());
                        }
                    }
                    if (!addReducerFields.isEmpty()) {
                        boolean firstField = true;
                        for (String field : addReducerFields) {
                            if (!firstField) mergeJs.append("else ");
                            mergeJs.append("if(k==='").append(field).append("'){");
                            mergeJs.append("if(!m[k])m[k]=[];");
                            mergeJs.append("if(Array.isArray(b[k]))m[k]=m[k].concat(b[k]);");
                            mergeJs.append("else m[k]=m[k].concat([b[k]]);");
                            mergeJs.append("}");
                            firstField = false;
                        }
                        mergeJs.append("else{m[k]=b[k]}");
                    } else {
                        mergeJs.append("m[k]=b[k]");
                    }

                    mergeJs.append("}}");
                    // Extract result from merged state
                    mergeJs.append("var r='';");
                    mergeJs.append("if(m.result)r=m.result;");
                    mergeJs.append("else if(m.final_report)r=m.final_report;");
                    mergeJs.append("else if(m.output)r=m.output;");
                    mergeJs.append("return{state:m,result:r};})()");

                    dynMergeInputs.put("expression", mergeJs.toString());
                    dynMergeTask.setInputParameters(dynMergeInputs);
                    tasks.add(dynMergeTask);

                    lastTaskRef = dynMergeRef;
                    lastStateRef = dynMergeRef + ".output.result.state";

                    // Mark target nodes as visited (they're invoked dynamically)
                    for (String targetNode : targets.values()) {
                        visited.add(targetNode);
                    }

                    // Queue the nodes that come after the dynamic targets
                    for (String targetNode : targets.values()) {
                        List<String> nextAfterTarget =
                                adjacency.getOrDefault(targetNode, List.of());
                        for (String next : nextAfterTarget) {
                            if (!"__end__".equals(next) && !visited.contains(next)) {
                                queue.add(next);
                            }
                        }
                    }

                    log.debug(
                            "FORK_JOIN_DYNAMIC compiled: fork={}, join={}, merge={}",
                            dynForkRef,
                            dynJoinRef,
                            dynMergeRef);

                } else if (routerRef != null && targets != null && !targets.isEmpty()) {
                    // Classify targets: back-edges (cycle) vs forward-edges (exit)
                    Map<String, String> continueDecisions = new LinkedHashMap<>();
                    Map<String, String> exitDecisions = new LinkedHashMap<>();
                    String backEdgeTarget = null;

                    for (Map.Entry<String, String> entry : targets.entrySet()) {
                        String targetNode = entry.getValue();
                        if ("__end__".equals(targetNode)) {
                            exitDecisions.put(entry.getKey(), targetNode);
                        } else if (visited.contains(targetNode)
                                && nodeTaskStartIndex.containsKey(targetNode)) {
                            continueDecisions.put(entry.getKey(), targetNode);
                            if (backEdgeTarget == null) backEdgeTarget = targetNode;
                        } else {
                            exitDecisions.put(entry.getKey(), targetNode);
                        }
                    }

                    if (!continueDecisions.isEmpty() && backEdgeTarget != null) {
                        // === CYCLE DETECTED: compile as DO_WHILE loop ===
                        log.debug(
                                "Cycle detected at '{}': back-edge to '{}', compiling as DO_WHILE",
                                nodeName,
                                backEdgeTarget);

                        // 1. Build router task (will be last task in loop body)
                        String allocRouterRef = allocRef(usedRefs, routerRef);
                        WorkflowTask routerTask = new WorkflowTask();
                        routerTask.setType("SIMPLE");
                        routerTask.setName(routerRef);
                        routerTask.setTaskReferenceName(allocRouterRef);
                        routerTask.setInputParameters(
                                new LinkedHashMap<>(Map.of("state", "${" + lastStateRef + "}")));

                        // 2. Extract loop body: tasks from back-edge target's start to current end
                        int loopStartIdx = nodeTaskStartIndex.get(backEdgeTarget);
                        List<WorkflowTask> loopBodyTasks =
                                new ArrayList<>(tasks.subList(loopStartIdx, tasks.size()));
                        loopBodyTasks.add(routerTask);

                        // Remove extracted tasks from main list
                        tasks.subList(loopStartIdx, tasks.size()).clear();

                        // 3. Create state-bridge INLINE (first task in loop body)
                        // Iteration 1: uses pre-loop state; Iteration 2+: uses router state
                        String bridgeRef = allocRef(usedRefs, "_loop_bridge_" + backEdgeTarget);
                        WorkflowTask bridgeTask = new WorkflowTask();
                        bridgeTask.setType("INLINE");
                        bridgeTask.setName("_loop_bridge");
                        bridgeTask.setTaskReferenceName(bridgeRef);

                        Map<String, Object> bridgeInputs = new LinkedHashMap<>();
                        bridgeInputs.put("evaluatorType", "graaljs");
                        bridgeInputs.put("router_state", "${" + allocRouterRef + ".output.state}");

                        String entryStateRef = nodeEntryStateRef.get(backEdgeTarget);
                        if (entryStateRef != null) {
                            bridgeInputs.put("pre_loop_state", "${" + entryStateRef + "}");
                        } else {
                            bridgeInputs.put("pre_loop_state", initialState);
                        }

                        bridgeInputs.put(
                                "expression",
                                "(function(){"
                                        + "var rs=$.router_state;"
                                        + "var ps=$.pre_loop_state;"
                                        + "if(rs&&typeof rs==='object'&&Object.keys(rs).length>0)return{state:rs};"
                                        + "return{state:ps};"
                                        + "})()");
                        bridgeTask.setInputParameters(bridgeInputs);

                        // 4. Update first loop task's state input to use bridge output
                        WorkflowTask firstLoopTask = loopBodyTasks.get(0);
                        if (firstLoopTask.getInputParameters() != null) {
                            firstLoopTask
                                    .getInputParameters()
                                    .put("state", "${" + bridgeRef + ".output.result.state}");
                        }

                        // 5. Prepend state-bridge to loop body
                        loopBodyTasks.add(0, bridgeTask);

                        // 6. Build loop condition: continue while router decision matches a
                        // back-edge
                        String doWhileRef = allocRef(usedRefs, "_loop_" + nodeName);
                        StringBuilder loopCond = new StringBuilder();
                        loopCond.append("if ( $.")
                                .append(doWhileRef)
                                .append("['iteration'] < ")
                                .append(recursionLimit)
                                .append(" && (");
                        boolean firstCond = true;
                        for (String decision : continueDecisions.keySet()) {
                            if (!firstCond) loopCond.append(" || ");
                            loopCond.append("$.")
                                    .append(allocRouterRef)
                                    .append("['decision'] == '")
                                    .append(decision)
                                    .append("'");
                            firstCond = false;
                        }
                        loopCond.append(") ) { true; } else { false; }");

                        // 7. Create DO_WHILE task
                        Map<String, Object> loopInputs = new LinkedHashMap<>();
                        loopInputs.put(doWhileRef, "${" + doWhileRef + "}");
                        WorkflowTask doWhileTask =
                                buildDoWhile(
                                        doWhileRef, loopCond.toString(), loopBodyTasks, loopInputs);
                        tasks.add(doWhileTask);

                        // 8. After the loop: state comes from router's last iteration output
                        lastTaskRef = allocRouterRef;
                        lastStateRef = allocRouterRef + ".output.state";

                        // 9. Queue exit-path nodes for compilation after the loop
                        for (Map.Entry<String, String> exit : exitDecisions.entrySet()) {
                            String exitTarget = exit.getValue();
                            if (!"__end__".equals(exitTarget) && !visited.contains(exitTarget)) {
                                queue.add(exitTarget);
                            }
                        }

                        log.debug(
                                "DO_WHILE loop compiled: ref={}, {} continue decisions, {} exit paths",
                                doWhileRef,
                                continueDecisions.size(),
                                exitDecisions.size());

                    } else {
                        // === No cycle: SWITCH approach ===

                        // Add router task
                        String allocRouterRef = allocRef(usedRefs, routerRef);
                        WorkflowTask routerTask = new WorkflowTask();
                        routerTask.setType("SIMPLE");
                        routerTask.setName(routerRef);
                        routerTask.setTaskReferenceName(allocRouterRef);
                        routerTask.setInputParameters(
                                new LinkedHashMap<>(Map.of("state", "${" + lastStateRef + "}")));
                        tasks.add(routerTask);

                        // SWITCH task: value-param evaluator
                        String switchRef = allocRef(usedRefs, "_route_" + nodeName);
                        WorkflowTask switchTask = new WorkflowTask();
                        switchTask.setType("SWITCH");
                        switchTask.setName("_route_" + nodeName);
                        switchTask.setTaskReferenceName(switchRef);
                        switchTask.setEvaluatorType("value-param");
                        switchTask.setExpression("switchCaseValue");
                        switchTask.setInputParameters(
                                new LinkedHashMap<>(
                                        Map.of(
                                                "switchCaseValue",
                                                "${" + allocRouterRef + ".output.decision}")));

                        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();
                        List<String> branchLastRefs = new ArrayList<>();

                        for (Map.Entry<String, String> entry : targets.entrySet()) {
                            String decision = entry.getKey();
                            String targetNode = entry.getValue();
                            String targetWorkerRef = nodeWorkerRef.get(targetNode);
                            if (targetWorkerRef == null) continue;

                            Map<String, Object> targetSpec = nodeSpecs.get(targetNode);
                            boolean targetIsLlm =
                                    targetSpec != null
                                            && Boolean.TRUE.equals(targetSpec.get("_llm_node"));
                            boolean targetIsHuman =
                                    targetSpec != null
                                            && Boolean.TRUE.equals(targetSpec.get("_human_node"));

                            List<WorkflowTask> branchTasks = new ArrayList<>();
                            String branchLastRef;

                            if (targetIsHuman) {
                                String tHumanRef = allocRef(usedRefs, "human_" + targetNode);
                                String tHumanPrompt =
                                        targetSpec.get("_human_prompt") instanceof String
                                                ? (String) targetSpec.get("_human_prompt")
                                                : "";
                                String branchStateExpr = "${" + allocRouterRef + ".output.state}";

                                HumanTaskBuilder.Pipeline branchHumanPipeline =
                                        HumanTaskBuilder.create(
                                                        tHumanRef,
                                                        config.getName() + " — " + targetNode)
                                                .contextInput("state", branchStateExpr)
                                                .contextInput(
                                                        "_prompt",
                                                        !tHumanPrompt.isEmpty()
                                                                ? tHumanPrompt
                                                                : null)
                                                .graphNodeValidation(
                                                        config.getModel(),
                                                        tHumanPrompt,
                                                        branchStateExpr)
                                                .build();
                                for (WorkflowTask pt : branchHumanPipeline.getTasks()) {
                                    usedRefs.add(pt.getTaskReferenceName());
                                }
                                branchTasks.addAll(branchHumanPipeline.getTasks());

                                branchLastRef = tHumanRef + "_process";
                            } else if (targetIsLlm) {
                                LlmNodeResult llmRes =
                                        buildLlmNodeTasks(
                                                config,
                                                targetSpec,
                                                "${" + allocRouterRef + ".output.state}",
                                                usedRefs,
                                                targetWorkerRef,
                                                targetNode,
                                                null);
                                branchTasks.addAll(llmRes.tasks());
                                branchLastRef = llmRes.lastTaskRef();
                            } else {
                                String tWorkerRef = allocRef(usedRefs, targetWorkerRef);
                                WorkflowTask branchTask = new WorkflowTask();
                                branchTask.setType("SIMPLE");
                                branchTask.setName(targetWorkerRef);
                                branchTask.setTaskReferenceName(tWorkerRef);
                                branchTask.setInputParameters(
                                        new LinkedHashMap<>(
                                                Map.of(
                                                        "state",
                                                        "${" + allocRouterRef + ".output.state}")));
                                branchTasks.add(branchTask);
                                branchLastRef = tWorkerRef;
                            }

                            decisionCases.put(decision, branchTasks);
                            branchLastRefs.add(branchLastRef);
                            visited.add(targetNode);
                        }
                        switchTask.setDecisionCases(decisionCases);
                        tasks.add(switchTask);

                        // Coalesce branch outputs
                        if (branchLastRefs.size() > 1) {
                            String coalesceRef = allocRef(usedRefs, "_coalesce_" + nodeName);
                            WorkflowTask coalesceTask = new WorkflowTask();
                            coalesceTask.setType("INLINE");
                            coalesceTask.setName("_coalesce_" + nodeName);
                            coalesceTask.setTaskReferenceName(coalesceRef);

                            Map<String, Object> coalesceInputs = new LinkedHashMap<>();
                            coalesceInputs.put("evaluatorType", "graaljs");

                            // Use the router's decision to select the correct branch output
                            // instead of || chaining which swallows falsy values (0, "", false).
                            coalesceInputs.put(
                                    "decision", "${" + allocRouterRef + ".output.decision}");
                            StringBuilder js = new StringBuilder("(function(){");
                            for (int bi = 0; bi < branchLastRefs.size(); bi++) {
                                String br = branchLastRefs.get(bi);
                                coalesceInputs.put("r" + bi, "${" + br + ".output.result}");
                                coalesceInputs.put("s" + bi, "${" + br + ".output.state}");
                            }
                            // Map decision values to branch indices
                            js.append("var d=$.decision;");
                            int bi2 = 0;
                            for (Map.Entry<String, String> entry : targets.entrySet()) {
                                String targetWorkerRef2 = nodeWorkerRef.get(entry.getValue());
                                if (targetWorkerRef2 == null) continue;
                                if (bi2 == 0) {
                                    js.append("if(d==='").append(entry.getKey()).append("')");
                                } else {
                                    js.append("else if(d==='").append(entry.getKey()).append("')");
                                }
                                js.append("{return{result:$.r")
                                        .append(bi2)
                                        .append(",state:$.s")
                                        .append(bi2)
                                        .append("};}");
                                bi2++;
                            }
                            // Fallback: try each branch for a non-null state
                            js.append("var r=null,s=null;");
                            for (int bi = 0; bi < branchLastRefs.size(); bi++) {
                                js.append("if($.s")
                                        .append(bi)
                                        .append("!=null&&$.s")
                                        .append(bi)
                                        .append("!==undefined){r=$.r")
                                        .append(bi)
                                        .append(";s=$.s")
                                        .append(bi)
                                        .append(";}");
                            }
                            js.append("return{result:r!==null?r:'',state:s!==null?s:{}};})()");
                            coalesceInputs.put("expression", js.toString());

                            coalesceTask.setInputParameters(coalesceInputs);
                            tasks.add(coalesceTask);

                            lastTaskRef = coalesceRef;
                            lastStateRef = coalesceRef + ".output.result.state";
                        } else {
                            lastTaskRef = branchLastRefs.get(0);
                            lastStateRef = branchLastRefs.get(0) + ".output.state";
                        }
                    }
                }
            } else {
                // Simple edges: check for fan-out (parallel branches)
                List<String> nextTargets = adjacency.getOrDefault(nodeName, List.of());
                if (nextTargets.size() > 1) {
                    // Mid-graph fan-out: compile as FORK_JOIN
                    String midJoinPoint = findJoinPoint(nextTargets, adjacency);
                    log.debug(
                            "Mid-graph fan-out from '{}' to {} branches, join at '{}'",
                            nodeName,
                            nextTargets.size(),
                            midJoinPoint);

                    List<List<WorkflowTask>> midForkBranches = new ArrayList<>();
                    List<String> midJoinOnRefs = new ArrayList<>();
                    List<String> midBranchStateExprs = new ArrayList<>();

                    for (String branchTarget : nextTargets) {
                        List<WorkflowTask> branchTasks = new ArrayList<>();
                        String branchNode = branchTarget;
                        String branchLastStateRef = null;

                        while (branchNode != null
                                && !branchNode.equals(midJoinPoint)
                                && !"__end__".equals(branchNode)
                                && !visited.contains(branchNode)) {
                            visited.add(branchNode);
                            String wRef = nodeWorkerRef.get(branchNode);
                            if (wRef == null) break;

                            Object bStateInput =
                                    branchLastStateRef != null
                                            ? "${" + branchLastStateRef + "}"
                                            : "${" + lastStateRef + "}";
                            Map<String, Object> bNodeSpec = nodeSpecs.get(branchNode);
                            boolean bIsLlm =
                                    bNodeSpec != null
                                            && Boolean.TRUE.equals(bNodeSpec.get("_llm_node"));

                            if (bIsLlm) {
                                LlmNodeResult llmRes =
                                        buildLlmNodeTasks(
                                                config,
                                                bNodeSpec,
                                                bStateInput,
                                                usedRefs,
                                                wRef,
                                                branchNode,
                                                null);
                                branchTasks.addAll(llmRes.tasks());
                                branchLastStateRef = llmRes.lastStateRef();
                            } else {
                                String nodeRef = allocRef(usedRefs, wRef);
                                WorkflowTask nodeTask = new WorkflowTask();
                                nodeTask.setType("SIMPLE");
                                nodeTask.setName(wRef);
                                nodeTask.setTaskReferenceName(nodeRef);
                                nodeTask.setInputParameters(
                                        new LinkedHashMap<>(Map.of("state", bStateInput)));
                                branchTasks.add(nodeTask);
                                branchLastStateRef = nodeRef + ".output.state";
                            }

                            // Move to next node in this branch (stop at join point)
                            List<String> nexts = adjacency.getOrDefault(branchNode, List.of());
                            branchNode =
                                    (nexts.size() == 1
                                                    && !nexts.get(0).equals(midJoinPoint)
                                                    && !"__end__".equals(nexts.get(0)))
                                            ? nexts.get(0)
                                            : null;
                        }

                        if (!branchTasks.isEmpty()) {
                            midForkBranches.add(branchTasks);
                            midJoinOnRefs.add(
                                    branchTasks.get(branchTasks.size() - 1).getTaskReferenceName());
                            midBranchStateExprs.add(branchLastStateRef);
                        }
                    }

                    // FORK_JOIN task
                    String midForkRef = allocRef(usedRefs, "_fork_" + nodeName);
                    WorkflowTask forkTask = new WorkflowTask();
                    forkTask.setType("FORK_JOIN");
                    forkTask.setTaskReferenceName(midForkRef);
                    forkTask.setForkTasks(midForkBranches);
                    tasks.add(forkTask);

                    // JOIN task
                    String midJoinRef = allocRef(usedRefs, "_join_" + nodeName);
                    WorkflowTask joinTask = new WorkflowTask();
                    joinTask.setType("JOIN");
                    joinTask.setTaskReferenceName(midJoinRef);
                    joinTask.setJoinOn(midJoinOnRefs);
                    tasks.add(joinTask);

                    // State-merge INLINE: combine all branch states (reducer-aware)
                    String midMergeRef = allocRef(usedRefs, "_fork_merge_" + nodeName);
                    tasks.add(
                            buildForkMergeTask(
                                    midMergeRef, midJoinOnRefs, midBranchStateExprs, reducers));

                    lastTaskRef = midMergeRef;
                    lastStateRef = midMergeRef + ".output.result.state";

                    // Queue join point for further processing
                    if (midJoinPoint != null) {
                        queue.add(midJoinPoint);
                    }
                } else {
                    queue.addAll(nextTargets);
                }
            }
        }

        // Build the workflow
        WorkflowDef wf = new WorkflowDef();
        wf.setName(config.getName());
        wf.setVersion(1);
        wf.setInputParameters(new ArrayList<>(WORKFLOW_INPUTS));
        wf.setTasks(tasks);

        // Output: reference the last executed task's result.
        // For INLINE tasks (coalesce/fork_merge), result is nested under output.result.
        // For SIMPLE tasks, result is under output.result.
        String outputRef;
        if (lastTaskRef != null
                && (lastTaskRef.startsWith("_coalesce_")
                        || lastTaskRef.startsWith("_fork_merge")
                        || lastTaskRef.startsWith("_sg_out_")
                        || lastTaskRef.startsWith("_llm_out_"))) {
            outputRef = "${" + lastTaskRef + ".output.result.result}";
        } else {
            outputRef = lastTaskRef != null ? "${" + lastTaskRef + ".output.result}" : "";
        }
        if (isSubgraphWorkflow) {
            // Subgraph workflows return both result and state for the parent finish worker
            String stateRef;
            if (lastStateRef != null) {
                stateRef = "${" + lastStateRef + "}";
            } else {
                stateRef = "";
            }
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("result", outputRef);
            outputs.put("state", stateRef);
            wf.setOutputParameters(outputs);
        } else {
            wf.setOutputParameters(Map.of("result", outputRef));
        }

        // Preserve metadata
        Map<String, Object> metadata =
                config.getMetadata() != null
                        ? new LinkedHashMap<>(config.getMetadata())
                        : new LinkedHashMap<>();
        metadata.put("_graph_workflow", true);
        if (config.getModel() != null) {
            metadata.put("model", config.getModel());
        }
        wf.setMetadata(metadata);

        return wf;
    }

    private boolean isFrameworkPassthrough(AgentConfig config) {
        return config.getMetadata() != null
                && Boolean.TRUE.equals(config.getMetadata().get("_framework_passthrough"));
    }

    WorkflowDef compileFrameworkPassthrough(AgentConfig config) {
        log.debug("Compiling framework passthrough workflow: {}", config.getName());

        if (config.getTools() == null || config.getTools().isEmpty()) {
            throw new IllegalArgumentException(
                    "Passthrough agent '"
                            + config.getName()
                            + "' must have exactly one worker tool defined.");
        }
        String workerName = config.getTools().get(0).getName();

        WorkflowTask fwTask = new WorkflowTask();
        fwTask.setType("SIMPLE");
        fwTask.setName(workerName);
        fwTask.setTaskReferenceName("_fw_task");
        fwTask.setInputParameters(
                new LinkedHashMap<>(
                        Map.of(
                                "prompt", "${workflow.input.prompt}",
                                "session_id", "${workflow.input.session_id}",
                                "media", "${workflow.input.media}",
                                "cwd", "${workflow.input.cwd}")));

        WorkflowDef wf = new WorkflowDef();
        wf.setName(config.getName());
        wf.setVersion(1);
        List<String> inputs = new ArrayList<>(WORKFLOW_INPUTS);
        inputs.add("context");
        wf.setInputParameters(inputs);
        wf.setTasks(List.of(fwTask));
        // Output both result and context so sequential pipelines can merge
        // pipeline state across passthrough stages (same contract as all other
        // agent workflow types).
        wf.setOutputParameters(
                Map.of(
                        "result", "${_fw_task.output.result}",
                        "context", "${workflow.input.context}"));

        Map<String, Object> metadata =
                config.getMetadata() != null
                        ? new LinkedHashMap<>(config.getMetadata())
                        : new LinkedHashMap<>();
        wf.setMetadata(metadata);

        return wf;
    }
}
