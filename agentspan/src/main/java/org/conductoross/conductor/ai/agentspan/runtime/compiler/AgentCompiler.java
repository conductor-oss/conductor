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
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowClassifier;
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

    /** JS evaluator identifier used by every INLINE task this package emits. */
    static final String GRAALJS_EVALUATOR_TYPE = "graaljs";

    /** Default {@code maxTokens} for an LLM task when {@code AgentConfig} doesn't set one. */
    private static final int DEFAULT_MAX_TOKENS = 16384;

    /** Default loop iteration cap when {@code AgentConfig.maxTurns} isn't set. */
    private static final int DEFAULT_MAX_TURNS = 25;

    /** Iteration cap on the outer required-tools-enforcement retry loop. */
    private static final int REQUIRED_TOOLS_MAX_ITERATIONS = 3;

    static final List<String> WORKFLOW_INPUTS = List.of("prompt", "session_id", "media", "cwd");
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
        } else if (GraphStructureCompiler.appliesTo(config)) {
            // Graph-structure: custom StateGraph with node/edge workflow
            wf = new GraphStructureCompiler(this).compile(config);
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

        // Stamp the agent classifier and definition into workflow metadata. The explicit
        // classifier is what execution search indexes, so agent runs do not appear in the
        // workflow-only execution list.
        stampAgentMetadata(wf, config);

        // Ensure every task has a name (Conductor requires it for execution)
        if (wf.getTasks() != null) {
            wf.getTasks().forEach(AgentCompiler::ensureTaskNames);
        }

        // Ensure every taskReferenceName is unique within the workflow.
        // Finds duplicates, renames them (appending _2, _3, etc.), and updates
        // all ${oldRef...} expressions in inputParameters/outputParameters so
        // the workflow remains internally consistent.
        if (wf.getTasks() != null) {
            WorkflowTaskRefs.ensureUniqueRefNames(wf.getTasks(), wf);
        }

        return wf;
    }

    /**
     * Marks a generated workflow definition as an agent and retains the source definition needed to
     * render its details. Embedded swarm workflows call this directly because they are built as
     * inline {@code SUB_WORKFLOW} definitions and bypass {@link #compile(AgentConfig)}.
     */
    void stampAgentMetadata(WorkflowDef wf, AgentConfig config) {
        Map<String, Object> metadata =
                wf.getMetadata() != null
                        ? new LinkedHashMap<>(wf.getMetadata())
                        : new LinkedHashMap<>();
        metadata.put("classifier", WorkflowClassifier.AGENT);
        metadata.put("agent_capabilities", new ArrayList<>(collectCapabilities(config)));
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
            List<WorkflowTask> tasks = new ArrayList<>();
            CallbackConfig beforeAgent = findCallback(config, "before_agent");
            if (beforeAgent != null) {
                tasks.add(buildCallbackTask(beforeAgent, config.getName(), null));
            }
            tasks.addAll(resolvedInstructions.getPreTasks());
            tasks.addAll(prefill.tasks());
            CallbackConfig beforeModel = findCallback(config, "before_model");
            if (beforeModel != null) {
                tasks.add(buildCallbackTask(beforeModel, config.getName(), llmRef));
            }
            tasks.add(llmTask);
            CallbackConfig afterModel = findCallback(config, "after_model");
            if (afterModel != null) {
                tasks.add(buildCallbackTask(afterModel, config.getName(), llmRef));
            }
            CallbackConfig afterAgent = findCallback(config, "after_agent");
            if (afterAgent != null) {
                tasks.add(buildCallbackTask(afterAgent, config.getName(), llmRef));
            }
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
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : DEFAULT_MAX_TURNS;

        List<WorkflowTask> loopTasks = new ArrayList<>();
        CallbackConfig beforeModel = findCallback(config, "before_model");
        if (beforeModel != null) {
            loopTasks.add(buildCallbackTask(beforeModel, config.getName(), llmRef));
        }
        loopTasks.add(llmTask);
        CallbackConfig afterModel = findCallback(config, "after_model");
        if (afterModel != null) {
            loopTasks.add(buildCallbackTask(afterModel, config.getName(), llmRef));
        }

        // Compile guardrails inside loop
        List<String[]> guardrailRefs = new ArrayList<>(); // [refName, isInline]
        List<String> retryRefs = new ArrayList<>();
        compileOutputGuardrails(
                outputGuardrails, config, contentRef, loopTasks, guardrailRefs, retryRefs);

        // Wire retry feedback into LLM participants
        wireRetryParticipants(llmTask, retryRefs);

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

        List<WorkflowTask> tasks = new ArrayList<>();
        CallbackConfig beforeAgent = findCallback(config, "before_agent");
        if (beforeAgent != null) {
            tasks.add(buildCallbackTask(beforeAgent, config.getName(), null));
        }
        tasks.addAll(resolvedInstructions.getPreTasks());
        tasks.addAll(prefill.tasks());
        tasks.add(loop);
        tasks.add(resolveTask);
        CallbackConfig afterAgent = findCallback(config, "after_agent");
        if (afterAgent != null) {
            tasks.add(buildCallbackTask(afterAgent, config.getName(), llmRef));
        }
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
        ToolDiscoveryOutcome discovery = resolveToolDiscovery(tc, config, tools, hasMcp, hasApi);
        ToolCompiler.DiscoveryResult discoveryResult = discovery.discoveryResult();
        List<Map<String, Object>> toolSpecs = discovery.toolSpecs();

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

        // Tool callbacks belong inside the tool_call branch. Placing them outside the router
        // would incorrectly invoke them for normal final-answer turns with no tool invocation.
        List<WorkflowTask> toolCallTasks = toolRouter.getDecisionCases().get("tool_call");
        CallbackConfig beforeTool = findCallback(config, "before_tool");
        if (beforeTool != null) {
            toolCallTasks.add(0, buildCallbackTask(beforeTool, config.getName(), llmRef));
        }
        CallbackConfig afterTool = findCallback(config, "after_tool");
        if (afterTool != null) {
            // This remains after the dynamic fork/JOIN/state merge chain and is never reached for
            // an approval rejection because that branch deliberately TERMINATEs the agent run.
            toolCallTasks.add(buildCallbackTask(afterTool, config.getName(), llmRef));
        }

        // Build loop body
        List<WorkflowTask> loopTasks = new ArrayList<>();

        // Context injection: compute state, signals, and immediately preceding tool-result prefix
        // (prompt is appended via template).  Tool results are the durable observation channel for
        // a ReAct turn: without them a resumed LLM only sees the original user prompt and can
        // repeat a completed human/tool call indefinitely.
        WorkflowTask ctxInject = buildContextInjectTask(toRef(config.getName()));
        loopTasks.add(ctxInject);

        // Replace user message prompt with context prefix + base prompt.
        // ctx_inject outputs only the state/signals prefix (small, changes per turn)
        // with its own trailing '\n\n' separator when non-empty, empty otherwise —
        // so concatenation never injects a leading-whitespace artifact when there's
        // no context to prepend. The base prompt is referenced once via
        // ${workflow.input.prompt} — Conductor resolves both ${} references but
        // only the prefix is stored per-turn.
        injectContextIntoUserMessage(llmTask, ctxInject.getTaskReferenceName());

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
        compileOutputGuardrails(
                outputGuardrails,
                config,
                ref(llmRef + ".output.result"),
                loopTasks,
                guardrailRefs,
                retryRefs);

        loopTasks.add(toolRouter);

        // Merge tool-level guardrail refs (from tool routing) into tracking lists
        guardrailRefs.addAll(toolRoutingResult.getToolGuardrailRefs());
        retryRefs.addAll(toolRoutingResult.getToolGuardrailRetryRefs());

        // Wire all retry refs (agent + tool guardrails) into LLM participants
        wireRetryParticipants(llmTask, retryRefs);

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
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : DEFAULT_MAX_TURNS;

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
        ctxResolve.setType(TaskType.TASK_TYPE_INLINE);
        ctxResolve.setTaskReferenceName(ctxResolveRef);
        ctxResolve.setInputParameters(
                Map.of(
                        "evaluatorType",
                        GRAALJS_EVALUATOR_TYPE,
                        "ctx",
                        "${workflow.input.context}",
                        "expression",
                        JavaScriptBuilder.nullCoalesceScript()));
        allTasks.add(ctxResolve);

        // Initialize workflow variables
        Map<String, Object> initVars = new LinkedHashMap<>();
        initVars.put("_agent_state", "${" + ctxResolveRef + ".output.result}");
        initVars.put("_last_tool_results", List.of());
        initVars.put("_stop_requested", false);
        initVars.put("_signal_injection", "");
        if (hasApproval) {
            // Pre-initialize to empty string so the system message doesn't
            // have null content on the first loop iteration.
            initVars.put("_human_feedback", "");
        }
        WorkflowTask initState = new WorkflowTask();
        initState.setType(TaskType.TASK_TYPE_SET_VARIABLE);
        initState.setTaskReferenceName(toRef(config.getName()) + "_init_state");
        initState.setInputParameters(initVars);
        allTasks.add(initState);

        // Prefill tool calls: execute before the loop so results are in LLM context
        allTasks.addAll(prefill.tasks());

        // Required tools enforcement: wrap loop + check in outer DO_WHILE
        if (config.getRequiredTools() != null && !config.getRequiredTools().isEmpty()) {
            String checkRef = toRef(config.getName()) + "_required_tools_check";
            WorkflowTask checkTask = new WorkflowTask();
            checkTask.setType(TaskType.TASK_TYPE_INLINE);
            checkTask.setTaskReferenceName(checkRef);
            checkTask.setInputParameters(
                    Map.of(
                            "evaluatorType", GRAALJS_EVALUATOR_TYPE,
                            "expression",
                                    JavaScriptBuilder.requiredToolsCheckScript(
                                            config.getRequiredTools()),
                            "completedTaskNames", ref(loopRef + ".output")));

            String outerLoopRef = toRef(config.getName()) + "_required_tools_loop";
            String outerCondition =
                    String.format(
                            "if ( $.%s.output.satisfied == false && $.%s['iteration'] < "
                                    + REQUIRED_TOOLS_MAX_ITERATIONS
                                    + " ) { true; } else { false; }",
                            checkRef,
                            outerLoopRef);
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
        ToolDiscoveryOutcome discovery = resolveToolDiscovery(tc, config, allTools, hasMcp, hasApi);
        ToolCompiler.DiscoveryResult discoveryResult = discovery.discoveryResult();
        List<Map<String, Object>> toolSpecs = discovery.toolSpecs();

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

        // Tool call routing (with tool-level guardrail metadata). The generated transfer tools
        // remain visible to the LLM through allTools/toolSpecs, but are not executable worker
        // tasks: the compiler-owned detector below consumes them before this router can fork.
        ToolCompiler.ToolCallRoutingResult toolRoutingResult;
        if (discoveryResult != null) {
            toolRoutingResult =
                    tc.buildToolCallRoutingDynamicWithResult(
                            config.getName(),
                            llmRef,
                            config.getTools(),
                            hasApproval,
                            config.getModel(),
                            discoveryResult.getMcpConfigRef(),
                            discoveryResult.getApiConfigRef());
        } else {
            toolRoutingResult =
                    tc.buildToolCallRoutingWithResult(
                            config.getName(),
                            llmRef,
                            config.getTools(),
                            hasApproval,
                            config.getModel());
        }
        WorkflowTask toolRouter = toolRoutingResult.getRouterTask();

        // Generated transfer calls are compiler-owned control signals. They are never emitted as
        // dynamic SIMPLE tasks and need no worker registration. First valid configured transfer
        // wins; a user worker with a transfer-like name remains an ordinary tool.
        Map<String, String> transferTargets = new LinkedHashMap<>();
        for (AgentConfig sub : config.getAgents()) {
            transferTargets.put(
                    toRef(config.getName()) + "_transfer_to_" + toRef(sub.getName()),
                    sub.getName());
        }
        String checkTransferRef = toRef(config.getName()) + "_check_transfer";
        WorkflowTask checkTransferTask = new WorkflowTask();
        checkTransferTask.setName(TaskType.TASK_TYPE_INLINE);
        checkTransferTask.setTaskReferenceName(checkTransferRef);
        checkTransferTask.setType(TaskType.TASK_TYPE_INLINE);
        Map<String, Object> ctInputs = new LinkedHashMap<>();
        ctInputs.put("evaluatorType", GRAALJS_EVALUATOR_TYPE);
        ctInputs.put("tool_calls", ref(llmRef + ".output.toolCalls"));
        ctInputs.put("expression", JavaScriptBuilder.detectTransferScript(transferTargets));
        checkTransferTask.setInputParameters(ctInputs);

        toolRouter
                .getInputParameters()
                .put("isTransfer", ref(checkTransferRef + ".output.result.is_transfer"));
        toolRouter.setExpression(
                "$.isTransfer == true ? 'none' : ($.toolCalls != null && $.toolCalls.length > 0 ? 'tool_call' : 'none')");

        // Build loop body
        List<WorkflowTask> loopTasks = new ArrayList<>();

        // Context injection for hybrid loop (state, signals, and recent tool-result prefix).
        WorkflowTask hybridCtxInject = buildContextInjectTask(toRef(config.getName()));
        loopTasks.add(hybridCtxInject);

        // Replace user message with context prefix + base prompt.
        // Prefix carries its own trailing '\n\n' when non-empty, empty otherwise —
        // see contextInjectionScript() docstring for why the joiner can't be a
        // literal here (leading whitespace shifts LLM behavior at temperature 0).
        injectContextIntoUserMessage(llmTask, hybridCtxInject.getTaskReferenceName());

        loopTasks.add(llmTask);

        // Output guardrails
        List<GuardrailConfig> outputGuardrails = getOutputGuardrails(config);
        List<String[]> guardrailRefs = new ArrayList<>();
        List<String> retryRefs = new ArrayList<>();
        compileOutputGuardrails(
                outputGuardrails,
                config,
                ref(llmRef + ".output.result"),
                loopTasks,
                guardrailRefs,
                retryRefs);

        // Detection must run before routing so a valid transfer does not reach the dynamic fork.
        loopTasks.add(checkTransferTask);
        loopTasks.add(toolRouter);

        // Merge tool-level guardrail refs
        guardrailRefs.addAll(toolRoutingResult.getToolGuardrailRefs());
        retryRefs.addAll(toolRoutingResult.getToolGuardrailRetryRefs());

        // Wire all retry refs into LLM participants
        wireRetryParticipants(llmTask, retryRefs);
        // DoWhile loop
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : DEFAULT_MAX_TURNS;

        String hasToolCalls =
                String.format(
                        "($.%s['toolCalls'] != null && $.%s['toolCalls'].length > 0)",
                        llmRef, llmRef);
        String notTransfer = String.format("($.%s.result.is_transfer != true)", checkTransferRef);

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
        transferSwitch.setType(TaskType.TASK_TYPE_SWITCH);
        transferSwitch.setTaskReferenceName(toRef(config.getName()) + "_transfer_check");
        transferSwitch.setEvaluatorType("value-param");
        transferSwitch.setExpression("switchCaseValue");
        transferSwitch.setInputParameters(
                Map.of("switchCaseValue", ref(checkTransferRef + ".output.result.transfer_to")));

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
        hybridCtxResolve.setType(TaskType.TASK_TYPE_INLINE);
        hybridCtxResolve.setTaskReferenceName(hybridCtxResolveRef);
        hybridCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType",
                        GRAALJS_EVALUATOR_TYPE,
                        "ctx",
                        "${workflow.input.context}",
                        "expression",
                        JavaScriptBuilder.nullCoalesceScript()));

        // Initialize workflow variables
        Map<String, Object> initHybridVars = new LinkedHashMap<>();
        initHybridVars.put("_agent_state", "${" + hybridCtxResolveRef + ".output.result}");
        initHybridVars.put("_last_tool_results", List.of());
        initHybridVars.put("_stop_requested", false);
        initHybridVars.put("_signal_injection", "");
        if (hasApproval) {
            initHybridVars.put("_human_feedback", "");
        }
        WorkflowTask initStateHybrid = new WorkflowTask();
        initStateHybrid.setType(TaskType.TASK_TYPE_SET_VARIABLE);
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
            task.setType(TaskType.TASK_TYPE_SUB_WORKFLOW);
            task.setName(sub.getName());
            task.setSubWorkflowParam(new SubWorkflowParams());
            task.getSubWorkflowParam().setName(sub.getName());
            task.setInputParameters(inputs);
        } else {
            WorkflowDef subWf = compile(sub);
            task.setType(TaskType.TASK_TYPE_SUB_WORKFLOW);
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
        task.setType(TaskType.TASK_TYPE_INLINE);
        task.setTaskReferenceName(coerceRefName);
        task.setInputParameters(
                Map.of(
                        "evaluatorType",
                        GRAALJS_EVALUATOR_TYPE,
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
            task.setType(TaskType.TASK_TYPE_SIMPLE);

            Map<String, Object> inputs = new LinkedHashMap<>(ptc.getArguments());
            task.setInputParameters(inputs);

            tasks.add(task);
            refs.add(new PrefillRef(ptc.getToolName(), refName, ptc.getArguments()));
        }

        // Multiple prefill tools → static FORK_JOIN for parallel execution
        if (tasks.size() > 1) {
            List<List<WorkflowTask>> branches = tasks.stream().map(List::of).toList();
            WorkflowTask fork = new WorkflowTask();
            fork.setType(TaskType.TASK_TYPE_FORK_JOIN);
            fork.setTaskReferenceName(toRef(config.getName()) + "_prefill_fork");
            fork.setForkTasks(branches);

            WorkflowTask join = new WorkflowTask();
            join.setType(TaskType.TASK_TYPE_JOIN);
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

        if (usesProviderNativeWebSearch(config)) {
            String provider = parsed.getProvider().toLowerCase(Locale.ROOT);
            if (!Set.of("openai", "anthropic").contains(provider)) {
                throw new IllegalArgumentException(
                        "Provider-native web search requires an OpenAI or Anthropic model; got '"
                                + parsed.getProvider()
                                + "'");
            }
            inputs.put("webSearch", true);
        }

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
                Map<String, Object> resolved = JsonSchemaTextConverter.inlineRefs(schema);
                String schemaStr = JsonSchemaTextConverter.simplifySchema(resolved);
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
        inputs.put(
                "maxTokens",
                config.getMaxTokens() != null ? config.getMaxTokens() : DEFAULT_MAX_TOKENS);

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
        workerTask.setType(TaskType.TASK_TYPE_SIMPLE);

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
        normalizeTask.setType(TaskType.TASK_TYPE_INLINE);
        Map<String, Object> normalizeInputs = new LinkedHashMap<>();
        normalizeInputs.put("evaluatorType", GRAALJS_EVALUATOR_TYPE);
        normalizeInputs.put("expression", JavaScriptBuilder.normalizeInstructionsScript());
        normalizeInputs.put("worker_output", "${" + workerRef + ".output}");
        normalizeTask.setInputParameters(normalizeInputs);

        return new ResolvedInstructions(
                List.of(workerTask, normalizeTask), ref(refName + ".output.result"));
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

    WorkflowTask buildDoWhile(
            String loopRef,
            String termCondition,
            List<WorkflowTask> loopTasks,
            Map<String, Object> inputParams) {
        WorkflowTask doWhile = new WorkflowTask();
        doWhile.setType(TaskType.TASK_TYPE_DO_WHILE);
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
        task.setType(TaskType.TASK_TYPE_INLINE);
        task.setTaskReferenceName(resolveRef);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", GRAALJS_EVALUATOR_TYPE);
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
        task.setType(TaskType.TASK_TYPE_INLINE);
        task.setTaskReferenceName(synthRef);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", GRAALJS_EVALUATOR_TYPE);
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

    /** Wire guardrail/tool retry-feedback refs into the LLM task's participants map. */
    private static void wireRetryParticipants(WorkflowTask llmTask, List<String> retryRefs) {
        if (retryRefs.isEmpty()) return;
        Map<String, Object> participants = new LinkedHashMap<>();
        for (String rr : retryRefs) {
            participants.put(rr, "user");
        }
        llmTask.getInputParameters().put("participants", participants);
    }

    /**
     * Build the context-injection INLINE task: computes state, signals, and the immediately
     * preceding tool-result prefix (prompt is appended via template). Tool results are the durable
     * observation channel for a ReAct turn: without them a resumed LLM only sees the original user
     * prompt and can repeat a completed human/tool call indefinitely.
     */
    private WorkflowTask buildContextInjectTask(String refPrefix) {
        WorkflowTask ctxInject = new WorkflowTask();
        ctxInject.setType(TaskType.TASK_TYPE_INLINE);
        ctxInject.setTaskReferenceName(refPrefix + "_ctx_inject");
        Map<String, Object> ctxInjectInputs = new LinkedHashMap<>();
        ctxInjectInputs.put("evaluatorType", GRAALJS_EVALUATOR_TYPE);
        ctxInjectInputs.put("state", "${workflow.variables._agent_state}");
        ctxInjectInputs.put("signals", "${workflow.variables._signal_injection}");
        ctxInjectInputs.put("toolResults", "${workflow.variables._last_tool_results}");
        ctxInjectInputs.put("maxSize", contextMaxSizeBytes);
        ctxInjectInputs.put("maxValueSize", contextMaxValueSizeBytes);
        ctxInjectInputs.put("expression", JavaScriptBuilder.contextInjectionScript());
        ctxInject.setInputParameters(ctxInjectInputs);
        return ctxInject;
    }

    /**
     * Replace the user message prompt with context prefix + base prompt. ctx_inject outputs only
     * the state/signals prefix (small, changes per turn) with its own trailing '\n\n' separator
     * when non-empty, empty otherwise — so concatenation never injects a leading-whitespace
     * artifact when there's no context to prepend. The base prompt is referenced once via
     * ${workflow.input.prompt} — Conductor resolves both ${} references but only the prefix is
     * stored per-turn.
     */
    @SuppressWarnings("unchecked")
    private void injectContextIntoUserMessage(WorkflowTask llmTask, String ctxInjectRef) {
        List<Object> messages = (List<Object>) llmTask.getInputParameters().get("messages");
        for (int mi = 0; mi < messages.size(); mi++) {
            if (messages.get(mi) instanceof Map<?, ?> msg && "user".equals(msg.get("role"))) {
                Map<String, Object> injectedMsg = new LinkedHashMap<>();
                injectedMsg.put("role", "user");
                injectedMsg.put(
                        "message", "${" + ctxInjectRef + ".output.result}${workflow.input.prompt}");
                injectedMsg.put("media", "${workflow.input.media}");
                messages.set(mi, injectedMsg);
                break;
            }
        }
    }

    /**
     * Compile output guardrails into {@code loopTasks}, appending each guardrail's ref/inline flag
     * to {@code guardrailRefs} and its retry ref to {@code retryRefs}. No-op if {@code
     * outputGuardrails} is empty.
     */
    private void compileOutputGuardrails(
            List<GuardrailConfig> outputGuardrails,
            AgentConfig config,
            String contentRef,
            List<WorkflowTask> loopTasks,
            List<String[]> guardrailRefs,
            List<String> retryRefs) {
        if (outputGuardrails.isEmpty()) return;
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

    /** Either a discovery-task result (MCP/API) or a static tool-spec list, never both. */
    private record ToolDiscoveryOutcome(
            ToolCompiler.DiscoveryResult discoveryResult, List<Map<String, Object>> toolSpecs) {}

    /** Build MCP/API discovery pre-loop tasks, or compile static tool specs if neither applies. */
    private ToolDiscoveryOutcome resolveToolDiscovery(
            ToolCompiler tc,
            AgentConfig config,
            List<ToolConfig> tools,
            boolean hasMcp,
            boolean hasApi) {
        if (!hasMcp && !hasApi) {
            return new ToolDiscoveryOutcome(null, tc.compileToolSpecs(tools));
        }
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

        ToolCompiler.DiscoveryResult discoveryResult;
        if (hasMcp && hasApi) {
            discoveryResult =
                    tc.buildDiscoveryTasks(
                            config.getName(), mcpTools, apiTools, staticSpecs, config.getModel());
        } else if (hasMcp) {
            discoveryResult =
                    tc.buildMcpDiscoveryTasks(
                            config.getName(), mcpTools, staticSpecs, config.getModel());
        } else {
            discoveryResult =
                    tc.buildApiDiscoveryTasks(
                            config.getName(), apiTools, staticSpecs, config.getModel());
        }
        return new ToolDiscoveryOutcome(discoveryResult, null);
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
        task.setType(TaskType.TASK_TYPE_SIMPLE);

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

    private static boolean usesProviderNativeWebSearch(AgentConfig config) {
        return config.getMetadata() != null
                && Boolean.TRUE.equals(config.getMetadata().get("_builtin_web_search"));
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
        fwTask.setType(TaskType.TASK_TYPE_SIMPLE);
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

        stampAgentMetadata(wf, config);

        return wf;
    }
}
