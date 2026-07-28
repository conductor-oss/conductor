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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.agentspan.runtime.service.PlanAndCompileTask;
import org.conductoross.conductor.ai.agentspan.runtime.service.PlannerContextFetchTask;
import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.conductoross.conductor.ai.agentspan.runtime.util.SchemaSubsetValidator;
import org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowTaskUtils;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.conductoross.conductor.common.metadata.agent.HandoffConfig;
import org.conductoross.conductor.common.metadata.agent.ModelParser;
import org.conductoross.conductor.common.metadata.agent.ModelParser.ParsedModel;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.conductoross.conductor.common.metadata.agent.WorkerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler.ref;
import static org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler.toRef;

/**
 * Compiles multi-agent strategies into Conductor workflows. Mirrors
 * python/src/conductor/agents/compiler/multi_agent_compiler.py.
 */
public class MultiAgentCompiler {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentCompiler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * /dg #2: anchored credential-placeholder pattern for plannerContext HTTP headers. Matches
     * ``${IDENTIFIER}`` only — leaves any other ``${...}`` substring (e.g. random characters that
     * happen to start with ``${``) untouched. Replacement rewrites to
     * ``${workflow.secrets.IDENTIFIER}`` so conductor's ParametersUtils resolves it wire-only (via
     * the configured SecretsDAO) at task hand-off.
     */
    private static final Pattern CREDENTIAL_PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.]*)\\}");

    private final AgentCompiler agentCompiler;

    public MultiAgentCompiler(AgentCompiler agentCompiler) {
        this.agentCompiler = agentCompiler;
    }

    /**
     * Return the deterministic workflow name used for the dynamic plan sub-workflow. Must match the
     * name produced by {@link PlanAndCompileTask} for {@code workflowDef.name}.
     */
    public static String planWorkflowName(String parentName) {
        return "pe_" + toRef(parentName) + "_plan";
    }

    /**
     * Check whether a tool named ``toolName`` is registered on the harness itself (not on a deeper
     * sub-agent). Used to validate ``plan_source.tool`` at compile time.
     *
     * <p>The check is intentionally non-recursive: the {@code plan_reader} SIMPLE task is emitted
     * in the parent harness's task namespace, so a tool that exists only on a deeper sub-agent's
     * worker won't be polled for the parent's task name. Forcing the user to declare the tool on
     * the harness keeps registration and polling namespaces consistent and makes the
     * misconfiguration surface at deploy with a clear message rather than as a silent runtime
     * no-op.
     */
    private boolean isToolRegisteredInHarness(AgentConfig config, String toolName) {
        if (config == null) return false;
        List<ToolConfig> tools = config.getTools();
        if (tools != null) {
            for (ToolConfig t : tools) {
                if (toolName.equals(t.getName())) return true;
            }
        }
        return false;
    }

    /**
     * Render the parent's tool list as a Markdown block to append to the planner's user prompt. The
     * planner sees each tool's name, description, and a compact summary of expected arguments —
     * enough to write a valid plan without inventing tool names. PAC validates the resulting plan
     * against the same set; this prompt and the validator share a contract.
     */
    private String buildAvailableToolsBlock(List<ToolConfig> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Available tools\n\n");
        sb.append(
                "Your plan's ``operations[].tool`` field MUST use a tool name from "
                        + "the list below. Any other name will fail plan validation and route "
                        + "to the fallback agent.\n\n");
        for (ToolConfig t : tools) {
            String name = t.getName() == null ? "(unnamed)" : t.getName();
            sb.append("- **`").append(name).append("`**");
            if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                sb.append(" — ").append(t.getDescription());
            }
            sb.append('\n');
            String argsSummary = summarizeToolArgs(t.getInputSchema());
            if (!argsSummary.isEmpty()) {
                sb.append("    args: ").append(argsSummary).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Render the canonical PAC plan schema as a Markdown block to append to the planner's user
     * prompt. Spelling out the JSON shape, the args-vs-generate distinction, and the
     * validation/on_success blocks means the planner agent's own ``instructions`` can focus on
     * domain-level guidance (what to plan) instead of re-teaching the universal schema in every
     * harness — which is what every existing example does today, copy-pasting ~50 lines of
     * escape-laden JSON.
     *
     * <p>The block is intentionally minimal: schema, escape rules, one worked example. Users can
     * still inline a richer example in their own ``instructions`` for domain-specific patterns. The
     * server's block is the floor, not the ceiling.
     */
    private String buildPlanSchemaBlock() {
        return "## Plan schema\n\n"
                + "Your final response MUST end with a ```json fenced block containing a "
                + "single JSON object with this shape:\n\n"
                + "```json\n"
                + "{\n"
                + "  \"steps\": [\n"
                + "    {\n"
                + "      \"id\": \"<unique step id>\",\n"
                + "      \"depends_on\": [\"<other step id>\"],   // optional; defaults to previous step\n"
                + "      \"parallel\": false,                      // run operations[] in parallel\n"
                + "      \"operations\": [\n"
                + "        // EITHER a static call:\n"
                + "        {\"tool\": \"<tool>\", \"args\": {<literal arg map>}},\n"
                + "        // OR an LLM-generated call:\n"
                + "        {\"tool\": \"<tool>\", \"generate\": {\n"
                + "          \"instructions\": \"<what the LLM should produce>\",\n"
                + "          \"output_schema\": \"<JSON shape that becomes the tool's args>\",\n"
                + "          \"max_tokens\": 4096                  // optional\n"
                + "        }}\n"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"validation\": [                              // optional\n"
                + "    {\"tool\": \"<validator tool>\", \"args\": {...},\n"
                + "     \"success_condition\": \"$.passed === true\"} // optional JS, $ = tool output\n"
                + "  ],\n"
                + "  \"on_success\": [{\"tool\": \"<tool>\", \"args\": {...}}],   // optional\n"
                + "  \"on_failure\": [{\"tool\": \"<tool>\", \"args\": {...}}]    // optional\n"
                + "}\n"
                + "```\n\n"
                + "Rules:\n"
                + "- Every ``operations[].tool`` and ``validation[].tool`` MUST be from the "
                + "Available tools list above. Other names fail plan validation and route to fallback.\n"
                + "- Use ``args`` when arg values are literals you decide now. Use ``generate`` "
                + "when an LLM should produce them at run time (e.g., the body of a write_file).\n"
                + "- ``parallel: true`` runs that step's operations concurrently (FORK_JOIN). "
                + "Cross-step concurrency is via ``depends_on`` — a step starts when all listed deps complete.\n"
                + "- The JSON must parse cleanly. Match brackets and escape strings.\n";
    }

    /**
     * Compact one-liner of an input schema's top-level properties. Avoids dumping the full JSON
     * Schema (which inflates the planner prompt disproportionately for large tool lists).
     */
    @SuppressWarnings("unchecked")
    private String summarizeToolArgs(Map<String, Object> inputSchema) {
        if (inputSchema == null) return "";
        Object propsObj = inputSchema.get("properties");
        if (!(propsObj instanceof Map)) return "";
        Map<String, Object> props = (Map<String, Object>) propsObj;
        if (props.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : props.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ");
            String type = "string";
            if (e.getValue() instanceof Map<?, ?> m && m.get("type") instanceof String s) {
                type = s;
            }
            sb.append("<").append(type).append(">");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public WorkflowDef compile(AgentConfig config) {
        // Validate uniqueness
        if (config.getAgents() != null) {
            List<String> names = config.getAgents().stream().map(AgentConfig::getName).toList();
            Set<String> unique = new HashSet<>(names);
            if (unique.size() != names.size()) {
                throw new IllegalArgumentException(
                        "Duplicate agent names in '"
                                + config.getName()
                                + "'. Each sub-agent must have a unique name.");
            }
        }

        WorkflowDef strategyWf = compileStrategy(config);

        // Wrap with guardrails if needed
        List<GuardrailConfig> outputGuardrails = agentCompiler.getOutputGuardrails(config);
        if (!outputGuardrails.isEmpty()) {
            return wrapWithGuardrails(config, strategyWf);
        }
        return strategyWf;
    }

    private WorkflowDef compileStrategy(AgentConfig config) {
        AgentConfig.Strategy strategy =
                config.getStrategy() != null ? config.getStrategy() : AgentConfig.Strategy.HANDOFF;
        // Exhaustive over AgentConfig.Strategy -- no default branch needed (or possible to miss
        // a case without a compile error) now that this switches on the enum instead of a raw
        // string.
        return switch (strategy) {
            case HANDOFF -> compileHandoff(config);
            case SEQUENTIAL -> compileSequential(config);
            case PARALLEL -> compileParallel(config);
            case ROUTER -> compileRouter(config);
            case ROUND_ROBIN -> compileRotation(config, false);
            case RANDOM -> compileRotation(config, true);
            case SWARM -> compileSwarm(config);
            case MANUAL -> compileManual(config);
            case PLAN_EXECUTE -> compilePlanExecute(config);
        };
    }

    // ── Handoff strategy ────────────────────────────────────────────

    private WorkflowDef compileHandoff(AgentConfig config) {
        ParsedModel parsed = ModelParser.parse(config.getModel());
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Handoff agent: " + config.getName());

        AgentCompiler.ResolvedInstructions instructionsPlan =
                resolveInstructionsPlan(config, toRef(config.getName()) + "_instructions");
        String instructions = instructionsPlan.getText();
        List<AgentConfig> agents = config.getAgents();
        rejectReservedAgentNames(config, agents);
        List<String> agentNames = agents.stream().map(AgentConfig::getName).toList();
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;
        String loopRef = toRef(config.getName()) + "_loop";
        String routerRef = toRef(config.getName()) + "_router";

        // Build agent descriptions for routing prompt
        StringBuilder agentsInfo = new StringBuilder();
        for (AgentConfig a : agents) {
            String desc =
                    a.getDescription() != null && !a.getDescription().isEmpty()
                            ? a.getDescription()
                            : (a.getInstructions() instanceof String
                                    ? (String) a.getInstructions()
                                    : a.getName());
            agentsInfo.append("- ").append(a.getName()).append(": ").append(desc).append("\n");
        }

        // 0. Context resolve: INLINE → null-coalesce input.context
        String handoffCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask handoffCtxResolve = new WorkflowTask();
        handoffCtxResolve.setType("INLINE");
        handoffCtxResolve.setTaskReferenceName(handoffCtxResolveRef);
        handoffCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        // 1. Init: seed conversation variable with prompt + context
        WorkflowTask initVar = new WorkflowTask();
        initVar.setType("SET_VARIABLE");
        initVar.setTaskReferenceName(toRef(config.getName()) + "_init");
        String introductions = buildIntroductions(config);
        Map<String, Object> initParams = new LinkedHashMap<>();
        if (!introductions.isEmpty()) {
            initParams.put("conversation", introductions + "\n\n${workflow.input.prompt}");
        } else {
            initParams.put("conversation", "${workflow.input.prompt}");
        }
        initParams.put("_agent_state", "${" + handoffCtxResolveRef + ".output.result}");
        initVar.setInputParameters(initParams);

        // 2. Router LLM — reads conversation, picks agent or says DONE
        String systemPrompt =
                buildCoordinatorPrompt(config.getName(), instructions, agentsInfo, agentNames);

        WorkflowTask routerLlm = buildIterativeRouterLlm(routerRef, parsed, systemPrompt, config);

        // 2a. Normalize the raw router output to a canonical agent name or DONE — the SWITCH,
        // the conversation annotation, and the loop condition all read the normalized value.
        String normRef = toRef(config.getName()) + "_route_norm";
        WorkflowTask routeNorm = buildRouteNormalizer(normRef, routerRef, agentNames);

        // 2b. Record routing decision in conversation so the router sees its own history
        String routeAnnotateRef = toRef(config.getName()) + "_route_annotate";
        WorkflowTask routeAnnotate = new WorkflowTask();
        routeAnnotate.setType("INLINE");
        routeAnnotate.setTaskReferenceName(routeAnnotateRef);
        Map<String, Object> annotateInputs = new LinkedHashMap<>();
        annotateInputs.put("evaluatorType", "graaljs");
        annotateInputs.put(
                "expression",
                "(function() { var d = $.decision; if (d === 'DONE') return $.prev; "
                        + "return $.prev + '\\n\\n[coordinator -> ' + d + ']'; })()");
        annotateInputs.put("prev", "${workflow.variables.conversation}");
        annotateInputs.put("decision", ref(normRef + ".output.result"));
        routeAnnotate.setInputParameters(annotateInputs);

        WorkflowTask routeAnnotateSet = new WorkflowTask();
        routeAnnotateSet.setType("SET_VARIABLE");
        routeAnnotateSet.setTaskReferenceName(toRef(config.getName()) + "_route_set");
        routeAnnotateSet.setInputParameters(
                Map.of("conversation", ref(routeAnnotateRef + ".output.result")));

        // 3. Switch on the normalized router output
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setTaskReferenceName(toRef(config.getName()) + "_switch");
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(Map.of("switchCaseValue", ref(normRef + ".output.result")));

        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        for (int i = 0; i < agents.size(); i++) {
            AgentConfig sub = agents.get(i);
            List<WorkflowTask> caseTasks = buildHandoffCaseTasks(config, sub, i);
            cases.put(sub.getName(), caseTasks);
        }

        // DONE case: no-op inline task
        WorkflowTask doneTask = new WorkflowTask();
        doneTask.setType("INLINE");
        doneTask.setTaskReferenceName(toRef(config.getName()) + "_done_noop");
        doneTask.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "expression", "(function() { return {result: 'done'}; })()"));
        cases.put("DONE", List.of(doneTask));

        switchTask.setDecisionCases(cases);

        // Default case: first agent (fallback for unexpected LLM output)
        if (!agents.isEmpty()) {
            AgentConfig firstAgent = agents.get(0);
            List<WorkflowTask> defaultTasks =
                    buildHandoffCaseTasks(config, firstAgent, 0, "_default");
            switchTask.setDefaultCase(defaultTasks);
        }

        // 4. DoWhile loop: continue while iteration < max_turns AND normalized decision != DONE
        String termCondition =
                String.format(
                        "if ( $.%s['iteration'] < %d && $.%s['result'] != 'DONE' ) { true; } else { false; }",
                        loopRef, maxTurns, normRef);
        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(normRef, "${" + normRef + "}");
        WorkflowTask loop =
                agentCompiler.buildDoWhile(
                        loopRef,
                        termCondition,
                        List.of(routerLlm, routeNorm, routeAnnotate, routeAnnotateSet, switchTask),
                        loopInputs);

        // 5. Final answer LLM: synthesize from accumulated conversation
        WorkflowTask finalLlm = new WorkflowTask();
        finalLlm.setName("LLM_CHAT_COMPLETE");
        finalLlm.setTaskReferenceName(toRef(config.getName()) + "_final");
        finalLlm.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> finalInputs = new LinkedHashMap<>();
        finalInputs.put("llmProvider", parsed.getProvider());
        finalInputs.put("model", parsed.getModel());
        finalInputs.put("maxTokens", config.getMaxTokens() != null ? config.getMaxTokens() : 16384);
        String finalSystemPrompt =
                (instructions.isEmpty() ? "" : instructions + "\n\n")
                        + "Based on the work done by the agents above, provide your final response to the user. "
                        + "IMPORTANT: Include ALL details from every agent's response — do NOT summarize or omit "
                        + "code examples, technical specifications, or specific recommendations. "
                        + "Organize the information coherently but preserve completeness.";
        finalInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", finalSystemPrompt),
                        Map.of("role", "user", "message", "${workflow.variables.conversation}")));
        finalLlm.setInputParameters(finalInputs);

        List<WorkflowTask> tasks = new ArrayList<>(instructionsPlan.getPreTasks());
        tasks.add(handoffCtxResolve);
        tasks.add(initVar);
        tasks.add(loop);
        if (config.isSynthesize()) {
            tasks.add(finalLlm);
        }
        wf.setTasks(tasks);
        wf.setOutputParameters(
                Map.of(
                        "result",
                        config.isSynthesize()
                                ? ref(toRef(config.getName()) + "_final.output.result")
                                : "${workflow.variables.conversation}",
                        "context",
                        "${workflow.variables._agent_state}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    // ── Sequential strategy ─────────────────────────────────────────

    private WorkflowDef compileSequential(AgentConfig config) {
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Sequential pipeline: " + config.getName());

        List<WorkflowTask> tasks = new ArrayList<>();
        String prevOutputRef = "${workflow.input.prompt}";

        // Initialize context from input (INLINE → SET_VARIABLE pattern)
        String seqCtxResolveRef = toRef(config.getName()) + "_ctx_init_resolve";
        WorkflowTask seqCtxResolve = new WorkflowTask();
        seqCtxResolve.setType("INLINE");
        seqCtxResolve.setTaskReferenceName(seqCtxResolveRef);
        seqCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));
        tasks.add(seqCtxResolve);

        WorkflowTask seqCtxInit = new WorkflowTask();
        seqCtxInit.setType("SET_VARIABLE");
        seqCtxInit.setTaskReferenceName(toRef(config.getName()) + "_ctx_init");
        seqCtxInit.setInputParameters(
                Map.of("context", "${" + seqCtxResolveRef + ".output.result}"));
        tasks.add(seqCtxInit);

        for (int i = 0; i < config.getAgents().size(); i++) {
            AgentConfig sub = config.getAgents().get(i);
            String taskRef = toRef(config.getName()) + "_step_" + i + "_" + sub.getName();
            String mediaRef = "${workflow.input.media}";

            // For non-first agents, combine the original user prompt with the
            // previous agent's output via Conductor string interpolation.
            // This ensures each agent in the sequence knows the full context.
            String promptRef = prevOutputRef;
            if (i > 0) {
                promptRef = "${workflow.input.prompt}\n\nPrevious agent output:\n" + prevOutputRef;
            }

            WorkflowTask task =
                    agentCompiler.compileSubAgent(
                            sub, taskRef, promptRef, mediaRef, "${workflow.variables.context}");
            tasks.add(task);

            // Merge child context back into pipeline context
            String mergeRef = toRef(config.getName()) + "_ctx_merge_" + i;
            WorkflowTask mergeTask = new WorkflowTask();
            mergeTask.setType("INLINE");
            mergeTask.setTaskReferenceName(mergeRef);
            mergeTask.setInputParameters(
                    Map.of(
                            "evaluatorType",
                            "graaljs",
                            "parent",
                            "${workflow.variables.context}",
                            "child",
                            "${" + taskRef + ".output.context}",
                            "expression",
                            JavaScriptBuilder.flatMergeContextScript()));
            tasks.add(mergeTask);

            String ctxSetRef = toRef(config.getName()) + "_ctx_set_" + i;
            WorkflowTask ctxSet = new WorkflowTask();
            ctxSet.setType("SET_VARIABLE");
            ctxSet.setTaskReferenceName(ctxSetRef);
            ctxSet.setInputParameters(Map.of("context", "${" + mergeRef + ".output.result}"));
            tasks.add(ctxSet);

            // Get raw result ref
            String rawRef = AgentCompiler.subAgentResultRef(sub, taskRef);

            // For non-final stages, add null coercion
            // to prevent deserialization failures when output.result is null
            if (i < config.getAgents().size() - 1) {
                String coerceRef = taskRef + "_coerce";
                tasks.add(AgentCompiler.createCoerceTask(rawRef, coerceRef));
                String coercedRef = AgentCompiler.coercedRef(coerceRef);

                // Gate check: if this stage has a gate, insert INLINE + SWITCH
                if (sub.getGate() != null) {
                    String gateRef = toRef(config.getName()) + "_gate_" + i;
                    WorkflowTask gateTask =
                            GateCompiler.compileGate(sub.getGate(), gateRef, coercedRef);
                    tasks.add(gateTask);

                    // SWITCH: "continue" → remaining stages, "stop" → end pipeline
                    WorkflowTask switchTask = new WorkflowTask();
                    switchTask.setType("SWITCH");
                    switchTask.setTaskReferenceName(toRef(config.getName()) + "_gate_switch_" + i);
                    switchTask.setEvaluatorType("value-param");
                    switchTask.setExpression("switchCaseValue");
                    switchTask.setInputParameters(
                            Map.of("switchCaseValue", "${" + gateRef + ".output.result.decision}"));

                    // "continue" case: compile remaining stages recursively
                    List<WorkflowTask> continueTasks =
                            compileRemainingStages(config, i + 1, coercedRef);
                    switchTask.setDecisionCases(Map.of("continue", continueTasks));
                    // "stop" (default): no-op — pipeline returns current output
                    switchTask.setDefaultCase(List.of());

                    tasks.add(switchTask);

                    // After the SWITCH, add an output-selector INLINE task.
                    // It walks the stages in reverse and returns the first non-null result.
                    // This ensures the workflow output is always the deepest stage that ran.
                    String selectorRef = toRef(config.getName()) + "_output_selector";
                    WorkflowTask selector = buildOutputSelector(config, i, selectorRef);
                    tasks.add(selector);

                    String selectorOutputRef = "${" + selectorRef + ".output.result}";
                    wf.setTasks(tasks);
                    wf.setOutputParameters(
                            Map.of(
                                    "result",
                                    selectorOutputRef,
                                    "context",
                                    "${workflow.variables.context}"));
                    agentCompiler.applyTimeout(wf, config);
                    return wf;
                }

                prevOutputRef = coercedRef;
            } else {
                prevOutputRef = rawRef;
            }
        }

        wf.setTasks(tasks);
        wf.setOutputParameters(
                Map.of("result", prevOutputRef, "context", "${workflow.variables.context}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    /**
     * Compile the remaining stages of a sequential pipeline (from startIndex onward). Used when a
     * gate creates a SWITCH — the "continue" branch contains the rest.
     */
    private List<WorkflowTask> compileRemainingStages(
            AgentConfig config, int startIndex, String prevOutputRef) {

        List<WorkflowTask> tasks = new ArrayList<>();

        for (int i = startIndex; i < config.getAgents().size(); i++) {
            AgentConfig sub = config.getAgents().get(i);
            String taskRef = toRef(config.getName()) + "_step_" + i + "_" + sub.getName();
            String mediaRef = "${workflow.input.media}";

            // Combine original prompt with previous output via string interpolation
            String promptRef =
                    "${workflow.input.prompt}\n\nPrevious agent output:\n" + prevOutputRef;

            WorkflowTask task =
                    agentCompiler.compileSubAgent(
                            sub, taskRef, promptRef, mediaRef, "${workflow.variables.context}");
            tasks.add(task);

            String rawRef = AgentCompiler.subAgentResultRef(sub, taskRef);

            if (i < config.getAgents().size() - 1) {
                String coerceRef = taskRef + "_coerce";
                tasks.add(AgentCompiler.createCoerceTask(rawRef, coerceRef));
                String coercedRef = AgentCompiler.coercedRef(coerceRef);

                // Nested gate
                if (sub.getGate() != null) {
                    String gateRef = toRef(config.getName()) + "_gate_" + i;
                    WorkflowTask gateTask =
                            GateCompiler.compileGate(sub.getGate(), gateRef, coercedRef);
                    tasks.add(gateTask);

                    WorkflowTask switchTask = new WorkflowTask();
                    switchTask.setType("SWITCH");
                    switchTask.setTaskReferenceName(toRef(config.getName()) + "_gate_switch_" + i);
                    switchTask.setEvaluatorType("value-param");
                    switchTask.setExpression("switchCaseValue");
                    switchTask.setInputParameters(
                            Map.of("switchCaseValue", "${" + gateRef + ".output.result.decision}"));

                    List<WorkflowTask> continueTasks =
                            compileRemainingStages(config, i + 1, coercedRef);
                    switchTask.setDecisionCases(Map.of("continue", continueTasks));
                    switchTask.setDefaultCase(List.of());
                    tasks.add(switchTask);
                    return tasks;
                }

                prevOutputRef = coercedRef;
            } else {
                prevOutputRef = rawRef;
            }
        }

        return tasks;
    }

    /**
     * Build an INLINE task that selects the deepest stage output that actually ran. Walks stages in
     * reverse: the first non-null result wins. When a gate stops the pipeline, later stages never
     * execute and their refs are null.
     */
    private WorkflowTask buildOutputSelector(
            AgentConfig config, int firstGateIndex, String refName) {
        // Build JS that checks each stage in reverse order
        StringBuilder sb = new StringBuilder();
        for (int i = config.getAgents().size() - 1; i >= 0; i--) {
            sb.append("if ($.s")
                    .append(i)
                    .append(" != null && $.s")
                    .append(i)
                    .append(" !== '') return $.s")
                    .append(i)
                    .append("; ");
        }
        sb.append("return '';");

        String script = JavaScriptBuilder.iife(sb.toString());

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", "graaljs");
        inputs.put("expression", script);

        // Add each stage's output as s0, s1, s2, ...
        for (int i = 0; i < config.getAgents().size(); i++) {
            AgentConfig sub = config.getAgents().get(i);
            String taskRef = toRef(config.getName()) + "_step_" + i + "_" + sub.getName();
            String resultRef = AgentCompiler.subAgentResultRef(sub, taskRef);
            inputs.put("s" + i, resultRef);
        }

        WorkflowTask task = new WorkflowTask();
        task.setType("INLINE");
        task.setTaskReferenceName(refName);
        task.setInputParameters(inputs);
        return task;
    }

    // ── Parallel strategy ───────────────────────────────────────────

    private WorkflowDef compileParallel(AgentConfig config) {
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Parallel agents: " + config.getName());

        List<WorkflowTask> tasks = new ArrayList<>();

        // Context init: INLINE → SET_VARIABLE (null-coalesce input.context)
        String parCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask parCtxResolve = new WorkflowTask();
        parCtxResolve.setType("INLINE");
        parCtxResolve.setTaskReferenceName(parCtxResolveRef);
        parCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));
        tasks.add(parCtxResolve);

        WorkflowTask parCtxInit = new WorkflowTask();
        parCtxInit.setType("SET_VARIABLE");
        parCtxInit.setTaskReferenceName(toRef(config.getName()) + "_ctx_init");
        parCtxInit.setInputParameters(
                Map.of("context", "${" + parCtxResolveRef + ".output.result}"));
        tasks.add(parCtxInit);

        // Build fork task
        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setType("FORK_JOIN");
        forkTask.setTaskReferenceName(toRef(config.getName()) + "_fork");

        List<List<WorkflowTask>> forkTasks = new ArrayList<>();
        List<String> joinOn = new ArrayList<>();
        List<String> taskRefs = new ArrayList<>();

        for (int i = 0; i < config.getAgents().size(); i++) {
            AgentConfig sub = config.getAgents().get(i);
            String taskRef = toRef(config.getName()) + "_parallel_" + i + "_" + sub.getName();
            WorkflowTask task =
                    agentCompiler.compileSubAgent(
                            sub,
                            taskRef,
                            "${workflow.input.prompt}",
                            "${workflow.input.media}",
                            "${workflow.variables.context}");
            forkTasks.add(List.of(task));
            joinOn.add(taskRef);
            taskRefs.add(taskRef);
        }
        forkTask.setForkTasks(forkTasks);
        forkTask.setJoinOn(joinOn);

        // Join task — joinOn on both fork and join (matches Python SDK toJSON)
        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setType("JOIN");
        joinTask.setTaskReferenceName(toRef(config.getName()) + "_fork_join");
        joinTask.setJoinOn(joinOn);

        // INLINE task to aggregate per-agent results into a consistent format:
        //   { "result": "<joined string>", "subResults": { "agentName": "output", ... } }
        WorkflowTask aggregateTask = new WorkflowTask();
        aggregateTask.setType("INLINE");
        aggregateTask.setTaskReferenceName(toRef(config.getName()) + "_aggregate");
        Map<String, Object> aggInputs = new LinkedHashMap<>();
        aggInputs.put("evaluatorType", "graaljs");

        // Pass each agent's result as a named input
        Map<String, Object> agentResults = new LinkedHashMap<>();
        for (int i = 0; i < config.getAgents().size(); i++) {
            AgentConfig sub = config.getAgents().get(i);
            String taskRef = toRef(config.getName()) + "_parallel_" + i + "_" + sub.getName();
            agentResults.put(sub.getName(), AgentCompiler.subAgentResultRef(sub, taskRef));
        }
        aggInputs.put("agentResults", agentResults);

        // Build the aggregation script
        List<String> agentNames =
                config.getAgents().stream().map(AgentConfig::getName).collect(Collectors.toList());
        aggInputs.put("expression", buildParallelAggregateScript(agentNames));
        aggregateTask.setInputParameters(aggInputs);

        // Namespaced context merge: INLINE merges parent context + each child's context under agent
        // name
        String ctxMergeRef = toRef(config.getName()) + "_ctx_merge";
        WorkflowTask ctxMergeTask = new WorkflowTask();
        ctxMergeTask.setType("INLINE");
        ctxMergeTask.setTaskReferenceName(ctxMergeRef);
        Map<String, Object> mergeInputs = new LinkedHashMap<>();
        mergeInputs.put("evaluatorType", "graaljs");
        mergeInputs.put("parentCtx", "${workflow.variables.context}");
        mergeInputs.put("agentNames", agentNames);
        for (int i = 0; i < config.getAgents().size(); i++) {
            mergeInputs.put("child_" + i, "${" + taskRefs.get(i) + ".output.context}");
        }
        mergeInputs.put("expression", JavaScriptBuilder.namespacedMergeContextScript());
        ctxMergeTask.setInputParameters(mergeInputs);

        // SET_VARIABLE to persist merged context
        String ctxSetRef = toRef(config.getName()) + "_ctx_set";
        WorkflowTask ctxSet = new WorkflowTask();
        ctxSet.setType("SET_VARIABLE");
        ctxSet.setTaskReferenceName(ctxSetRef);
        ctxSet.setInputParameters(Map.of("context", "${" + ctxMergeRef + ".output.result}"));

        tasks.addAll(List.of(forkTask, joinTask, aggregateTask, ctxMergeTask, ctxSet));
        wf.setTasks(tasks);

        // Output references the INLINE task's result + merged context
        String aggRef = toRef(config.getName()) + "_aggregate";
        wf.setOutputParameters(
                Map.of(
                        "result", "${" + aggRef + ".output.result.result}",
                        "subResults", "${" + aggRef + ".output.result.subResults}",
                        "context", "${workflow.variables.context}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    /**
     * Build a GraalJS script that aggregates parallel agent results into a consistent output format
     * with a joined string result and per-agent subResults.
     */
    private String buildParallelAggregateScript(List<String> agentNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  var results = $.agentResults;\n");
        sb.append("  var subResults = {};\n");
        sb.append("  var parts = [];\n");
        for (String name : agentNames) {
            sb.append("  var v_").append(name).append(" = results['").append(name).append("'];\n");
            sb.append("  subResults['")
                    .append(name)
                    .append("'] = (v_")
                    .append(name)
                    .append(" != null) ? String(v_")
                    .append(name)
                    .append(") : '';\n");
            sb.append("  if (v_")
                    .append(name)
                    .append(" != null && String(v_")
                    .append(name)
                    .append(") !== '') {\n");
            sb.append("    parts.push('[")
                    .append(name)
                    .append("]: ' + String(v_")
                    .append(name)
                    .append("));\n");
            sb.append("  }\n");
        }
        sb.append("  return { result: parts.join('\\n\\n'), subResults: subResults };\n");
        sb.append("})();");
        return sb.toString();
    }

    // ── Router strategy ─────────────────────────────────────────────

    private WorkflowDef compileRouter(AgentConfig config) {
        ParsedModel parsed = ModelParser.parse(config.getModel());
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Router agent: " + config.getName());
        AgentCompiler.ResolvedInstructions parentInstructions =
                resolveInstructionsPlan(config, toRef(config.getName()) + "_instructions");
        List<WorkflowTask> preTasks = new ArrayList<>(parentInstructions.getPreTasks());

        List<AgentConfig> agents = config.getAgents();
        rejectReservedAgentNames(config, agents);
        List<String> agentNames = agents.stream().map(AgentConfig::getName).toList();
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;
        String loopRef = toRef(config.getName()) + "_loop";
        String routerRef = toRef(config.getName()) + "_router";

        StringBuilder agentsInfo = new StringBuilder();
        for (AgentConfig a : agents) {
            String desc =
                    a.getDescription() != null && !a.getDescription().isEmpty()
                            ? a.getDescription()
                            : (a.getInstructions() instanceof String
                                    ? (String) a.getInstructions()
                                    : a.getName());
            agentsInfo.append("- ").append(a.getName()).append(": ").append(desc).append("\n");
        }

        // 0. Context resolve: INLINE → null-coalesce input.context
        String routerCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask routerCtxResolve = new WorkflowTask();
        routerCtxResolve.setType("INLINE");
        routerCtxResolve.setTaskReferenceName(routerCtxResolveRef);
        routerCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        // 1. Init: seed conversation variable + context
        WorkflowTask initVar = new WorkflowTask();
        initVar.setType("SET_VARIABLE");
        initVar.setTaskReferenceName(toRef(config.getName()) + "_init");
        String introductions = buildIntroductions(config);
        Map<String, Object> routerInitParams = new LinkedHashMap<>();
        if (!introductions.isEmpty()) {
            routerInitParams.put("conversation", introductions + "\n\n${workflow.input.prompt}");
        } else {
            routerInitParams.put("conversation", "${workflow.input.prompt}");
        }
        routerInitParams.put("_agent_state", "${" + routerCtxResolveRef + ".output.result}");
        initVar.setInputParameters(routerInitParams);

        // 2. Build router task (supports WorkerRef, AgentConfig, or fallback)
        Object router = config.getRouter();

        // Deserialize router from Map to typed object if needed
        if (router instanceof Map<?, ?> routerMap) {
            if (routerMap.containsKey("taskName")) {
                ObjectMapper mapper = new ObjectMapper();
                router = mapper.convertValue(routerMap, WorkerRef.class);
            } else if (routerMap.containsKey("model") || routerMap.containsKey("name")) {
                ObjectMapper mapper = new ObjectMapper();
                router = mapper.convertValue(routerMap, AgentConfig.class);
            }
        }

        WorkflowTask routerTask;
        if (router instanceof WorkerRef workerRef) {
            // Function-based router -> SIMPLE task reading conversation
            routerTask = new WorkflowTask();
            routerTask.setName(workerRef.getTaskName());
            routerTask.setTaskReferenceName(routerRef);
            routerTask.setType("SIMPLE");
            Map<String, Object> workerInputs = new LinkedHashMap<>();
            workerInputs.put("prompt", "${workflow.variables.conversation}");
            workerInputs.put("conversation", "${workflow.variables.conversation}");
            routerTask.setInputParameters(workerInputs);
            // Worker must return output.result = agent_name or "DONE"
        } else {
            // LLM-based router (AgentConfig or fallback to parent model)
            ParsedModel routerParsed;
            String routerInstr;
            if (router instanceof AgentConfig routerAgent) {
                routerParsed = ModelParser.parse(routerAgent.getModel());
                AgentCompiler.ResolvedInstructions routerInstructions =
                        resolveInstructionsPlan(
                                routerAgent, toRef(config.getName()) + "_router_instructions");
                preTasks.addAll(routerInstructions.getPreTasks());
                routerInstr = routerInstructions.getText();
            } else {
                routerParsed = parsed;
                routerInstr = parentInstructions.getText();
            }
            String systemPrompt =
                    buildCoordinatorPrompt(config.getName(), routerInstr, agentsInfo, agentNames);
            routerTask = buildIterativeRouterLlm(routerRef, routerParsed, systemPrompt, config);
        }

        // 2a. Normalize the raw router output (LLM or user worker) to a canonical agent name
        // or DONE — the SWITCH, the annotation, and the loop condition read the normalized value.
        String normRef = toRef(config.getName()) + "_route_norm";
        WorkflowTask routeNorm = buildRouteNormalizer(normRef, routerRef, agentNames);

        // 2b. Record routing decision in conversation
        String routeAnnotateRef = toRef(config.getName()) + "_route_annotate";
        WorkflowTask routeAnnotate = new WorkflowTask();
        routeAnnotate.setType("INLINE");
        routeAnnotate.setTaskReferenceName(routeAnnotateRef);
        Map<String, Object> annotateInputs = new LinkedHashMap<>();
        annotateInputs.put("evaluatorType", "graaljs");
        annotateInputs.put(
                "expression",
                "(function() { var d = $.decision; if (d === 'DONE') return $.prev; "
                        + "return $.prev + '\\n\\n[coordinator -> ' + d + ']'; })()");
        annotateInputs.put("prev", "${workflow.variables.conversation}");
        annotateInputs.put("decision", ref(normRef + ".output.result"));
        routeAnnotate.setInputParameters(annotateInputs);

        WorkflowTask routeAnnotateSet = new WorkflowTask();
        routeAnnotateSet.setType("SET_VARIABLE");
        routeAnnotateSet.setTaskReferenceName(toRef(config.getName()) + "_route_set");
        routeAnnotateSet.setInputParameters(
                Map.of("conversation", ref(routeAnnotateRef + ".output.result")));

        // 3. Switch on the normalized router output
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setTaskReferenceName(toRef(config.getName()) + "_switch");
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(Map.of("switchCaseValue", ref(normRef + ".output.result")));

        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        for (int i = 0; i < agents.size(); i++) {
            AgentConfig sub = agents.get(i);
            List<WorkflowTask> caseTasks = buildHandoffCaseTasks(config, sub, i);
            cases.put(sub.getName(), caseTasks);
        }

        // DONE case: no-op
        WorkflowTask doneTask = new WorkflowTask();
        doneTask.setType("INLINE");
        doneTask.setTaskReferenceName(toRef(config.getName()) + "_done_noop");
        doneTask.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "expression", "(function() { return {result: 'done'}; })()"));
        cases.put("DONE", List.of(doneTask));

        switchTask.setDecisionCases(cases);

        // Default case: first agent fallback
        if (!agents.isEmpty()) {
            AgentConfig firstAgent = agents.get(0);
            List<WorkflowTask> defaultTasks =
                    buildHandoffCaseTasks(config, firstAgent, 0, "_default");
            switchTask.setDefaultCase(defaultTasks);
        }

        // 4. DoWhile loop: continue while iteration < max_turns AND normalized decision != DONE
        String termCondition =
                String.format(
                        "if ( $.%s['iteration'] < %d && $.%s['result'] != 'DONE' ) { true; } else { false; }",
                        loopRef, maxTurns, normRef);
        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(normRef, "${" + normRef + "}");
        WorkflowTask loop =
                agentCompiler.buildDoWhile(
                        loopRef,
                        termCondition,
                        List.of(routerTask, routeNorm, routeAnnotate, routeAnnotateSet, switchTask),
                        loopInputs);

        // 5. Final answer LLM
        WorkflowTask finalLlm = new WorkflowTask();
        finalLlm.setName("LLM_CHAT_COMPLETE");
        finalLlm.setTaskReferenceName(toRef(config.getName()) + "_final");
        finalLlm.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> finalInputs = new LinkedHashMap<>();
        finalInputs.put("llmProvider", parsed.getProvider());
        finalInputs.put("model", parsed.getModel());
        finalInputs.put("maxTokens", config.getMaxTokens() != null ? config.getMaxTokens() : 16384);
        String instructions = parentInstructions.getText();
        String finalSystemPrompt =
                (instructions.isEmpty() ? "" : instructions + "\n\n")
                        + "Based on the work done by the agents above, provide your final response to the user. "
                        + "IMPORTANT: Include ALL details from every agent's response — do NOT summarize or omit "
                        + "code examples, technical specifications, or specific recommendations. "
                        + "Organize the information coherently but preserve completeness.";
        finalInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", finalSystemPrompt),
                        Map.of("role", "user", "message", "${workflow.variables.conversation}")));
        finalLlm.setInputParameters(finalInputs);

        preTasks.add(routerCtxResolve);
        preTasks.add(initVar);
        preTasks.add(loop);
        if (config.isSynthesize()) {
            preTasks.add(finalLlm);
        }
        wf.setTasks(preTasks);
        wf.setOutputParameters(
                Map.of(
                        "result",
                        config.isSynthesize()
                                ? ref(toRef(config.getName()) + "_final.output.result")
                                : "${workflow.variables.conversation}",
                        "context",
                        "${workflow.variables._agent_state}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    // ── Round-robin / Random (shared rotation) ──────────────────────

    private WorkflowDef compileRotation(AgentConfig config, boolean random) {
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        String label = random ? "Random" : "Round-Robin";
        wf.setDescription(label + " discussion: " + config.getName());

        int numAgents = config.getAgents().size();
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;

        // 0. Context resolve: INLINE → null-coalesce input.context
        String rotCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask rotCtxResolve = new WorkflowTask();
        rotCtxResolve.setType("INLINE");
        rotCtxResolve.setTaskReferenceName(rotCtxResolveRef);
        rotCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        // 1. Init: seed conversation + context
        WorkflowTask initVar = new WorkflowTask();
        initVar.setType("SET_VARIABLE");
        initVar.setTaskReferenceName(toRef(config.getName()) + "_init");
        Map<String, Object> initInputs = new LinkedHashMap<>();
        String introductions = buildIntroductions(config);
        if (!introductions.isEmpty()) {
            initInputs.put("conversation", introductions + "\n\n${workflow.input.prompt}");
        } else {
            initInputs.put("conversation", "${workflow.input.prompt}");
        }
        if (config.getAllowedTransitions() != null) {
            initInputs.put("last_agent", "0");
        }
        initInputs.put("_agent_state", "${" + rotCtxResolveRef + ".output.result}");
        initVar.setInputParameters(initInputs);

        // 2a. Select agent
        String selectScript = buildSelectScript(config, numAgents, loopRef, random);
        WorkflowTask selectTask = new WorkflowTask();
        selectTask.setType("INLINE");
        selectTask.setTaskReferenceName(toRef(config.getName()) + "_select");
        Map<String, Object> selectInputs = new LinkedHashMap<>();
        selectInputs.put("evaluatorType", "graaljs");
        selectInputs.put("expression", selectScript);
        selectInputs.put("iteration", ref(loopRef + ".output.iteration"));
        if (config.getAllowedTransitions() != null) {
            selectInputs.put("last_agent", "${workflow.variables.last_agent}");
        }
        selectTask.setInputParameters(selectInputs);

        // 2b. Switch to selected agent
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setTaskReferenceName(toRef(config.getName()) + "_switch");
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(
                Map.of("switchCaseValue", ref(toRef(config.getName()) + "_select.output.result")));

        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        for (int i = 0; i < numAgents; i++) {
            AgentConfig sub = config.getAgents().get(i);
            List<WorkflowTask> caseTasks = buildRotationCaseTasks(config, sub, i, loopRef);
            cases.put(String.valueOf(i), caseTasks);
        }
        switchTask.setDecisionCases(cases);

        // 3. Optional stop_when / termination workers
        List<WorkflowTask> loopTasks = new ArrayList<>(List.of(selectTask, switchTask));

        String stopWhenRef = null;
        if (config.getStopWhen() != null) {
            WorkflowTask stopWhenTask =
                    TerminationCompiler.compileStopWhenForConversation(
                            config.getStopWhen().getTaskName(), config.getName(), loopRef);
            loopTasks.add(stopWhenTask);
            stopWhenRef = toRef(config.getName()) + "_stop_when";
        }

        String terminationRef = null;
        if (config.getTermination() != null) {
            WorkflowTask termTask =
                    TerminationCompiler.compileTerminationForConversation(
                            config.getTermination(), config.getName(), loopRef);
            loopTasks.add(termTask);
            terminationRef = toRef(config.getName()) + "_termination";
        }

        // 4. DoWhile loop
        StringBuilder termCondition = new StringBuilder();
        termCondition.append(String.format("if ( $.%s['iteration'] < %d", loopRef, maxTurns));
        if (stopWhenRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", stopWhenRef));
        }
        if (terminationRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", terminationRef));
        }
        termCondition.append(" ) { true; } else { false; }");

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        if (stopWhenRef != null) loopInputs.put(stopWhenRef, "${" + stopWhenRef + "}");
        if (terminationRef != null) loopInputs.put(terminationRef, "${" + terminationRef + "}");

        WorkflowTask loop =
                agentCompiler.buildDoWhile(
                        loopRef, termCondition.toString(), loopTasks, loopInputs);

        wf.setTasks(List.of(rotCtxResolve, initVar, loop));
        wf.setOutputParameters(
                Map.of(
                        "result", "${workflow.variables.conversation}",
                        "context", "${workflow.variables._agent_state}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    // ── Swarm strategy ──────────────────────────────────────────────

    private WorkflowDef compileSwarm(AgentConfig config) {
        validateSwarmHandoffs(config);
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Swarm orchestration: " + config.getName());
        AgentCompiler.ResolvedInstructions instructionsPlan =
                resolveInstructionsPlan(config, toRef(config.getName()) + "_instructions");

        int numAgents = config.getAgents().size();
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;

        // Build allSwarmAgents list (parent + sub-agents) for transfer tool generation
        AgentConfig parentAsAgent =
                AgentConfig.builder()
                        .name(config.getName())
                        .model(config.getModel())
                        .instructions(config.getInstructions())
                        .tools(config.getTools())
                        .guardrails(config.getGuardrails())
                        .memory(config.getMemory())
                        .temperature(config.getTemperature())
                        .maxTokens(config.getMaxTokens())
                        .thinkingConfig(config.getThinkingConfig())
                        .allowedTransitions(config.getAllowedTransitions())
                        .build();

        List<AgentConfig> allSwarmAgents = new ArrayList<>();
        allSwarmAgents.add(parentAsAgent);
        allSwarmAgents.addAll(config.getAgents());

        // 0. Context resolve: INLINE → null-coalesce input.context
        String swarmCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask swarmCtxResolve = new WorkflowTask();
        swarmCtxResolve.setType("INLINE");
        swarmCtxResolve.setTaskReferenceName(swarmCtxResolveRef);
        swarmCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        // 1. Init — track conversation, active_agent, last_response, transfer state, context
        WorkflowTask initVar = new WorkflowTask();
        initVar.setType("SET_VARIABLE");
        initVar.setTaskReferenceName(toRef(config.getName()) + "_init");
        Map<String, Object> initInputs = new LinkedHashMap<>();
        String introductions = buildIntroductions(config);
        initInputs.put(
                "conversation",
                introductions.isEmpty()
                        ? "${workflow.input.prompt}"
                        : introductions + "\n\n${workflow.input.prompt}");
        initInputs.put("active_agent", "0");
        initInputs.put("last_response", "");
        initInputs.put("is_transfer", false);
        initInputs.put("transfer_to", "");
        initInputs.put("_last_tool_results", List.of());
        initInputs.put("_agent_state", "${" + swarmCtxResolveRef + ".output.result}");
        initVar.setInputParameters(initInputs);

        // 2. Switch by active_agent
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setTaskReferenceName(toRef(config.getName()) + "_switch");
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(
                Map.of("switchCaseValue", "${workflow.variables.active_agent}"));

        // Parent agent as case "0", sub-agents shifted to 1, 2, ...
        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        List<ToolConfig> parentTransferTools =
                buildTransferToolsFor(
                        parentAsAgent, allSwarmAgents, config.getAllowedTransitions());
        cases.put("0", buildSwarmCaseTasks(config, parentAsAgent, 0, parentTransferTools));
        for (int i = 0; i < numAgents; i++) {
            AgentConfig sub = config.getAgents().get(i);
            List<ToolConfig> subTransferTools =
                    buildTransferToolsFor(sub, allSwarmAgents, config.getAllowedTransitions());
            List<WorkflowTask> caseTasks =
                    buildSwarmCaseTasks(config, sub, i + 1, subTransferTools);
            cases.put(String.valueOf(i + 1), caseTasks);
        }
        switchTask.setDecisionCases(cases);

        // 3. Handoff resolver. Generated transfer tools are control signals, not workers.  The
        // source case has already merged its context and appended its annotation before this
        // resolver selects the next active agent.
        String handoffRef = toRef(config.getName()) + "_handoff_check";
        WorkflowTask handoffTask = new WorkflowTask();
        handoffTask.setName("INLINE");
        handoffTask.setTaskReferenceName(handoffRef);
        handoffTask.setType("INLINE");
        Map<String, Object> handoffInputs = new LinkedHashMap<>();
        handoffInputs.put("evaluatorType", "graaljs");
        handoffInputs.put("expression", swarmHandoffResolverScript(config, allSwarmAgents));
        handoffInputs.put("result", "${workflow.variables.last_response}");
        handoffInputs.put("active_agent", "${workflow.variables.active_agent}");
        handoffInputs.put("conversation", "${workflow.variables.conversation}");
        handoffInputs.put("is_transfer", "${workflow.variables.is_transfer}");
        handoffInputs.put("transfer_to", "${workflow.variables.transfer_to}");
        handoffInputs.put("tool_results", "${workflow.variables._last_tool_results}");
        List<WorkflowTask> conditionTasks = new ArrayList<>();
        if (config.getHandoffs() != null) {
            for (int i = 0; i < config.getHandoffs().size(); i++) {
                HandoffConfig condition = config.getHandoffs().get(i);
                if (!"on_condition".equals(condition.getType())) continue;
                String conditionRef = toRef(config.getName()) + "_handoff_condition_" + i;
                WorkflowTask conditionTask = new WorkflowTask();
                conditionTask.setName(condition.getTaskName());
                conditionTask.setTaskReferenceName(conditionRef);
                conditionTask.setType("SIMPLE");
                Map<String, Object> conditionInputs = new LinkedHashMap<>();
                conditionInputs.put("result", "${workflow.variables.last_response}");
                conditionInputs.put("conversation", "${workflow.variables.conversation}");
                conditionInputs.put("context", "${workflow.variables._agent_state}");
                conditionInputs.put("active_agent", "${workflow.variables.active_agent}");
                conditionInputs.put("tool_results", "${workflow.variables._last_tool_results}");
                conditionTask.setInputParameters(conditionInputs);
                conditionTasks.add(conditionTask);
                handoffInputs.put("condition_" + i, ref(conditionRef + ".output"));
            }
        }
        handoffTask.setInputParameters(handoffInputs);

        // Update active_agent
        WorkflowTask updateActive = new WorkflowTask();
        updateActive.setType("SET_VARIABLE");
        updateActive.setTaskReferenceName(toRef(config.getName()) + "_update_active");
        updateActive.setInputParameters(
                Map.of("active_agent", ref(handoffRef + ".output.result.active_agent")));

        // 4. Optional stop_when / termination workers
        List<WorkflowTask> loopTasks = new ArrayList<>();
        loopTasks.add(switchTask);
        loopTasks.addAll(conditionTasks);
        loopTasks.add(handoffTask);
        loopTasks.add(updateActive);

        String stopWhenRef = null;
        if (config.getStopWhen() != null) {
            WorkflowTask stopWhenTask =
                    TerminationCompiler.compileStopWhenForConversation(
                            config.getStopWhen().getTaskName(), config.getName(), loopRef);
            loopTasks.add(stopWhenTask);
            stopWhenRef = toRef(config.getName()) + "_stop_when";
        }

        String terminationRef = null;
        if (config.getTermination() != null) {
            WorkflowTask termTask =
                    TerminationCompiler.compileTerminationForConversation(
                            config.getTermination(), config.getName(), loopRef);
            loopTasks.add(termTask);
            terminationRef = toRef(config.getName()) + "_termination";
        }

        // 5. DoWhile — early termination when no handoff triggers
        StringBuilder termCondition = new StringBuilder();
        termCondition.append(
                String.format(
                        "if ( $.%s['iteration'] < %d && $.%s['result'].handoff == true",
                        loopRef, maxTurns, handoffRef));
        if (stopWhenRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", stopWhenRef));
        }
        if (terminationRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", terminationRef));
        }
        termCondition.append(" ) { true; } else { false; }");

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(handoffRef, "${" + handoffRef + ".output}");
        if (stopWhenRef != null) loopInputs.put(stopWhenRef, "${" + stopWhenRef + "}");
        if (terminationRef != null) loopInputs.put(terminationRef, "${" + terminationRef + "}");

        WorkflowTask loop =
                agentCompiler.buildDoWhile(
                        loopRef, termCondition.toString(), loopTasks, loopInputs);

        // 5. Final synthesis LLM: combine all agents' work into a coherent response
        WorkflowTask finalLlm = new WorkflowTask();
        finalLlm.setName("LLM_CHAT_COMPLETE");
        finalLlm.setTaskReferenceName(toRef(config.getName()) + "_final");
        finalLlm.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> finalInputs = new LinkedHashMap<>();
        ParsedModel parsed = ModelParser.parse(config.getModel());
        finalInputs.put("llmProvider", parsed.getProvider());
        finalInputs.put("model", parsed.getModel());
        finalInputs.put("maxTokens", config.getMaxTokens() != null ? config.getMaxTokens() : 16384);
        String instructions = instructionsPlan.getText();
        String finalSystemPrompt =
                (instructions.isEmpty() ? "" : instructions + "\n\n")
                        + "Based on the work done by the agents above, provide your final response to the user. "
                        + "IMPORTANT: Include ALL details from every agent's response — do NOT summarize or omit "
                        + "code examples, technical specifications, or specific recommendations. "
                        + "Organize the information coherently but preserve completeness.";
        finalInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", finalSystemPrompt),
                        Map.of("role", "user", "message", "${workflow.variables.conversation}")));
        finalLlm.setInputParameters(finalInputs);

        List<WorkflowTask> tasks = new ArrayList<>(instructionsPlan.getPreTasks());
        tasks.add(swarmCtxResolve);
        tasks.add(initVar);
        tasks.add(loop);
        if (config.isSynthesize()) {
            tasks.add(finalLlm);
        }
        wf.setTasks(tasks);
        wf.setOutputParameters(
                Map.of(
                        "result",
                        config.isSynthesize()
                                ? ref(toRef(config.getName()) + "_final.output.result")
                                : "${workflow.variables.conversation}",
                        "context",
                        "${workflow.variables._agent_state}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    /** Build transfer_to_<peer> tools for a swarm agent, excluding itself. */
    List<ToolConfig> buildTransferToolsFor(
            AgentConfig self,
            List<AgentConfig> allSwarmAgents,
            Map<String, List<String>> swarmTransitions) {
        List<ToolConfig> transferTools = new ArrayList<>();
        Set<String> declaredToolNames = new HashSet<>();
        if (self.getTools() != null) {
            for (ToolConfig tool : self.getTools()) {
                if (tool.getName() != null) {
                    declaredToolNames.add(tool.getName());
                }
            }
        }
        for (AgentConfig peer : allSwarmAgents) {
            if (peer.getName().equals(self.getName())) continue;
            if (!isTransitionAllowed(self, peer.getName(), swarmTransitions)) continue;
            String transferName = self.getName() + "_transfer_to_" + peer.getName();
            if (declaredToolNames.contains(transferName)) {
                throw new IllegalArgumentException(
                        "SWARM tool name collides with compiler-owned transfer control: "
                                + transferName);
            }
            String peerDesc =
                    peer.getDescription() != null && !peer.getDescription().isEmpty()
                            ? peer.getDescription()
                            : (peer.getInstructions() instanceof String
                                    ? (String) peer.getInstructions()
                                    : "Agent: " + peer.getName());
            ToolConfig transferTool =
                    ToolConfig.builder()
                            .name(transferName)
                            .description(
                                    "Transfer the conversation to "
                                            + peer.getName()
                                            + ". "
                                            + peerDesc
                                            + " Call at most ONE transfer tool per turn.")
                            .inputSchema(
                                    Map.of(
                                            "type",
                                            "object",
                                            "properties",
                                            Map.of(
                                                    "message",
                                                    Map.of(
                                                            "type",
                                                            "string",
                                                            "description",
                                                            "Hand-off note for "
                                                                    + peer.getName()
                                                                    + ": what they should do next and any context they need.")),
                                            "required",
                                            List.of("message")))
                            // This is deliberately not a worker.  The swarm compiler detects this
                            // LLM-visible tool call before normal tool dispatch and uses it as a
                            // routing control signal.
                            .toolType("handoff")
                            .build();
            transferTools.add(transferTool);
        }
        return transferTools;
    }

    private static boolean isTransitionAllowed(
            AgentConfig source, String target, Map<String, List<String>> swarmTransitions) {
        Map<String, List<String>> transitions = source.getAllowedTransitions();
        if ((transitions == null || transitions.isEmpty()) && swarmTransitions != null) {
            transitions = swarmTransitions;
        }
        if (transitions == null || transitions.isEmpty()) {
            return true;
        }
        List<String> allowed = transitions.get(source.getName());
        return allowed != null && allowed.stream().anyMatch(target::equals);
    }

    private static Map<String, String> transferToolNames(List<ToolConfig> transferTools) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (ToolConfig tool : transferTools) {
            String marker = "_transfer_to_";
            int index = tool.getName().indexOf(marker);
            if (index >= 0)
                targets.put(tool.getName(), tool.getName().substring(index + marker.length()));
        }
        return targets;
    }

    /** Validate the declarative SWARM contract without changing its wire representation. */
    private static void validateSwarmHandoffs(AgentConfig config) {
        if (config.getHandoffs() == null) return;
        Set<String> targets = new HashSet<>();
        targets.add(config.getName());
        if (config.getAgents() != null) {
            for (AgentConfig agent : config.getAgents()) targets.add(agent.getName());
        }
        for (HandoffConfig handoff : config.getHandoffs()) {
            if (handoff == null
                    || handoff.getType() == null
                    || !Set.of("on_tool_result", "on_text_mention", "on_condition")
                            .contains(handoff.getType())) {
                throw new IllegalArgumentException(
                        "SWARM handoff type must be on_tool_result, on_text_mention, or on_condition");
            }
            if (handoff.getTarget() == null || !targets.contains(handoff.getTarget())) {
                throw new IllegalArgumentException(
                        "SWARM handoff target must name a swarm agent: " + handoff.getTarget());
            }
            switch (handoff.getType()) {
                case "on_tool_result" -> {
                    if (isBlank(handoff.getToolName()) || isBlank(handoff.getResultContains())) {
                        throw new IllegalArgumentException(
                                "on_tool_result requires toolName and resultContains");
                    }
                }
                case "on_text_mention" -> {
                    if (isBlank(handoff.getText())) {
                        throw new IllegalArgumentException("on_text_mention requires text");
                    }
                }
                case "on_condition" -> {
                    if (isBlank(handoff.getTaskName())) {
                        throw new IllegalArgumentException(
                                "on_condition requires a nonblank taskName");
                    }
                }
                default -> throw new IllegalStateException("validated above");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * First-wins handoff resolver. Explicit model transfers take precedence; declarative text and
     * tool-result matches then follow configuration order. The returned map is consumed by the
     * outer loop after the source case has made the transcript/state durable.
     */
    private static String swarmHandoffResolverScript(
            AgentConfig config, List<AgentConfig> allAgents) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < allAgents.size(); i++) indexes.put(allAgents.get(i).getName(), i);
        List<Map<String, Object>> rules = new ArrayList<>();
        if (config.getHandoffs() != null) {
            for (HandoffConfig h : config.getHandoffs()) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("type", h.getType());
                rule.put("target", h.getTarget());
                rule.put("toolName", h.getToolName());
                rule.put("resultContains", h.getResultContains());
                rule.put("text", h.getText());
                rule.put("index", rules.size());
                rules.add(rule);
            }
        }
        return JavaScriptBuilder.iife(
                "var indexes="
                        + JavaScriptBuilder.toJson(indexes)
                        + ";"
                        + "var rules="
                        + JavaScriptBuilder.toJson(rules)
                        + ";"
                        + "var active=$.active_agent == null ? '0' : String($.active_agent);"
                        + "var has=function(v){return v!==null&&v!==undefined&&String(v)!=='';};"
                        // Tool outputs can be text, objects, arrays, or null. Workflow outputs
                        // are often Java Map/List proxies: JSON.stringify(proxy) becomes "{}",
                        // while String(proxy) preserves its values (for example
                        // {customerName=Alice}). Native JavaScript values still use JSON.
                        + "var text=function(v){if(v==null)return '';if(typeof v==='string')return v;"
                        + "if(v.get||v.entrySet)return String(v);"
                        + "try{return JSON.stringify(v);}catch(e){return String(v);}};"
                        + "if (($.is_transfer===true||$.is_transfer==='true') && has($.transfer_to)"
                        + " && indexes[String($.transfer_to)]!==undefined) return {handoff:true,active_agent:String(indexes[String($.transfer_to)])};"
                        + "var result=$.result==null?'':String($.result).toLowerCase();"
                        + "var trs=$.tool_results||[];"
                        // Workflow variables arrive as Java List proxies in GraalJS. A direct
                        // `trs.length` only works for native arrays, silently skipping every
                        // successful JOIN output and preventing on_tool_result handoffs.
                        + "var trCount=trs.size?trs.size():(trs.length||0);"
                        + "for(var i=0;i<rules.length;i++){var r=rules[i]; var hit=false;"
                        + " if(r.type==='on_text_mention') hit=result.indexOf(String(r.text).toLowerCase())>=0;"
                        + " else if(r.type==='on_tool_result'){for(var j=0;j<trCount;j++){var x=(trs.get?trs.get(j):trs[j])||{};"
                        + " var n=x.get?x.get('name'):x.name; var o=x.get?x.get('output'):x.output;"
                        + " if(String(n)===String(r.toolName)&&text(o).toLowerCase().indexOf(String(r.resultContains).toLowerCase())>=0){hit=true;break;}}}"
                        + " else if(r.type==='on_condition'){var c=$['condition_'+r.index];"
                        + " var h=c&&(c.get?c.get('handoff'):c.handoff); if(typeof h!=='boolean') throw 'Invalid on_condition output: handoff must be boolean'; hit=h;}"
                        + " if(hit) return {handoff:true,active_agent:String(indexes[r.target])};}"
                        // A completed turn without a matching transfer or declarative rule is a
                        // terminal swarm result.  Returning handoff=true here would re-run the
                        // same active agent until the max-turn guard is exhausted.
                        + "return {handoff:false,active_agent:active};");
    }

    /**
     * Compile a single swarm agent into a SUB_WORKFLOW with transfer detection.
     *
     * <p>The inner workflow contains: init_state → DO_WHILE(llm, tool_router, check_transfer) and
     * outputs {result, finishReason, is_transfer, transfer_to}.
     */
    WorkflowDef compileSwarmAgentWorkflow(AgentConfig agent, List<ToolConfig> transferTools) {
        return compileSwarmAgentWorkflow(agent, transferTools, false);
    }

    /**
     * Compiles a child used by the SWARM coordinator.
     *
     * <p>When the parent has an {@code on_tool_result} rule, a completed dynamic-tool JOIN must
     * return control to the coordinator before the child opens another LLM turn. The coordinator
     * owns the declared handoff rules and has the durable merged tool results required to select
     * the next agent. Continuing the child loop first would hide the result behind an unnecessary
     * source-model turn and allow that turn to change the routing signal.
     */
    private WorkflowDef compileSwarmAgentWorkflow(
            AgentConfig agent, List<ToolConfig> transferTools, boolean returnAfterToolResults) {
        // Claude Code agents use passthrough — no LLM loop, just a single SIMPLE task
        if (agent.getModel() != null && agent.getModel().startsWith("claude-code")) {
            // Ensure the passthrough worker tool is set
            if (agent.getTools() == null || agent.getTools().isEmpty()) {
                agent.setTools(
                        List.of(
                                ToolConfig.builder()
                                        .name(agent.getName())
                                        .description("Claude Agent SDK passthrough worker")
                                        .toolType("worker")
                                        .build()));
            }
            if (agent.getMetadata() == null) agent.setMetadata(new LinkedHashMap<>());
            agent.getMetadata().put("_framework_passthrough", true);
            return agentCompiler.compileFrameworkPassthrough(agent);
        }

        boolean hasSubAgents = agent.getAgents() != null && !agent.getAgents().isEmpty();

        if (hasSubAgents) {
            // Agent has its own strategy (handoff, sequential, etc.)
            // Compile it normally to preserve its multi-agent behavior,
            // then wrap with transfer detection
            return compileSwarmAgentWorkflowWithSubAgents(agent, transferTools);
        }

        // Original flat path for simple/tool-calling agents
        ParsedModel parsed = ModelParser.parse(agent.getModel());
        String llmRef = agent.getName() + "_llm";
        String checkTransferRef = agent.getName() + "_check_transfer";

        // Merge agent's own tools with transfer tools
        List<ToolConfig> allTools = new ArrayList<>();
        if (agent.getTools() != null) {
            allTools.addAll(agent.getTools());
        }
        allTools.addAll(transferTools);

        ToolCompiler tc = new ToolCompiler();
        boolean hasApproval = allTools.stream().anyMatch(ToolConfig::isApprovalRequired);
        List<Map<String, Object>> toolSpecs = tc.compileToolSpecs(allTools);

        // LLM task
        WorkflowTask llmTask = agentCompiler.buildLlmTask(agent, parsed, llmRef, toolSpecs);

        // Detect a transfer before ordinary tool dispatch. This is compiler-owned routing logic;
        // transfer tools never become dynamically forked SIMPLE tasks.
        WorkflowTask checkTransferTask = new WorkflowTask();
        checkTransferTask.setName("INLINE");
        checkTransferTask.setTaskReferenceName(checkTransferRef);
        checkTransferTask.setType("INLINE");
        checkTransferTask.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "tool_calls", ref(llmRef + ".output.toolCalls"),
                        "expression",
                                JavaScriptBuilder.detectTransferScript(
                                        transferToolNames(transferTools))));

        // Tool call routing (the transfer detector makes this a no-op for a transfer turn).
        WorkflowTask toolRouter =
                tc.buildToolCallRouting(
                        agent.getName(), llmRef, allTools, hasApproval, agent.getModel());
        toolRouter
                .getInputParameters()
                .put("isTransfer", ref(checkTransferRef + ".output.result.is_transfer"));
        toolRouter.setExpression(
                "$.isTransfer == true ? 'none' : ($.toolCalls != null && $.toolCalls.length > 0 ? 'tool_call' : 'none')");

        // DoWhile loop: continue while tool calls present and no transfer
        String loopRef = agent.getName() + "_loop";
        int maxTurns = agent.getMaxTurns() > 0 ? agent.getMaxTurns() : 100;
        String hasToolCalls =
                String.format(
                        "($.%s['toolCalls'] != null && $.%s['toolCalls'].length > 0)",
                        llmRef, llmRef);
        String notTransfer = String.format("($.%s.result.is_transfer != true)", checkTransferRef);
        String continueAfterToolCalls =
                returnAfterToolResults ? "false" : "(" + hasToolCalls + " && " + notTransfer + ")";
        String termCondition =
                String.format(
                        "if ( $.%s['iteration'] < %d && ($.%s['finishReason'] == 'LENGTH' || $.%s['finishReason'] == 'MAX_TOKENS' || %s) ) { true; } else { false; }",
                        loopRef, maxTurns, llmRef, llmRef, continueAfterToolCalls);

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        loopInputs.put(llmRef, "${" + llmRef + "}");
        loopInputs.put(checkTransferRef, "${" + checkTransferRef + "}");
        WorkflowTask loop =
                agentCompiler.buildDoWhile(
                        loopRef,
                        termCondition,
                        List.of(llmTask, checkTransferTask, toolRouter),
                        loopInputs);

        // Initialize _agent_state for ToolContext.state from the caller-provided context
        // (the swarm parent passes ${workflow.variables._agent_state}) — null-coalesced so a
        // standalone run without context still starts from {}.
        String ctxResolveRef = agent.getName() + "_ctx_resolve";
        WorkflowTask ctxResolve = new WorkflowTask();
        ctxResolve.setType("INLINE");
        ctxResolve.setTaskReferenceName(ctxResolveRef);
        ctxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        WorkflowTask initState = new WorkflowTask();
        initState.setType("SET_VARIABLE");
        initState.setTaskReferenceName(agent.getName() + "_init_state");
        // Keep the same state contract as a standalone/hybrid agent. In particular, dynamic
        // tool JOINs append normalized {name, output} values to _last_tool_results; without an
        // initialized list, a missing template can resolve to its parent map and the SWARM parent
        // cannot evaluate an on_tool_result handoff from the child's durable output.
        initState.setInputParameters(
                Map.of(
                        "_agent_state",
                        "${" + ctxResolveRef + ".output.result}",
                        "_last_tool_results",
                        List.of()));

        // Extract the transfer message from the last LLM turn's tool calls so the parent can
        // record the delegation intent in the conversation (the check_transfer worker only
        // returns {is_transfer, transfer_to}).
        String transferMsgRef = agent.getName() + "_transfer_msg";
        WorkflowTask transferMsg = new WorkflowTask();
        transferMsg.setType("INLINE");
        transferMsg.setTaskReferenceName(transferMsgRef);
        transferMsg.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "tool_calls", ref(llmRef + ".output.toolCalls"),
                        "expression",
                                JavaScriptBuilder.extractTransferMessageScript(
                                        transferToolNames(transferTools))));

        // Build the sub-workflow
        WorkflowDef subWf = agentCompiler.createWorkflow(agent);
        subWf.setName(agent.getName() + "_swarm_wf");
        subWf.setDescription("Swarm agent: " + agent.getName());
        subWf.setTasks(List.of(ctxResolve, initState, loop, transferMsg));
        Map<String, Object> swarmAgentOutputs = new LinkedHashMap<>();
        swarmAgentOutputs.put("result", ref(llmRef + ".output.result"));
        swarmAgentOutputs.put("finishReason", ref(llmRef + ".output.finishReason"));
        swarmAgentOutputs.put("is_transfer", ref(checkTransferRef + ".output.result.is_transfer"));
        swarmAgentOutputs.put("transfer_to", ref(checkTransferRef + ".output.result.transfer_to"));
        swarmAgentOutputs.put("transfer_message", ref(transferMsgRef + ".output.result"));
        // Round-trip structured state: tools merge into _agent_state during the loop, and the
        // swarm parent merges this back into its own _agent_state after each turn.
        swarmAgentOutputs.put("context", "${workflow.variables._agent_state}");
        swarmAgentOutputs.put("tool_results", "${workflow.variables._last_tool_results}");
        subWf.setOutputParameters(swarmAgentOutputs);
        // Backfill task.name on system tasks (SET_VARIABLE, DO_WHILE, INLINE)
        // so Conductor's WorkflowSweeper doesn't trip on "TaskDef name cannot
        // be null" when the SUB_WORKFLOW executes — the outer compile-pass
        // doesn't recurse into SubWorkflowParam.workflowDefinition, so each
        // embedding compiler owns that pass for its own sub-workflows.
        WorkflowTaskUtils.ensureAllTaskNames(subWf);
        agentCompiler.stampAgentMetadata(subWf, agent);
        return subWf;
    }

    /**
     * Compile a swarm agent that has its own sub-agents (hierarchical). The agent's strategy
     * (handoff, sequential, etc.) is preserved via normal compilation, then wrapped with transfer
     * detection logic.
     */
    private WorkflowDef compileSwarmAgentWorkflowWithSubAgents(
            AgentConfig agent, List<ToolConfig> transferTools) {
        ParsedModel parsed = ModelParser.parse(agent.getModel());
        String innerRef = agent.getName() + "_inner";
        String transferLlmRef = agent.getName() + "_transfer_llm";
        String checkTransferRef = agent.getName() + "_check_transfer";

        // 1. Compile the agent normally to preserve its multi-agent strategy.
        WorkflowDef innerWf = agentCompiler.compile(agent);

        // Inner agent as SUB_WORKFLOW
        WorkflowTask innerTask = new WorkflowTask();
        innerTask.setType("SUB_WORKFLOW");
        innerTask.setName(agent.getName() + "_strategy");
        innerTask.setTaskReferenceName(innerRef);
        innerTask.setSubWorkflowParam(new SubWorkflowParams());
        innerTask.getSubWorkflowParam().setName(innerWf.getName());
        WorkflowTaskUtils.ensureAllTaskNames(innerWf);
        innerTask.getSubWorkflowParam().setWorkflowDef(innerWf);
        Map<String, Object> innerInputs = new LinkedHashMap<>();
        innerInputs.put("prompt", "${workflow.input.prompt}");
        innerInputs.put("media", "${workflow.input.media}");
        innerInputs.put("session_id", "${workflow.input.session_id}");
        // Forward the swarm's accumulated _agent_state so the inner strategy is not stateless
        // across swarm turns.
        innerInputs.put("context", "${workflow.input.context}");
        innerTask.setInputParameters(innerInputs);

        // 2. Coerce inner result to string (may be array/null when last turn was tool calls)
        String coerceRef = agent.getName() + "_coerce_result";
        WorkflowTask coerceTask =
                AgentCompiler.createCoerceTask(ref(innerRef + ".output.result"), coerceRef);
        String coercedResultRef = AgentCompiler.coercedRef(coerceRef);

        // 3. LLM step with transfer tools to decide whether to transfer to a peer
        ToolCompiler tc = new ToolCompiler();
        List<Map<String, Object>> transferToolSpecs = tc.compileToolSpecs(transferTools);

        WorkflowTask transferLlm = new WorkflowTask();
        transferLlm.setName("LLM_CHAT_COMPLETE");
        transferLlm.setTaskReferenceName(transferLlmRef);
        transferLlm.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> llmInputs = new LinkedHashMap<>();
        llmInputs.put("llmProvider", parsed.getProvider());
        llmInputs.put("model", parsed.getModel());
        llmInputs.put("maxTokens", agent.getMaxTokens() != null ? agent.getMaxTokens() : 16384);
        String transferPrompt =
                "You have just completed your task. Your result is shown above.\n\n"
                        + "If another agent should handle a different part of the request, call the appropriate "
                        + "transfer tool — at most ONE — and use its `message` argument to tell the receiving "
                        + "agent exactly what to do next. Otherwise, do NOT call any tool — just respond with "
                        + "a brief acknowledgment.";
        llmInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", transferPrompt),
                        Map.of("role", "user", "message", "${workflow.input.prompt}"),
                        Map.of("role", "assistant", "message", coercedResultRef)));
        if (!transferToolSpecs.isEmpty()) {
            llmInputs.put("tools", transferToolSpecs);
        }
        transferLlm.setInputParameters(llmInputs);

        // 4. Compiler-owned transfer detection; no generated SIMPLE worker is required.
        WorkflowTask checkTransferTask = new WorkflowTask();
        checkTransferTask.setName("INLINE");
        checkTransferTask.setTaskReferenceName(checkTransferRef);
        checkTransferTask.setType("INLINE");
        checkTransferTask.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "tool_calls", ref(transferLlmRef + ".output.toolCalls"),
                        "expression",
                                JavaScriptBuilder.detectTransferScript(
                                        transferToolNames(transferTools))));

        // 5. Extract the transfer message from the transfer LLM's tool calls so the parent can
        // record the delegation intent in the conversation.
        String transferMsgRef = agent.getName() + "_transfer_msg";
        WorkflowTask transferMsg = new WorkflowTask();
        transferMsg.setType("INLINE");
        transferMsg.setTaskReferenceName(transferMsgRef);
        transferMsg.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "tool_calls", ref(transferLlmRef + ".output.toolCalls"),
                        "expression",
                                JavaScriptBuilder.extractTransferMessageScript(
                                        transferToolNames(transferTools))));

        // Build the wrapper sub-workflow
        WorkflowDef subWf = agentCompiler.createWorkflow(agent);
        subWf.setName(agent.getName() + "_swarm_wf");
        subWf.setDescription("Swarm hierarchical agent: " + agent.getName());
        subWf.setTasks(List.of(innerTask, coerceTask, transferLlm, checkTransferTask, transferMsg));
        Map<String, Object> hierOutputs = new LinkedHashMap<>();
        hierOutputs.put("result", ref(innerRef + ".output.result"));
        hierOutputs.put("finishReason", "stop");
        hierOutputs.put("is_transfer", ref(checkTransferRef + ".output.result.is_transfer"));
        hierOutputs.put("transfer_to", ref(checkTransferRef + ".output.result.transfer_to"));
        hierOutputs.put("transfer_message", ref(transferMsgRef + ".output.result"));
        // Round-trip structured state: every inner strategy outputs `context`; without this the
        // swarm parent's _agent_state merge always sees null and state never accumulates.
        hierOutputs.put("context", ref(innerRef + ".output.context"));
        hierOutputs.put("tool_results", ref(innerRef + ".output.tool_results"));
        subWf.setOutputParameters(hierOutputs);
        // See compileSwarmAgentWorkflow above — backfill task names so the
        // embedded SUB_WORKFLOW passes Conductor's null-name validation.
        WorkflowTaskUtils.ensureAllTaskNames(subWf);
        agentCompiler.stampAgentMetadata(subWf, agent);
        return subWf;
    }

    // ── Manual strategy ─────────────────────────────────────────────

    private WorkflowDef compileManual(AgentConfig config) {
        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Manual selection: " + config.getName());

        int numAgents = config.getAgents().size();
        String loopRef = toRef(config.getName()) + "_loop";
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;

        // 0. Context resolve: INLINE → null-coalesce input.context
        String manCtxResolveRef = toRef(config.getName()) + "_ctx_resolve";
        WorkflowTask manCtxResolve = new WorkflowTask();
        manCtxResolve.setType("INLINE");
        manCtxResolve.setTaskReferenceName(manCtxResolveRef);
        manCtxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));

        // 1. Init
        WorkflowTask initVar = new WorkflowTask();
        initVar.setType("SET_VARIABLE");
        initVar.setTaskReferenceName(toRef(config.getName()) + "_init");
        Map<String, Object> initInputs = new LinkedHashMap<>();
        String introductions = buildIntroductions(config);
        initInputs.put(
                "conversation",
                introductions.isEmpty()
                        ? "${workflow.input.prompt}"
                        : introductions + "\n\n${workflow.input.prompt}");
        initInputs.put("_agent_state", "${" + manCtxResolveRef + ".output.result}");
        initVar.setInputParameters(initInputs);

        // 2. HumanTask
        String humanRef = toRef(config.getName()) + "_pick_agent";
        Map<String, String> agentOptions = new LinkedHashMap<>();
        List<String> agentNames = new ArrayList<>();
        for (int i = 0; i < config.getAgents().size(); i++) {
            String name = config.getAgents().get(i).getName();
            agentOptions.put(name, String.valueOf(i));
            agentNames.add(name);
        }

        // Response schema: human must pick one of the available agent names.
        // Without this schema, response_schema is absent from pendingTool and
        // the Python/Java CLI handler never prompts the human — defaulting to
        // the first agent every time.
        Map<String, Object> selectedProp = new LinkedHashMap<>();
        selectedProp.put("type", "string");
        selectedProp.put("title", "Select Agent");
        selectedProp.put(
                "description",
                "Choose which agent should respond next: " + String.join(", ", agentNames));
        selectedProp.put("enum", agentNames);
        Map<String, Object> responseSchema = new LinkedHashMap<>();
        responseSchema.put("type", "object");
        responseSchema.put("required", List.of("selected"));
        responseSchema.put("properties", Map.of("selected", selectedProp));
        Map<String, Object> responseUiSchema = new LinkedHashMap<>();
        responseUiSchema.put("ui:order", List.of("selected"));
        responseUiSchema.put("selected", Map.of("ui:widget", "select"));

        HumanTaskBuilder.Pipeline humanPipeline =
                HumanTaskBuilder.create(humanRef, config.getName() + ": Select next agent")
                        .contextInput("agent_options", agentOptions)
                        .contextInput("conversation", "${workflow.variables.conversation}")
                        .responseSchema(responseSchema)
                        .responseUiSchema(responseUiSchema)
                        .build();
        WorkflowTask humanTask = humanPipeline.getTasks().get(0);

        // Process selection worker
        String processRef = toRef(config.getName()) + "_process_selection";
        WorkflowTask processTask = new WorkflowTask();
        processTask.setName(toRef(config.getName()) + "_process_selection");
        processTask.setTaskReferenceName(processRef);
        processTask.setType("SIMPLE");
        processTask.setInputParameters(Map.of("human_output", ref(humanRef + ".output")));

        // 3. Switch to selected agent
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType("SWITCH");
        switchTask.setTaskReferenceName(toRef(config.getName()) + "_switch");
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.setInputParameters(
                Map.of("switchCaseValue", ref(processRef + ".output.selected")));

        Map<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        for (int i = 0; i < numAgents; i++) {
            AgentConfig sub = config.getAgents().get(i);
            List<WorkflowTask> caseTasks = buildRotationCaseTasks(config, sub, i, loopRef);
            cases.put(String.valueOf(i), caseTasks);
        }
        switchTask.setDecisionCases(cases);

        // 4. Optional stop_when / termination workers
        List<WorkflowTask> loopTasks = new ArrayList<>(List.of(humanTask, processTask, switchTask));

        String stopWhenRef = null;
        if (config.getStopWhen() != null) {
            WorkflowTask stopWhenTask =
                    TerminationCompiler.compileStopWhenForConversation(
                            config.getStopWhen().getTaskName(), config.getName(), loopRef);
            loopTasks.add(stopWhenTask);
            stopWhenRef = toRef(config.getName()) + "_stop_when";
        }

        String terminationRef = null;
        if (config.getTermination() != null) {
            WorkflowTask termTask =
                    TerminationCompiler.compileTerminationForConversation(
                            config.getTermination(), config.getName(), loopRef);
            loopTasks.add(termTask);
            terminationRef = toRef(config.getName()) + "_termination";
        }

        // 5. DoWhile
        StringBuilder termCondition = new StringBuilder();
        termCondition.append(String.format("if ( $.%s['iteration'] < %d", loopRef, maxTurns));
        if (stopWhenRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", stopWhenRef));
        }
        if (terminationRef != null) {
            termCondition.append(String.format(" && $.%s.should_continue == true", terminationRef));
        }
        termCondition.append(" ) { true; } else { false; }");

        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(loopRef, "${" + loopRef + "}");
        if (stopWhenRef != null) loopInputs.put(stopWhenRef, "${" + stopWhenRef + "}");
        if (terminationRef != null) loopInputs.put(terminationRef, "${" + terminationRef + "}");

        WorkflowTask loop =
                agentCompiler.buildDoWhile(
                        loopRef, termCondition.toString(), loopTasks, loopInputs);

        wf.setTasks(List.of(manCtxResolve, initVar, loop));
        wf.setOutputParameters(
                Map.of(
                        "result", "${workflow.variables.conversation}",
                        "context", "${workflow.variables._agent_state}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    // ── Guardrail wrapping ──────────────────────────────────────────

    private WorkflowDef wrapWithGuardrails(AgentConfig config, WorkflowDef strategyWf) {
        String subRef = toRef(config.getName()) + "_strategy";

        // Run strategy as inline sub-workflow
        WorkflowTask subTask = new WorkflowTask();
        subTask.setType("SUB_WORKFLOW");
        subTask.setTaskReferenceName(subRef);
        subTask.setSubWorkflowParam(new SubWorkflowParams());
        subTask.getSubWorkflowParam().setName(strategyWf.getName());
        WorkflowTaskUtils.ensureAllTaskNames(strategyWf);
        subTask.getSubWorkflowParam().setWorkflowDef(strategyWf);
        Map<String, Object> subInputs = new LinkedHashMap<>();
        subInputs.put("prompt", "${workflow.input.prompt}");
        subInputs.put("media", "${workflow.input.media}");
        subInputs.put("session_id", "${workflow.input.session_id}");
        subTask.setInputParameters(subInputs);

        String contentRef = ref(subRef + ".output.result");

        GuardrailCompiler gc = new GuardrailCompiler();
        List<GuardrailConfig> outputGuardrails = agentCompiler.getOutputGuardrails(config);
        List<GuardrailCompiler.GuardrailTaskResult> guardrailResults =
                gc.compileGuardrailTasks(outputGuardrails, config.getName(), contentRef);

        List<WorkflowTask> loopTasks = new ArrayList<>();
        loopTasks.add(subTask);

        List<String[]> guardrailRefs = new ArrayList<>();
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
                            gr.isInline());
            loopTasks.addAll(gr.getTasks());
            loopTasks.add(routing.getSwitchTask());
            guardrailRefs.add(new String[] {gr.getRefName(), String.valueOf(gr.isInline())});
        }

        String guardrailContinue = agentCompiler.buildGuardrailContinue(guardrailRefs);
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 25;
        String loopCondition =
                String.format(
                        "if ( $.%s_guardrail_loop['iteration'] < %d && (%s) ) { true; } else { false; }",
                        config.getName(), maxTurns, guardrailContinue);

        String guardrailLoopRef = toRef(config.getName()) + "_guardrail_loop";
        Map<String, Object> loopInputs = new LinkedHashMap<>();
        loopInputs.put(guardrailLoopRef, "${" + guardrailLoopRef + "}");
        agentCompiler.addGuardrailInputs(loopInputs, guardrailRefs);
        WorkflowTask doWhile =
                agentCompiler.buildDoWhile(guardrailLoopRef, loopCondition, loopTasks, loopInputs);

        WorkflowDef outerWf = agentCompiler.createWorkflow(config);
        outerWf.setTasks(List.of(doWhile));
        outerWf.setOutputParameters(Map.of("result", contentRef));
        return outerWf;
    }

    // ── Shared helpers ──────────────────────────────────────────────

    private List<WorkflowTask> buildRotationCaseTasks(
            AgentConfig parent, AgentConfig sub, int idx, String loopRef) {
        List<WorkflowTask> caseTasks = new ArrayList<>();
        String subRef = parent.getName() + "_agent_" + idx + "_" + sub.getName();

        WorkflowTask task =
                agentCompiler.compileSubAgent(
                        sub,
                        subRef,
                        "${workflow.variables.conversation}",
                        "${workflow.input.media}",
                        "${workflow.variables._agent_state}");
        caseTasks.add(task);

        // Concat
        String responseRef = AgentCompiler.subAgentResultRef(sub, subRef);
        WorkflowTask concatTask = new WorkflowTask();
        concatTask.setType("INLINE");
        concatTask.setTaskReferenceName(parent.getName() + "_concat_" + idx);
        Map<String, Object> concatInputs = new LinkedHashMap<>();
        concatInputs.put("evaluatorType", "graaljs");
        concatInputs.put("expression", JavaScriptBuilder.concatScript(sub.getName()));
        concatInputs.put("prev", "${workflow.variables.conversation}");
        concatInputs.put("response", responseRef);
        concatTask.setInputParameters(concatInputs);
        caseTasks.add(concatTask);

        // Merge child context back into _agent_state
        String rCtxMergeRef = parent.getName() + "_rctx_merge_" + idx;
        WorkflowTask rCtxMerge = new WorkflowTask();
        rCtxMerge.setType("INLINE");
        rCtxMerge.setTaskReferenceName(rCtxMergeRef);
        rCtxMerge.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "parent",
                        "${workflow.variables._agent_state}",
                        "child",
                        "${" + subRef + ".output.context}",
                        "expression",
                        JavaScriptBuilder.flatMergeContextScript()));
        caseTasks.add(rCtxMerge);

        // SetVariable — persist conversation + merged context
        WorkflowTask setVar = new WorkflowTask();
        setVar.setType("SET_VARIABLE");
        setVar.setTaskReferenceName(parent.getName() + "_set_" + idx);
        Map<String, Object> setInputs = new LinkedHashMap<>();
        setInputs.put("conversation", ref(parent.getName() + "_concat_" + idx + ".output.result"));
        if (parent.getAllowedTransitions() != null) {
            setInputs.put("last_agent", String.valueOf(idx));
        }
        setInputs.put("_agent_state", "${" + rCtxMergeRef + ".output.result}");
        setVar.setInputParameters(setInputs);
        caseTasks.add(setVar);

        return caseTasks;
    }

    private List<WorkflowTask> buildSwarmCaseTasks(
            AgentConfig parent, AgentConfig sub, int idx, List<ToolConfig> transferTools) {
        List<WorkflowTask> caseTasks = new ArrayList<>();
        String subRef = parent.getName() + "_agent_" + idx + "_" + sub.getName();

        // Compile as SUB_WORKFLOW with inline transfer-aware workflow
        boolean returnAfterToolResults =
                parent.getHandoffs() != null
                        && parent.getHandoffs().stream()
                                .anyMatch(
                                        handoff ->
                                                handoff != null
                                                        && "on_tool_result"
                                                                .equals(handoff.getType()));
        WorkflowDef agentWf = compileSwarmAgentWorkflow(sub, transferTools, returnAfterToolResults);
        WorkflowTask task = new WorkflowTask();
        task.setType("SUB_WORKFLOW");
        task.setName(sub.getName());
        task.setTaskReferenceName(subRef);
        task.setSubWorkflowParam(new SubWorkflowParams());
        task.getSubWorkflowParam().setName(agentWf.getName());
        task.getSubWorkflowParam().setWorkflowDef(agentWf);
        Map<String, Object> subInputs = new LinkedHashMap<>();
        subInputs.put("prompt", "${workflow.variables.conversation}");
        subInputs.put("media", "${workflow.input.media}");
        subInputs.put("session_id", "${workflow.input.session_id}");
        subInputs.put("context", "${workflow.variables._agent_state}");
        task.setInputParameters(subInputs);
        caseTasks.add(task);

        // Concat response to conversation — swarm-aware: tool-call-only turns ([]/{}) add
        // nothing, and handoffs are annotated as [agent -> target]: <transfer message>.
        String responseRef = ref(subRef + ".output.result");
        WorkflowTask concatTask = new WorkflowTask();
        concatTask.setType("INLINE");
        concatTask.setTaskReferenceName(parent.getName() + "_concat_" + idx);
        Map<String, Object> concatInputs = new LinkedHashMap<>();
        concatInputs.put("evaluatorType", "graaljs");
        concatInputs.put("expression", JavaScriptBuilder.swarmConcatScript(sub.getName()));
        concatInputs.put("prev", "${workflow.variables.conversation}");
        concatInputs.put("response", responseRef);
        concatInputs.put("is_transfer", ref(subRef + ".output.is_transfer"));
        concatInputs.put("transfer_to", ref(subRef + ".output.transfer_to"));
        concatInputs.put("transfer_message", ref(subRef + ".output.transfer_message"));
        concatTask.setInputParameters(concatInputs);
        caseTasks.add(concatTask);

        // Merge child context back into _agent_state
        String sCtxMergeRef = parent.getName() + "_sctx_merge_" + idx;
        WorkflowTask sCtxMerge = new WorkflowTask();
        sCtxMerge.setType("INLINE");
        sCtxMerge.setTaskReferenceName(sCtxMergeRef);
        sCtxMerge.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "parent",
                        "${workflow.variables._agent_state}",
                        "child",
                        "${" + subRef + ".output.context}",
                        "expression",
                        JavaScriptBuilder.flatMergeContextScript()));
        caseTasks.add(sCtxMerge);

        // SetVariable — set conversation, last_response, transfer state, and merged context
        String concatRef = parent.getName() + "_concat_" + idx;
        WorkflowTask setVar = new WorkflowTask();
        setVar.setType("SET_VARIABLE");
        setVar.setTaskReferenceName(parent.getName() + "_set_" + idx);
        Map<String, Object> setInputs = new LinkedHashMap<>();
        setInputs.put("conversation", ref(concatRef + ".output.result"));
        setInputs.put("last_response", responseRef);
        setInputs.put("is_transfer", ref(subRef + ".output.is_transfer"));
        setInputs.put("transfer_to", ref(subRef + ".output.transfer_to"));
        setInputs.put("_last_tool_results", ref(subRef + ".output.tool_results"));
        setInputs.put("_agent_state", "${" + sCtxMergeRef + ".output.result}");
        setVar.setInputParameters(setInputs);
        caseTasks.add(setVar);

        return caseTasks;
    }

    /**
     * Reject sub-agent names that collide with the coordinator's reserved {@code DONE} decision. An
     * agent named "done" (any case) would clobber the DONE switch case and make the coordinator's
     * completion signal unreachable by construction.
     */
    private void rejectReservedAgentNames(AgentConfig config, List<AgentConfig> agents) {
        for (AgentConfig a : agents) {
            if ("done".equalsIgnoreCase(a.getName())) {
                throw new IllegalArgumentException(
                        "Sub-agent name '"
                                + a.getName()
                                + "' in '"
                                + config.getName()
                                + "' is reserved: the coordinator uses DONE as its completion "
                                + "signal. Rename the agent.");
            }
        }
    }

    /**
     * Build the coordinator routing system prompt shared by the handoff and router strategies.
     *
     * <p>Scope-awareness matters when the team is nested (e.g. a handoff team inside a swarm): the
     * conversation seeded into the team contains the FULL original request, but the team may have
     * been delegated only a slice of it (recorded as a {@code [<sender> -> <teamName>]: <note>}
     * line by the swarm/handoff concat scripts). Without the scope rules below, a part of the
     * request that no team member can handle makes DONE unreachable — the coordinator is forced to
     * keep delegating forever (observed live: an engineering team re-delegating a finished API
     * design because the request also asked for a marketing campaign it had no agent for).
     */
    private String buildCoordinatorPrompt(
            String teamName,
            String instructions,
            StringBuilder agentsInfo,
            List<String> agentNames) {
        return (instructions.isEmpty() ? "" : instructions + "\n\n")
                + "You are a coordinator that delegates tasks to specialized agents.\n\n"
                + "Available agents:\n"
                + agentsInfo
                + "\nScope of your responsibility:\n"
                + "- You may have been delegated only PART of a larger request. If the conversation "
                + "contains a delegation note addressed to your team '"
                + teamName
                + "' — a line like '[<sender> -> "
                + teamName
                + "]: <instructions>' — judge completion against the MOST RECENT such note's "
                + "instructions, not the entire original request.\n"
                + "- If the most recent note addressed to you carries no instructions, complete the "
                + "parts of the request your agents can handle, then respond DONE.\n"
                + "- A part of the request is OUT OF SCOPE only if NONE of your available agents "
                + "could plausibly handle it. Out-of-scope parts are NOT your responsibility: never "
                + "delegate them, and do NOT withhold DONE because of them. When in doubt, delegate "
                + "to the closest-matching agent.\n"
                + "\nBased on the conversation so far, decide the next action:\n"
                + "- Carefully analyze the user's COMPLETE request. It may contain MULTIPLE parts "
                + "that require DIFFERENT agents.\n"
                + "- If ANY in-scope part of the request has NOT yet been addressed by an appropriate "
                + "agent, respond with ONLY the name of the agent that should handle the unaddressed "
                + "part (one of: "
                + String.join(", ", agentNames)
                + ")\n"
                + "- ONLY if ALL parts of the request that are in scope have been fully addressed, "
                + "respond with ONLY the word DONE\n\n"
                + "Important: Review the full conversation to check which parts have been handled. "
                + "Do NOT say DONE until every distinct in-scope part of the request has received a "
                + "response from a suitable agent.\n\n"
                + "Respond with a single word — either an agent name or DONE. No other text.";
    }

    /**
     * Build the INLINE task that normalizes the raw router output (LLM or user worker) to a
     * canonical agent name or {@code DONE}. See {@link
     * JavaScriptBuilder#normalizeRouterDecisionScript} for the algorithm and the plain-string
     * return contract the loop condition depends on.
     */
    private WorkflowTask buildRouteNormalizer(
            String normRef, String routerRef, List<String> agentNames) {
        WorkflowTask norm = new WorkflowTask();
        norm.setType("INLINE");
        norm.setTaskReferenceName(normRef);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", "graaljs");
        inputs.put("raw", ref(routerRef + ".output.result"));
        inputs.put("expression", JavaScriptBuilder.normalizeRouterDecisionScript(agentNames));
        norm.setInputParameters(inputs);
        return norm;
    }

    private String buildSelectScript(
            AgentConfig config, int numAgents, String loopRef, boolean random) {
        if (config.getAllowedTransitions() != null) {
            Map<String, List<String>> transitions = config.getAllowedTransitions();
            Map<String, List<Integer>> idxMap = new LinkedHashMap<>();
            Map<String, Integer> nameToIdx = new LinkedHashMap<>();
            for (int i = 0; i < config.getAgents().size(); i++) {
                nameToIdx.put(config.getAgents().get(i).getName(), i);
            }
            for (Map.Entry<String, List<String>> entry : transitions.entrySet()) {
                Integer srcIdx = nameToIdx.get(entry.getKey());
                if (srcIdx == null) continue;
                List<Integer> dstIndices =
                        entry.getValue().stream()
                                .map(nameToIdx::get)
                                .filter(Objects::nonNull)
                                .toList();
                if (!dstIndices.isEmpty()) {
                    idxMap.put(String.valueOf(srcIdx), dstIndices);
                }
            }
            String idxMapJson = JavaScriptBuilder.toJson(idxMap);
            return random
                    ? JavaScriptBuilder.constrainedRandomScript(idxMapJson, numAgents)
                    : JavaScriptBuilder.constrainedRoundRobinScript(idxMapJson, numAgents);
        }

        return random
                ? JavaScriptBuilder.randomSelectScript(numAgents)
                : JavaScriptBuilder.roundRobinSelectScript(numAgents);
    }

    private String buildIntroductions(AgentConfig config) {
        if (config.getAgents() == null) return "";
        List<String> intros = new ArrayList<>();
        for (AgentConfig sub : config.getAgents()) {
            if (sub.getIntroduction() != null && !sub.getIntroduction().isEmpty()) {
                intros.add("[" + sub.getName() + "]: " + sub.getIntroduction());
            }
        }
        return String.join("\n", intros);
    }

    /**
     * Build router LLM task wrapped in a SUB_WORKFLOW.
     *
     * <p>Using a sub-workflow prevents Conductor's DoWhile from accumulating previous iteration LLM
     * outputs as assistant messages. The router must make a fresh routing decision each iteration
     * based solely on the conversation variable — stale assistant messages confuse the model and
     * cause failures (e.g. consecutive/empty assistant messages that Gemini rejects).
     */
    private WorkflowTask buildIterativeRouterLlm(
            String taskRef, ParsedModel parsed, String systemPrompt, AgentConfig parentAgent) {
        // Inner LLM task inside the sub-workflow
        WorkflowTask llm = new WorkflowTask();
        llm.setName("LLM_CHAT_COMPLETE");
        llm.setTaskReferenceName(taskRef + "_llm");
        llm.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> llmInputs = new LinkedHashMap<>();
        llmInputs.put("llmProvider", parsed.getProvider());
        llmInputs.put("model", parsed.getModel());
        llmInputs.put("maxTokens", 4096);
        llmInputs.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", systemPrompt),
                        Map.of("role", "user", "message", "${workflow.input.conversation}")));
        llmInputs.put("temperature", 0);
        llm.setInputParameters(llmInputs);

        // Sub-workflow definition containing just the LLM task
        WorkflowDef routerWf = new WorkflowDef();
        routerWf.setName(taskRef + "_wf");
        routerWf.setVersion(1);
        routerWf.setDescription("Router sub-workflow for " + taskRef);
        routerWf.setInputParameters(List.of("conversation"));
        routerWf.setTasks(List.of(llm));
        routerWf.setOutputParameters(Map.of("result", ref(taskRef + "_llm.output.result")));
        // Routers are implementation details of an AgentSpan execution.  Stamping the inline
        // definition preserves that identity in the execution index, where parentWorkflowId
        // distinguishes this generated child from the top-level agent run.
        agentCompiler.stampAgentMetadata(routerWf, parentAgent);

        // SUB_WORKFLOW task that passes conversation as input
        WorkflowTask subTask = new WorkflowTask();
        subTask.setType("SUB_WORKFLOW");
        subTask.setName(taskRef);
        subTask.setTaskReferenceName(taskRef);
        subTask.setSubWorkflowParam(new SubWorkflowParams());
        subTask.getSubWorkflowParam().setName(routerWf.getName());
        WorkflowTaskUtils.ensureAllTaskNames(routerWf);
        subTask.getSubWorkflowParam().setWorkflowDef(routerWf);
        subTask.setInputParameters(Map.of("conversation", "${workflow.variables.conversation}"));

        return subTask;
    }

    /** Build case tasks for handoff: sub-agent -> concat -> SetVariable. */
    private List<WorkflowTask> buildHandoffCaseTasks(AgentConfig parent, AgentConfig sub, int idx) {
        return buildHandoffCaseTasks(parent, sub, idx, "");
    }

    private List<WorkflowTask> buildHandoffCaseTasks(
            AgentConfig parent, AgentConfig sub, int idx, String suffix) {
        List<WorkflowTask> caseTasks = new ArrayList<>();
        String subRef = parent.getName() + "_handoff_" + idx + "_" + sub.getName() + suffix;

        WorkflowTask task =
                agentCompiler.compileSubAgent(
                        sub,
                        subRef,
                        "${workflow.variables.conversation}",
                        "${workflow.input.media}",
                        "${workflow.variables._agent_state}");
        caseTasks.add(task);

        // Concat response to conversation
        String responseRef = AgentCompiler.subAgentResultRef(sub, subRef);
        WorkflowTask concatTask = new WorkflowTask();
        concatTask.setType("INLINE");
        concatTask.setTaskReferenceName(parent.getName() + "_hconcat_" + idx + suffix);
        Map<String, Object> concatInputs = new LinkedHashMap<>();
        concatInputs.put("evaluatorType", "graaljs");
        concatInputs.put("expression", JavaScriptBuilder.concatScript(sub.getName()));
        concatInputs.put("prev", "${workflow.variables.conversation}");
        concatInputs.put("response", responseRef);
        concatTask.setInputParameters(concatInputs);
        caseTasks.add(concatTask);

        // Merge child context back into _agent_state
        String hCtxMergeRef = parent.getName() + "_hctx_merge_" + idx + suffix;
        WorkflowTask hCtxMerge = new WorkflowTask();
        hCtxMerge.setType("INLINE");
        hCtxMerge.setTaskReferenceName(hCtxMergeRef);
        hCtxMerge.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "parent",
                        "${workflow.variables._agent_state}",
                        "child",
                        "${" + subRef + ".output.context}",
                        "expression",
                        JavaScriptBuilder.flatMergeContextScript()));
        caseTasks.add(hCtxMerge);

        // Persist updated conversation + merged context
        WorkflowTask setVar = new WorkflowTask();
        setVar.setType("SET_VARIABLE");
        setVar.setTaskReferenceName(parent.getName() + "_hset_" + idx + suffix);
        Map<String, Object> setParams = new LinkedHashMap<>();
        setParams.put(
                "conversation",
                ref(parent.getName() + "_hconcat_" + idx + suffix + ".output.result"));
        setParams.put("_agent_state", "${" + hCtxMergeRef + ".output.result}");
        setVar.setInputParameters(setParams);
        caseTasks.add(setVar);

        return caseTasks;
    }

    private AgentCompiler.ResolvedInstructions resolveInstructionsPlan(
            AgentConfig config, String refName) {
        return agentCompiler.resolveInstructions(config, refName);
    }

    // ── Plan-Execute strategy ─────────────────────────────────────────
    //
    // Planner (agentic LLM) → extract JSON fence → compile plan to dynamic
    // Conductor sub-workflow → execute deterministically → on failure,
    // run fallback agent (agentic LLM, bounded turns).
    //
    // The JSON plan describes a DAG of operations.  Each operation is either
    // "static" (tool call with known args) or "generated" (LLM produces args).
    // Static ops compile to SIMPLE tasks.  Generated ops compile to
    // LLM_CHAT_COMPLETE → INLINE(parse) → SIMPLE(apply) chains running in
    // parallel within each step.

    private WorkflowDef compilePlanExecute(AgentConfig config) {
        // Named-slot resolution. PLAN_EXECUTE requires ``planner=``;
        // ``fallback=`` is optional. The Python SDK rejects the legacy
        // ``agents=[planner, fallback]`` positional shape at construction
        // time (see Agent.__init__); we mirror that hard cut here so the
        // Java SDK and any HTTP caller crafting JSON by hand fail with the
        // same migration message instead of silently quasi-working.
        AgentConfig plannerConfig = config.getPlanner();
        AgentConfig fallbackConfig = config.getFallback();
        if (plannerConfig == null) {
            throw new IllegalArgumentException(
                    "PLAN_EXECUTE strategy requires ``planner=<Agent>`` on the parent agent. "
                            + "The legacy ``agents=[planner, fallback]`` positional shape is no "
                            + "longer accepted — set the named slots ``planner=`` (required) and "
                            + "``fallback=`` (optional) instead.");
        }

        // Parent-level ``tools`` is the canonical plan-executable set. The
        // planner is told which tools are available (so it can't hallucinate
        // names), PAC validates ``op.tool`` names against this set, and PAC
        // wraps each emitted SIMPLE task with the tool's input guardrails
        // (if any). Empty/null degrades gracefully — no allowlist check, no
        // guardrail wrapping; the recommended shape always sets tools.
        List<ToolConfig> parentTools = config.getTools() != null ? config.getTools() : List.of();

        // Warn when a tool's guardrail uses a non-RAISE on_fail and there's
        // no fallback agent to recover. In plan mode, RETRY/FIX/HUMAN all
        // collapse to TERMINATE on the dynamic plan SUB_WORKFLOW; without a
        // configured fallback, the whole pipeline just fails — the user
        // probably intended adaptive recovery (which the fallback agent
        // provides). Log-only — don't block compile, since "fail loud on
        // guardrail trip" is also a valid choice.
        if (fallbackConfig == null) {
            List<String> offenders = new ArrayList<>();
            for (ToolConfig t : parentTools) {
                if (t.getGuardrails() == null) continue;
                for (GuardrailConfig g : t.getGuardrails()) {
                    String onFail = g.getOnFail();
                    if (onFail != null && !"raise".equalsIgnoreCase(onFail)) {
                        offenders.add(
                                t.getName() + ":" + g.getName() + " (on_fail=" + onFail + ")");
                    }
                }
            }
            if (!offenders.isEmpty()) {
                throw new IllegalStateException(
                        "PLAN_EXECUTE harness '"
                                + config.getName()
                                + "' has guardrails with on_fail=retry|fix|human but no fallback "
                                + "agent. In plan mode these collapse to TERMINATE — the user-intended "
                                + "retry-with-feedback semantics do not apply. Either configure a "
                                + "``fallback=<Agent>`` on the harness, or set ``on_fail=raise`` on "
                                + "these guardrails to acknowledge fail-closed semantics. Offenders: "
                                + String.join(", ", offenders));
            }
        }
        List<String> knownToolNames = new ArrayList<>();
        for (ToolConfig t : parentTools) {
            if (t.getName() != null && !t.getName().isEmpty()) {
                knownToolNames.add(t.getName());
            }
        }
        // Serialise the full ToolConfig list to Maps so PAC can deserialise
        // them server-side and reach guardrail metadata at SUB_WORKFLOW
        // emission time. ``knownToolNames`` is preserved for the existing
        // allowlist test surface; ``parentTools`` is the new field that
        // drives guardrail wrapping.
        List<Map<String, Object>> parentToolsAsMaps = new ArrayList<>();
        for (ToolConfig t : parentTools) {
            // /dg #1: reject schemas using JSON-Schema features the runtime
            // INLINE validator silently ignores ($ref, allOf, anyOf, oneOf,
            // format, if/then/else, etc.). Without this check users got
            // permissive runtime validation — the schema appears to declare
            // constraints but the validator never fires them. Fail at
            // agent-compile time with the exact offending keyword + path.
            if (t.getInputSchema() != null) {
                try {
                    SchemaSubsetValidator.validate(
                            t.getInputSchema(),
                            "PLAN_EXECUTE '"
                                    + config.getName()
                                    + "': tool '"
                                    + t.getName()
                                    + "' inputSchema");
                } catch (SchemaSubsetValidator.UnsupportedSchemaException usx) {
                    throw new IllegalStateException(usx.getMessage(), usx);
                }
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = MAPPER.convertValue(t, Map.class);
                parentToolsAsMaps.add(m);
            } catch (Exception e) {
                // /dg #7: fail-closed on ALL serialization failures, not just
                // guardrailed ones. Previously a non-guardrailed tool was
                // silently dropped from parentToolsByName with only a WARN,
                // which meant ``knownToolNames`` still allowed the tool but
                // PAC had no schema / inputSchema / guardrail context — a
                // generate-op output landed in a bare SIMPLE with no
                // validation. Treat the divergence as a compile error so the
                // user fixes the ToolConfig (typically a non-Jackson-friendly
                // value in inputSchema or config) instead of shipping a
                // half-configured tool. Guardrailed tools get the longer
                // diagnostic since the failure mode there is more dangerous.
                int guardrailCount = t.getGuardrails() != null ? t.getGuardrails().size() : 0;
                throw new IllegalStateException(
                        "PLAN_EXECUTE '"
                                + config.getName()
                                + "': tool '"
                                + t.getName()
                                + "' failed to serialise for PAC ("
                                + e.getMessage()
                                + ")."
                                + (guardrailCount > 0
                                        ? " The tool has "
                                                + guardrailCount
                                                + " guardrail(s) — silently dropping it would compile a"
                                                + " wrapper-less version of a safety-checked tool."
                                        : " Silently dropping the tool would leave"
                                                + " ``knownToolNames`` allowing it while PAC has no schema or"
                                                + " inputSchema for validation.")
                                + " Fix the ToolConfig (typically a non-Jackson-friendly value"
                                + " in inputSchema or config) and recompile.",
                        e);
            }
        }

        WorkflowDef wf = agentCompiler.createWorkflow(config);
        wf.setDescription("Plan-Execute harness: " + config.getName());

        List<WorkflowTask> tasks = new ArrayList<>();
        String prefix = toRef(config.getName());

        // ── 1. Context init ──────────────────────────────────────────
        String ctxResolveRef = prefix + "_ctx_resolve";
        WorkflowTask ctxResolve = new WorkflowTask();
        ctxResolve.setType("INLINE");
        ctxResolve.setTaskReferenceName(ctxResolveRef);
        ctxResolve.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "ctx", "${workflow.input.context}",
                        "expression", JavaScriptBuilder.nullCoalesceScript()));
        tasks.add(ctxResolve);

        WorkflowTask ctxInit = new WorkflowTask();
        ctxInit.setType("SET_VARIABLE");
        ctxInit.setTaskReferenceName(prefix + "_ctx_init");
        ctxInit.setInputParameters(Map.of("context", "${" + ctxResolveRef + ".output.result}"));
        tasks.add(ctxInit);

        // ── 2. Run planner (agentic sub-workflow) ────────────────────
        // Augment the planner's user prompt with the parent's tool list.
        // The planner can ONLY emit ``op.tool`` names from this set;
        // PAC validates the plan against ``knownToolNames`` below. Stating
        // the constraint explicitly in the prompt prevents hallucinated
        // tool names (workflow ``a369f52c`` got bitten by Claude emitting
        // ``str_replace`` from training memory; PAC then compiled a
        // task that no worker polled for and the workflow hung).
        // Compose the planner's user prompt: original prompt + auto-generated
        // tool list + auto-generated plan schema. Both server-generated
        // blocks share a contract with PAC's validator, so users don't
        // re-teach them in every harness's instructions string. (Examples
        // pre-#1 hand-wrote ~50 lines of plan schema in their instructions;
        // that's now redundant — the server appends a canonical version.)
        String availableToolsBlock = buildAvailableToolsBlock(parentTools);
        String planSchemaBlock = buildPlanSchemaBlock();

        // ── 2a. Optional planner context (text + URL fetches) ────────────
        // When the harness declares ``plannerContext``, emit a per-URL HTTP
        // fetch + a concatenating INLINE inside the planner-route LIVE
        // branch so the static-plan path skips the work. The INLINE's
        // ``output.result`` is referenced as a ``## Reference Context``
        // block in the planner's user prompt.
        List<WorkflowTask> contextPreTasks = new ArrayList<>();
        String contextBuildRef =
                emitPlannerContextBuilder(config.getPlannerContext(), prefix, contextPreTasks);

        StringBuilder pp = new StringBuilder("${workflow.input.prompt}");
        if (contextBuildRef != null) {
            pp.append("\n\n## Reference Context\n${")
                    .append(contextBuildRef)
                    .append(".output.result}");
        }
        if (!availableToolsBlock.isEmpty()) {
            pp.append("\n\n").append(availableToolsBlock);
        }
        pp.append("\n\n").append(planSchemaBlock);
        String plannerPrompt = pp.toString();
        String plannerRef = prefix + "_planner";
        String plannerCoerceRef = prefix + "_planner_coerce";
        emitPlannerStage(
                plannerConfig,
                prefix,
                plannerRef,
                plannerCoerceRef,
                plannerPrompt,
                contextPreTasks,
                tasks);
        String plannerResult = AgentCompiler.coercedRef(plannerCoerceRef);

        // ── 2b. Optional plan_source: deterministic tool call to read plan ──
        // If planSource is configured, call the specified tool (e.g. contextbook_read)
        // to retrieve the plan from an external source. This provides a deterministic
        // fallback: even if the planner's text output fails extraction, the plan can
        // be read directly from where the explorer wrote it.
        //
        // Validate at compile time that ``planSource.tool`` is a real tool registered
        // somewhere in the harness — a typo is silently swallowed if we wait until
        // runtime (the ``optional:true`` task simply doesn't run, extraction falls
        // through to the no_plan branch). Reject the harness here so the misconfig
        // surfaces at deploy.
        String planReaderRef = null;
        if (config.getPlanSource() != null) {
            Map<String, Object> planSource = config.getPlanSource();
            String toolName = (String) planSource.get("tool");
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException(
                        "plan_source must include a non-empty 'tool' field");
            }
            if (!isToolRegisteredInHarness(config, toolName)) {
                throw new IllegalArgumentException(
                        "plan_source.tool '"
                                + toolName
                                + "' is not registered as a harness-level tool on '"
                                + config.getName()
                                + "'. The plan_reader task is emitted in the harness's task "
                                + "namespace, so the tool must be declared in tools=[...] on the harness itself "
                                + "(declaring it on a sub-agent does not work).");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> toolArgs =
                    (Map<String, Object>) planSource.getOrDefault("args", Map.of());

            planReaderRef = prefix + "_plan_reader";
            WorkflowTask planReaderTask = new WorkflowTask();
            planReaderTask.setName(toolName);
            planReaderTask.setTaskReferenceName(planReaderRef);
            planReaderTask.setType("SIMPLE");

            Map<String, Object> readerInputs = new LinkedHashMap<>(toolArgs);
            // Forward the ambient execution inputs — the same set the dynamic plan's per-tool
            // tasks receive via injectAmbient. A reader tool that
            // needs cwd (e.g. filesystem reads of a workspace plan file) was
            // previously starved of working-dir context and silently failed
            // through to the no_plan branch. Forced overrides — if planSource
            // toolArgs accidentally collided on these keys, the ambient values
            // win.
            readerInputs.put("session_id", "${workflow.input.session_id}");
            readerInputs.put("cwd", "${workflow.input.cwd}");
            readerInputs.put("media", "${workflow.input.media}");
            planReaderTask.setInputParameters(readerInputs);
            // ``optional:true`` is intentional: plan_source is a backup for plan
            // extraction. A reader pointed at a contextbook section that doesn't
            // exist yet (e.g. the planner didn't write to it on this run) is a
            // normal "no fallback content available" condition — extract_json
            // then tries other sources. If the harness's compile-time tool-exists
            // validation passed but the read still failed, the no_plan SWITCH
            // path will surface that as a missing-fence failure with the
            // fallback agent (or TERMINATE if no fallback configured).
            planReaderTask.setOptional(true);
            tasks.add(planReaderTask);
        }

        // ── 3. Extract JSON plan from planner output ─────────────────
        // Pass BOTH the raw result (Java Map if LLM returned JSON) and the
        // coerced string (for markdown-with-fence case).  The extract script
        // tries the raw object first (checking for a `steps` key), then falls
        // back to regex-extracting a ```json fence from the coerced string.
        // If planSource is configured, planReaderContent provides a deterministic
        // fallback source — the script tries it after the planner text fails.
        String extractRef = prefix + "_extract_json";
        WorkflowTask extractTask = new WorkflowTask();
        extractTask.setType("INLINE");
        extractTask.setTaskReferenceName(extractRef);
        Map<String, Object> extractInputs = new LinkedHashMap<>();
        extractInputs.put("evaluatorType", "graaljs");
        // ``staticPlan`` (Case 0 — highest priority) is the user-supplied plan
        // passed through ``runtime.run(harness, plan=...)``. When present, it
        // wins over planner output and the plan_source backup. The planner
        // LLM still runs (the workflow shape is fixed at compile time) but
        // its output is discarded by extract_json.
        extractInputs.put("staticPlan", "${workflow.input.static_plan}");
        extractInputs.put("rawResult", AgentCompiler.subAgentResultRef(plannerConfig, plannerRef));
        extractInputs.put("coercedResult", plannerResult);
        extractInputs.put(
                "planReaderContent",
                planReaderRef != null ? "${" + planReaderRef + ".output.result}" : "");
        extractInputs.put("expression", JavaScriptBuilder.extractJsonFenceScript());
        extractTask.setInputParameters(extractInputs);
        tasks.add(extractTask);

        // ── 4. SWITCH: if JSON plan found → compile & execute, else → fallback ──
        // Tighten the predicate beyond presence: the plan must actually parse,
        // be an object, and have a non-empty ``steps`` array — that is what
        // PLAN_AND_COMPILE will require. A weaker check sends garbage into
        // the compiler and then routes the validation error to the fallback
        // when the simpler "no_plan" branch would have done.
        String hasJsonRef = prefix + "_has_json";
        WorkflowTask hasJsonCheck = new WorkflowTask();
        hasJsonCheck.setType("INLINE");
        hasJsonCheck.setTaskReferenceName(hasJsonRef);
        hasJsonCheck.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "json",
                        "${" + extractRef + ".output.result.plan_json}",
                        "expression",
                        "(function(){ if (!$.json || $.json === '{}') return 'no_plan';"
                                + " try { var p = JSON.parse($.json); if (!p || typeof p !== 'object') return 'no_plan';"
                                + " if (!Array.isArray(p.steps) || p.steps.length === 0) return 'no_plan';"
                                + " return 'has_plan'; } catch(e) { return 'no_plan'; } })()"));
        tasks.add(hasJsonCheck);

        // Build the two branches.
        // Fallback agents see ``markdown_plan`` (the original planner prose
        // produced by extract_json) — not ``plannerResult`` (the coerced /
        // possibly re-serialized form). The original text is what the LLM
        // wrote and is more useful context when the agentic recovery loop
        // tries to repair the situation.
        String fallbackPlanText = "${" + extractRef + ".output.result.markdown_plan}";
        List<WorkflowTask> hasPlanTasks =
                buildPlanExecutionBranch(
                        config,
                        plannerConfig,
                        fallbackConfig,
                        prefix,
                        extractRef,
                        fallbackPlanText,
                        knownToolNames,
                        parentToolsAsMaps);
        List<WorkflowTask> noPlanTasks =
                buildFallbackOnlyBranch(config, fallbackConfig, prefix, fallbackPlanText);

        WorkflowTask routeSwitch = new WorkflowTask();
        routeSwitch.setType("SWITCH");
        routeSwitch.setTaskReferenceName(prefix + "_plan_route");
        routeSwitch.setEvaluatorType("value-param");
        routeSwitch.setExpression("switchCaseValue");
        routeSwitch.setInputParameters(
                Map.of("switchCaseValue", "${" + hasJsonRef + ".output.result}"));
        routeSwitch.setDecisionCases(Map.of("has_plan", hasPlanTasks));
        routeSwitch.setDefaultCase(noPlanTasks);
        tasks.add(routeSwitch);

        // ── Output selector: read final_result variable ─────────────────
        // /dg #5: each of the four mutually-exclusive terminal branches
        // (plan_exec success, exec-failure fallback, compile-failure
        // fallback, no-plan fallback) writes ``workflow.variables.final_result``
        // via SET_VARIABLE as its last task. The selector reads from that
        // single resolved variable instead of pattern-matching unresolved
        // ``${...}`` template strings across all four branch refs.
        //
        // The previous shape needed a ``safe()`` helper to filter out
        // Conductor-left-behind ``${...}`` literals from dead branches, and
        // built the ``${`` marker from ``String.fromCharCode(36)`` to keep
        // the script's source out of Conductor's own templater. All of
        // that goes away: the variable resolves to exactly one value
        // (the branch that ran), or null if no branch did.
        String outputRef = prefix + "_output_select";
        WorkflowTask outputSelect = new WorkflowTask();
        outputSelect.setType("INLINE");
        outputSelect.setTaskReferenceName(outputRef);
        Map<String, Object> outputInputs = new LinkedHashMap<>();
        outputInputs.put("evaluatorType", "graaljs");
        outputInputs.put("r", "${workflow.variables.final_result}");
        outputInputs.put(
                "expression",
                "(function(){"
                        + " function plain(v){"
                        + " if(v == null || typeof v !== 'object') return v;"
                        + " if(Array.isArray(v)){ var a=[]; for(var i=0;i<v.length;i++) a.push(plain(v[i])); return a; }"
                        + " if(v.keySet && v.get){ var o={}; var ks=v.keySet().toArray();"
                        + " for(var k=0;k<ks.length;k++){ var key=ks[k]; o[key]=plain(v.get(key)); } return o; }"
                        + " if(v.size && v.get){ var l=[]; for(var j=0;j<v.size();j++) l.push(plain(v.get(j))); return l; }"
                        + " var native={}; var nativeKeys=Object.keys(v);"
                        + " for(var n=0;n<nativeKeys.length;n++){ var nativeKey=nativeKeys[n]; native[nativeKey]=plain(v[nativeKey]); }"
                        + " return native;"
                        + " }"
                        + " var r=plain($.r); if(r == null) return '';"
                        + " return (typeof r === 'object') ? JSON.stringify(r) : String(r);"
                        + " })()");
        outputSelect.setInputParameters(outputInputs);
        tasks.add(outputSelect);

        wf.setTasks(tasks);
        wf.setOutputParameters(
                Map.of(
                        "result",
                        "${" + outputRef + ".output.result}",
                        "context",
                        "${workflow.variables.context}"));
        agentCompiler.applyTimeout(wf, config);
        return wf;
    }

    /**
     * Build the "has_plan" branch: compile JSON plan to dynamic workflow, register it, execute as
     * SUB_WORKFLOW, then SWITCH on success/failure.
     */
    private List<WorkflowTask> buildPlanExecutionBranch(
            AgentConfig config,
            AgentConfig plannerConfig,
            AgentConfig fallbackConfig,
            String prefix,
            String extractRef,
            String plannerResult,
            List<String> knownToolNames,
            List<Map<String, Object>> parentToolsAsMaps) {

        List<WorkflowTask> tasks = new ArrayList<>();

        // ── 5. Compile JSON plan to Conductor WorkflowDef ────────────
        // PLAN_AND_COMPILE is a server-side Java system task. Its output is a
        // structured Map: ``{workflowDef: Map|null, error: String|null,
        // warnings: [...], stats: {...}}``. Validation failures complete the
        // task with status COMPLETED but error non-null; the SWITCH below
        // routes on that. Compared with the old GraalJS INLINE compiler this
        // (a) eliminates the JSON-string round-trip (workflowDef is already a
        // Map for SubWorkflowTaskMapper), and (b) makes the compilation logic
        // unit-testable in plain Java.
        String compileRef = prefix + "_plan_and_compile";
        WorkflowTask compileTask = new WorkflowTask();
        compileTask.setType(PlanAndCompileTask.TASK_TYPE);
        compileTask.setName("plan_and_compile");
        compileTask.setTaskReferenceName(compileRef);
        Map<String, Object> compileInputs = new LinkedHashMap<>();
        compileInputs.put("planJson", "${" + extractRef + ".output.result.plan_json}");
        compileInputs.put("parentName", config.getName());
        compileInputs.put(
                "model", config.getModel() != null ? config.getModel() : "openai/gpt-4o-mini");
        Integer harnessTimeout = config.getTimeoutSeconds();
        if (harnessTimeout != null && harnessTimeout > 0) {
            compileInputs.put("harnessTimeoutSeconds", harnessTimeout);
        }
        // Tool-name allowlist: PAC rejects plans referencing tools outside
        // this set ∪ server-side built-ins. Empty list disables the check
        // (legacy callers without parent tools degrade to old behaviour).
        if (knownToolNames != null && !knownToolNames.isEmpty()) {
            compileInputs.put("knownToolNames", knownToolNames);
        }
        // Full tool configs (with guardrails). PAC uses these to wrap each
        // emitted SIMPLE task with the tool's input guardrails — without
        // this, a plan referencing a guardrailed tool would compile into a
        // bare SIMPLE that bypasses the safety check entirely.
        if (parentToolsAsMaps != null && !parentToolsAsMaps.isEmpty()) {
            compileInputs.put("parentTools", parentToolsAsMaps);
        }
        compileTask.setInputParameters(compileInputs);
        tasks.add(compileTask);

        // ── 5b. Surface compile errors before they reach SUB_WORKFLOW ─
        // PLAN_AND_COMPILE sets ``output.error`` to a non-null string on
        // validation failure. Fold ``error set`` and ``workflowDef null``
        // into a single ``compile_failed`` sentinel so the gate has no
        // fall-through case. When a fallback agent is configured, route
        // compile failures into it — compile failure is the canonical case
        // the agentic fallback exists to recover from. Only TERMINATE when
        // no fallback is available.
        String compileStatusRef = prefix + "_compile_status";
        WorkflowTask compileStatus = new WorkflowTask();
        compileStatus.setType("INLINE");
        compileStatus.setTaskReferenceName(compileStatusRef);
        compileStatus.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "wfDef",
                        "${" + compileRef + ".output.workflowDef}",
                        "err",
                        "${" + compileRef + ".output.error}",
                        "expression",
                        "(function(){ if ($.err || !$.wfDef) return 'compile_failed'; return 'ok'; })()"));
        tasks.add(compileStatus);

        // Compile-failure branch: fallback agent if configured, else TERMINATE.
        List<WorkflowTask> compileFailureBranch;
        if (fallbackConfig != null) {
            // Reuse the regular fallback infrastructure but with ``compileRef``
            // as the error source — the PLAN_AND_COMPILE task's output map
            // contains the error string and any warnings. A distinct prefix
            // prevents task-name collision with the exec-failure fallback.
            List<WorkflowTask> base =
                    new ArrayList<>(
                            buildFallbackBranch(
                                    config,
                                    fallbackConfig,
                                    prefix + "_compile",
                                    plannerResult,
                                    compileRef));
            // /dg #5: terminal SET_VARIABLE so the output selector reads
            // ``workflow.variables.final_result`` instead of pattern-matching
            // unresolved ``${_compile_fallback.output.result}`` from the
            // outer scope.
            WorkflowTask compileFallbackSet = new WorkflowTask();
            compileFallbackSet.setType("SET_VARIABLE");
            compileFallbackSet.setTaskReferenceName(prefix + "_compile_fallback_set");
            compileFallbackSet.setInputParameters(
                    Map.of("final_result", "${" + prefix + "_compile_fallback.output.result}"));
            base.add(compileFallbackSet);
            compileFailureBranch = base;
        } else {
            WorkflowTask compileFail = new WorkflowTask();
            compileFail.setType("TERMINATE");
            compileFail.setTaskReferenceName(prefix + "_compile_fail");
            compileFail.setInputParameters(
                    Map.of(
                            "terminationStatus",
                            "FAILED",
                            "terminationReason",
                            "Plan compilation failed: ${" + compileRef + ".output.error}"));
            compileFailureBranch = List.of(compileFail);
        }

        // ── 6. Build the compile-success branch: exec + status-check + fallback gate
        // These tasks live inside compileGate's ``default`` case so they are
        // SKIPPED entirely when compile_failed. Previously they were sibling
        // tasks of compileGate and ran unconditionally — when compile failed,
        // plan_exec then attempted to execute against a null workflowDef and
        // failed the whole workflow, even though the compile-fallback branch
        // had already recovered.
        String planWfName = planWorkflowName(config.getName());
        String execRef = prefix + "_plan_exec";
        WorkflowTask execTask = new WorkflowTask();
        execTask.setType("SUB_WORKFLOW");
        execTask.setName(planWfName);
        execTask.setTaskReferenceName(execRef);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(planWfName);
        subParams.setVersion(1);
        subParams.setWorkflowDefinition("${" + compileRef + ".output.workflowDef}");
        execTask.setSubWorkflowParam(subParams);
        Map<String, Object> execInputs = new LinkedHashMap<>();
        execInputs.put("prompt", "${workflow.input.prompt}");
        execInputs.put("session_id", "${workflow.input.session_id}");
        execInputs.put("context", "${workflow.variables.context}");
        // Forward execution-scoped inputs that compiled tools may need: working
        // directory (cwd) for filesystem tools and media for vision/audio tools. Previously these
        // were silently dropped, forcing examples to hardcode WORK_DIR etc.
        execInputs.put("cwd", "${workflow.input.cwd}");
        execInputs.put("media", "${workflow.input.media}");
        execTask.setInputParameters(execInputs);
        // optional:true — without this, a non-COMPLETED dynamic plan
        // (guardrail trip TERMINATEs, plan-step failure, etc.) FAILs the
        // task, which halts the parent workflow before ``statusCheck`` /
        // ``statusSwitch`` can route to the fallback. The earlier comment
        // here said the opposite, but Conductor halts on non-optional task
        // failures regardless of any downstream SWITCH — there's no way to
        // "catch" the failure without optional:true. We then read the real
        // status from ``${execRef.status}`` in statusCheck below and route
        // to fallback when it isn't COMPLETED.
        execTask.setOptional(true);

        String statusRef = prefix + "_exec_status";
        WorkflowTask statusCheck = new WorkflowTask();
        statusCheck.setType("INLINE");
        statusCheck.setTaskReferenceName(statusRef);
        statusCheck.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "taskStatus", "${" + execRef + ".status}",
                        "expression",
                                "(function(){ "
                                        + "var s = String($.taskStatus || ''); "
                                        + "return (s === 'COMPLETED') ? 'success' : 'failed'; })()"));

        List<WorkflowTask> fallbackTasks =
                buildFallbackBranch(config, fallbackConfig, prefix, plannerResult, execRef);

        // /dg #5: each terminal arm writes ``workflow.variables.final_result``
        // via SET_VARIABLE so the output selector reads from one resolved
        // variable instead of pattern-matching unresolved ``${...}`` template
        // strings across four mutually-exclusive branches. Append the
        // ``final_result`` SET_VARIABLE to every branch's last task.
        WorkflowTask execSuccessSet = new WorkflowTask();
        execSuccessSet.setType("SET_VARIABLE");
        execSuccessSet.setTaskReferenceName(prefix + "_exec_success_set");
        execSuccessSet.setInputParameters(
                Map.of("final_result", "${" + execRef + ".output.result}"));

        WorkflowTask statusSwitch = new WorkflowTask();
        statusSwitch.setType("SWITCH");
        statusSwitch.setTaskReferenceName(prefix + "_exec_route");
        statusSwitch.setEvaluatorType("value-param");
        statusSwitch.setExpression("switchCaseValue");
        statusSwitch.setInputParameters(
                Map.of("switchCaseValue", "${" + statusRef + ".output.result}"));
        // Append the exec_fallback's SET_VARIABLE ONLY when the fallback
        // branch actually produces a result. With no fallback configured,
        // buildFallbackBranch returns ``[TERMINATE]`` — Conductor halts
        // there, the SET_VARIABLE would be dead code, and existing tests
        // assert TERMINATE is the branch's last task. Same gate pattern
        // as the compile-failure branch below.
        List<WorkflowTask> failedBranch = new ArrayList<>(fallbackTasks);
        if (fallbackConfig != null) {
            WorkflowTask fallbackSet = new WorkflowTask();
            fallbackSet.setType("SET_VARIABLE");
            fallbackSet.setTaskReferenceName(prefix + "_fallback_set");
            fallbackSet.setInputParameters(
                    Map.of("final_result", "${" + prefix + "_fallback.output.result}"));
            failedBranch.add(fallbackSet);
        }
        statusSwitch.setDecisionCases(Map.of("failed", failedBranch));
        statusSwitch.setDefaultCase(List.of(execSuccessSet));

        List<WorkflowTask> compileSuccessBranch = new ArrayList<>();
        compileSuccessBranch.add(execTask);
        compileSuccessBranch.add(statusCheck);
        compileSuccessBranch.add(statusSwitch);

        WorkflowTask compileGate = new WorkflowTask();
        compileGate.setType("SWITCH");
        compileGate.setTaskReferenceName(prefix + "_compile_gate");
        compileGate.setEvaluatorType("value-param");
        compileGate.setExpression("switchCaseValue");
        compileGate.setInputParameters(
                Map.of("switchCaseValue", "${" + compileStatusRef + ".output.result}"));
        compileGate.setDecisionCases(Map.of("compile_failed", compileFailureBranch));
        compileGate.setDefaultCase(compileSuccessBranch);
        tasks.add(compileGate);

        return tasks;
    }

    /**
     * Emit per-URL fetch tasks plus a concatenating INLINE that builds the {@code ## Reference
     * Context} block injected into the planner's prompt. Returns the ref of the INLINE so the
     * caller can template {@code ${ref.output.result}} into the prompt — or {@code null} when
     * {@code entries} is null/empty (no context configured).
     *
     * <p>Per-entry semantics:
     *
     * <ul>
     *   <li>{@code text}: inlined verbatim — no fetch.
     *   <li>{@code url}: emits a {@code PLANNER_CONTEXT_FETCH} system task with the supplied
     *       headers (credential placeholders rewritten from {@code ${CRED}} to {@code
     *       ${workflow.secrets.CRED}}, which conductor resolves wire-only at task hand-off). The
     *       custom task adds an in-process TTL cache + {@code If-None-Match} conditional-GET on top
     *       of Conductor's HTTP task — see {@link PlannerContextFetchTask}. {@code required=false}
     *       doesn't fail the workflow on a fetch error; instead the INLINE substitutes a {@code
     *       [doc unavailable]} marker. {@code maxBytes} (default 16384) truncates large responses
     *       with a {@code [doc truncated]} marker.
     * </ul>
     *
     * <p>/dg #4: when there are ≥2 URL fetches the compiler wraps them in a {@code FORK_JOIN} so
     * they run in parallel. Single-fetch case stays flat to keep the workflow graph readable. The
     * {@code _ctx_build} INLINE always runs after all fetches complete.
     */
    private String emitPlannerContextBuilder(
            List<Map<String, Object>> entries, String prefix, List<WorkflowTask> out) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        // Per-entry descriptors handed to the builder INLINE. URL entries
        // reference the fetch task's response.body via Conductor template;
        // text entries inline their literal.
        List<Map<String, Object>> descriptors = new ArrayList<>();
        // Fetch tasks collected here so >1 can be wrapped in FORK_JOIN.
        List<WorkflowTask> fetchTasks = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> e = entries.get(i);
            if (e == null) continue;
            Object text = e.get("text");
            Object url = e.get("url");
            if (text instanceof String ts && !ts.isEmpty()) {
                descriptors.add(Map.of("type", "text", "text", ts));
            } else if (url instanceof String us && !us.isEmpty()) {
                String fetchRef = prefix + "_ctx_fetch_" + i;
                WorkflowTask fetch = new WorkflowTask();
                fetch.setName(PlannerContextFetchTask.TASK_TYPE);
                fetch.setType(PlannerContextFetchTask.TASK_TYPE);
                fetch.setTaskReferenceName(fetchRef);

                Map<String, Object> headers = new LinkedHashMap<>();
                Object hdrObj = e.get("headers");
                if (hdrObj instanceof Map<?, ?> hdrMap) {
                    for (Map.Entry<?, ?> h : hdrMap.entrySet()) {
                        // /dg #2: escape ONLY ``${CRED_NAME}`` patterns where
                        // ``CRED_NAME`` is an identifier — preserves literal
                        // ``${...}`` substrings that don't look like
                        // credentials. Also reject CR/LF up-front to close
                        // the response-splitting injection vector.
                        String name = String.valueOf(h.getKey());
                        String value = String.valueOf(h.getValue());
                        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                            throw new IllegalArgumentException(
                                    "plannerContext header '"
                                            + name
                                            + "' contains CR/LF — rejected to prevent HTTP response splitting");
                        }
                        headers.put(
                                name,
                                CREDENTIAL_PLACEHOLDER
                                        .matcher(value)
                                        .replaceAll("\\${workflow.secrets.$1}"));
                    }
                }

                boolean required = !Boolean.FALSE.equals(e.get("required"));
                int maxBytes = 16384;
                if (e.get("maxBytes") instanceof Number n) {
                    maxBytes = n.intValue();
                }
                int ttlSeconds = 60;
                if (e.get("ttlSeconds") instanceof Number n) {
                    ttlSeconds = n.intValue();
                }

                Map<String, Object> fetchInputs = new LinkedHashMap<>();
                fetchInputs.put("url", us);
                fetchInputs.put("headers", headers);
                fetchInputs.put("required", required);
                fetchInputs.put("maxBytes", maxBytes);
                fetchInputs.put("ttl_seconds", ttlSeconds);
                fetch.setInputParameters(fetchInputs);

                if (!required) {
                    // /dg #4: optional=true means a doc-host outage on a
                    // non-required doc doesn't fail the workflow — the
                    // INLINE substitutes [doc unavailable] marker.
                    fetch.setOptional(true);
                }
                fetchTasks.add(fetch);

                Map<String, Object> desc = new LinkedHashMap<>();
                desc.put("type", "url");
                desc.put("url", us);
                desc.put("required", required);
                desc.put("maxBytes", maxBytes);
                // Conductor resolves these templates BEFORE invoking the
                // INLINE script — so $.entries[i].body is the actual body.
                desc.put("body", "${" + fetchRef + ".output.response.body}");
                desc.put("statusCode", "${" + fetchRef + ".output.response.statusCode}");
                descriptors.add(desc);
            }
            // Entries with neither text nor url are silently skipped — the
            // SDK already validates exactly-one-of at construction time,
            // so this only fires on hand-rolled wire payloads.
        }

        if (descriptors.isEmpty()) {
            return null;
        }

        // /dg #4: emit fetches in parallel when there are ≥2. Single-fetch
        // stays flat to keep the graph readable. The build INLINE always
        // runs after every fetch completes (FORK_JOIN's JOIN gives us
        // that for free).
        if (fetchTasks.size() >= 2) {
            String forkRef = prefix + "_ctx_fork";
            String joinRef = prefix + "_ctx_join";
            WorkflowTask fork = new WorkflowTask();
            fork.setType("FORK_JOIN");
            fork.setTaskReferenceName(forkRef);
            List<List<WorkflowTask>> branches = new ArrayList<>();
            List<String> joinOn = new ArrayList<>();
            for (WorkflowTask f : fetchTasks) {
                branches.add(List.of(f));
                joinOn.add(f.getTaskReferenceName());
            }
            fork.setForkTasks(branches);
            out.add(fork);
            WorkflowTask join = new WorkflowTask();
            join.setType("JOIN");
            join.setTaskReferenceName(joinRef);
            join.setJoinOn(joinOn);
            out.add(join);
        } else if (!fetchTasks.isEmpty()) {
            out.addAll(fetchTasks);
        }

        String buildRef = prefix + "_ctx_build";
        WorkflowTask buildTask = new WorkflowTask();
        buildTask.setType("INLINE");
        buildTask.setTaskReferenceName(buildRef);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("evaluatorType", "graaljs");
        inputs.put("entries", descriptors);
        inputs.put("expression", JavaScriptBuilder.plannerContextBuilderScript());
        buildTask.setInputParameters(inputs);
        out.add(buildTask);
        return buildRef;
    }

    /**
     * Emit the planner-stage tasks: a static-plan gate INLINE, then a SWITCH whose default branch
     * runs the planner sub-workflow + the three follow-on context-handling tasks, and whose
     * ``skip`` branch is a single no-op INLINE that fires when ``workflow.input.static_plan`` is
     * supplied (dg-review F1 / recommendation #13).
     *
     * <p>Appends two top-level tasks ({@code plannerGate} + {@code plannerRoute}) to the supplied
     * {@code tasks} list. Downstream consumers reference the planner's coerced output via {@link
     * AgentCompiler#coercedRef} with {@code plannerCoerceRef} — when the SWITCH takes the skip
     * branch those references resolve to null, which {@code extract_json} Case 0 doesn't read
     * anyway (it reads {@code workflow.input.static_plan} directly).
     *
     * <p>{@code preLiveBranchTasks} are prepended to the SWITCH's default (live) branch — used by
     * the planner-context fetch+build pipeline to resolve URLs on every planner invocation while
     * staying cost-free on the static-plan path (the skip branch never runs them).
     */
    private void emitPlannerStage(
            AgentConfig plannerConfig,
            String prefix,
            String plannerRef,
            String plannerCoerceRef,
            String plannerPrompt,
            List<WorkflowTask> preLiveBranchTasks,
            List<WorkflowTask> tasks) {

        String plannerGateRef = prefix + "_planner_gate";
        WorkflowTask plannerGate = new WorkflowTask();
        plannerGate.setType("INLINE");
        plannerGate.setTaskReferenceName(plannerGateRef);
        plannerGate.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "staticPlan",
                        "${workflow.input.static_plan}",
                        "expression",
                        // /dg #3: an object without ``steps`` (e.g. ``{}`` from
                        // ``runtime.run(harness, plan={})``) used to take the skip
                        // branch, then extract_json Case 0 rejected it for the
                        // missing key, and the user saw both "planner skipped" and
                        // "no plan found". Mirror Case 0's accept-criteria here so
                        // skip only fires when the static_plan is genuinely usable.
                        "(function(){ var sp = $.staticPlan; "
                                + "if (sp == null) return 'run'; "
                                + "if (typeof sp === 'object') {"
                                + "  var hasSteps = false;"
                                + "  try { hasSteps = sp.steps != null || (sp.get && sp.get('steps') != null); } catch(e) {}"
                                + "  return hasSteps ? 'skip' : 'run';"
                                + "} "
                                + "if (typeof sp === 'string' && sp.length > 2 && sp.indexOf('\"steps\"') >= 0) return 'skip'; "
                                + "return 'run'; })();"));
        tasks.add(plannerGate);

        // Live branch: planner sub-workflow + ctx_merge + ctx_set + coerce.
        WorkflowTask plannerTask =
                agentCompiler.compileSubAgent(
                        plannerConfig,
                        plannerRef,
                        plannerPrompt,
                        "${workflow.input.media}",
                        "${workflow.variables.context}");

        String plannerMergeRef = prefix + "_planner_ctx_merge";
        WorkflowTask plannerMerge = new WorkflowTask();
        plannerMerge.setType("INLINE");
        plannerMerge.setTaskReferenceName(plannerMergeRef);
        plannerMerge.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "parent",
                        "${workflow.variables.context}",
                        "child",
                        "${" + plannerRef + ".output.context}",
                        "expression",
                        JavaScriptBuilder.flatMergeContextScript()));

        WorkflowTask plannerCtxSet = new WorkflowTask();
        plannerCtxSet.setType("SET_VARIABLE");
        plannerCtxSet.setTaskReferenceName(prefix + "_planner_ctx_set");
        plannerCtxSet.setInputParameters(
                Map.of("context", "${" + plannerMergeRef + ".output.result}"));

        String plannerResultRaw = AgentCompiler.subAgentResultRef(plannerConfig, plannerRef);
        WorkflowTask plannerCoerce =
                AgentCompiler.createCoerceTask(plannerResultRaw, plannerCoerceRef);

        List<WorkflowTask> plannerLiveBranch = new ArrayList<>();
        if (preLiveBranchTasks != null) {
            plannerLiveBranch.addAll(preLiveBranchTasks);
        }
        plannerLiveBranch.add(plannerTask);
        plannerLiveBranch.add(plannerMerge);
        plannerLiveBranch.add(plannerCtxSet);
        plannerLiveBranch.add(plannerCoerce);

        // Skip branch: a single no-op INLINE so the SWITCH has both arms.
        WorkflowTask plannerSkipped = new WorkflowTask();
        plannerSkipped.setType("INLINE");
        plannerSkipped.setTaskReferenceName(prefix + "_planner_skipped");
        plannerSkipped.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function(){ return {result: '[planner skipped — static_plan supplied]'}; })();"));

        WorkflowTask plannerRoute = new WorkflowTask();
        plannerRoute.setType("SWITCH");
        plannerRoute.setTaskReferenceName(prefix + "_planner_route");
        plannerRoute.setEvaluatorType("value-param");
        plannerRoute.setExpression("route");
        plannerRoute.setInputParameters(Map.of("route", "${" + plannerGateRef + ".output.result}"));
        plannerRoute.setDecisionCases(Map.of("skip", List.of(plannerSkipped)));
        plannerRoute.setDefaultCase(plannerLiveBranch);
        tasks.add(plannerRoute);
    }

    /**
     * Build the fallback branch: run the fallback agent with plan + errors. When fallbackConfig is
     * null, returns a TERMINATE task with FAILED status.
     */
    private List<WorkflowTask> buildFallbackBranch(
            AgentConfig config,
            AgentConfig fallbackConfig,
            String prefix,
            String plannerResult,
            String execRef) {

        if (fallbackConfig == null) {
            WorkflowTask terminate = new WorkflowTask();
            terminate.setType("TERMINATE");
            terminate.setTaskReferenceName(prefix + "_no_fallback_term");
            terminate.setInputParameters(
                    Map.of(
                            "terminationStatus", "FAILED",
                            "terminationReason",
                                    "Plan execution failed and no fallback agent configured"));
            return List.of(terminate);
        }

        List<WorkflowTask> tasks = new ArrayList<>();

        // Compose fallback prompt: plan + errors
        String fbPromptRef = prefix + "_fb_prompt";
        WorkflowTask fbPrompt = new WorkflowTask();
        fbPrompt.setType("INLINE");
        fbPrompt.setTaskReferenceName(fbPromptRef);
        fbPrompt.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "plan",
                        plannerResult,
                        "execOutput",
                        "${" + execRef + ".output}",
                        "originalPrompt",
                        "${workflow.input.prompt}",
                        "expression",
                        "(function(){ "
                                + "var errors = ''; "
                                + "try { errors = JSON.stringify($.execOutput || {}, null, 2); } catch(e) { errors = String($.execOutput); } "
                                + "return $.originalPrompt + '\\n\\nPlan:\\n' + $.plan + '\\n\\nExecution errors:\\n' + errors; })()"));
        tasks.add(fbPrompt);

        // Apply fallbackMaxTurns if set. Use Lombok's toBuilder so every field
        // configured on the fallback agent (memory, prompt_inputs, tool_choice,
        // termination, handoffs, callbacks, etc.) is preserved — the previous
        // explicit-whitelist rebuild silently dropped anything not enumerated.
        Integer fbMaxTurns = config.getFallbackMaxTurns();
        if (fbMaxTurns != null) {
            fallbackConfig = fallbackConfig.toBuilder().maxTurns(fbMaxTurns).build();
        }

        String fallbackRef = prefix + "_fallback";
        WorkflowTask fallbackTask =
                agentCompiler.compileSubAgent(
                        fallbackConfig,
                        fallbackRef,
                        "${" + fbPromptRef + ".output.result}",
                        "${workflow.input.media}",
                        "${workflow.variables.context}");
        tasks.add(fallbackTask);

        return tasks;
    }

    /**
     * Build the "no_plan" branch: when JSON fence extraction fails, degrade to running the fallback
     * agent with just the planner output. When fallbackConfig is null, returns a TERMINATE task
     * with FAILED status.
     */
    private List<WorkflowTask> buildFallbackOnlyBranch(
            AgentConfig config, AgentConfig fallbackConfig, String prefix, String plannerResult) {

        if (fallbackConfig == null) {
            WorkflowTask terminate = new WorkflowTask();
            terminate.setType("TERMINATE");
            terminate.setTaskReferenceName(prefix + "_noplan_term");
            terminate.setInputParameters(
                    Map.of(
                            "terminationStatus", "FAILED",
                            "terminationReason",
                                    "No JSON plan found and no fallback agent configured"));
            return List.of(terminate);
        }

        List<WorkflowTask> tasks = new ArrayList<>();

        log.warn(
                "PLAN_EXECUTE '{}': no JSON fence found in planner output — degrading to fallback agent",
                config.getName());

        // Compose prompt: original + planner output (no errors since plan execution didn't happen)
        String npPromptRef = prefix + "_np_prompt";
        WorkflowTask npPrompt = new WorkflowTask();
        npPrompt.setType("INLINE");
        npPrompt.setTaskReferenceName(npPromptRef);
        npPrompt.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "plan",
                        plannerResult,
                        "originalPrompt",
                        "${workflow.input.prompt}",
                        "expression",
                        "(function(){ "
                                + "return $.originalPrompt + '\\n\\nPlanner output:\\n' + $.plan; })()"));
        tasks.add(npPrompt);

        // Apply fallbackMaxTurns identically to buildFallbackBranch — without
        // this, a runaway fallback (e.g. an explorer that loops re-reading
        // the same files) ran with the agent's own ``maxTurns`` instead of
        // the user's ``coder.fallback_max_turns`` cap, and we'd burn 50+
        // turns before either failing validation or hitting the model's own
        // ceiling. The override mirrors the compile-fail / exec-fail path.
        Integer fbMaxTurns = config.getFallbackMaxTurns();
        if (fbMaxTurns != null) {
            fallbackConfig = fallbackConfig.toBuilder().maxTurns(fbMaxTurns).build();
        }

        String noPlanFallbackRef = prefix + "_noplan_fallback";
        WorkflowTask fallbackTask =
                agentCompiler.compileSubAgent(
                        fallbackConfig,
                        noPlanFallbackRef,
                        "${" + npPromptRef + ".output.result}",
                        "${workflow.input.media}",
                        "${workflow.variables.context}");
        tasks.add(fallbackTask);

        // /dg #5: terminal SET_VARIABLE so the output selector reads
        // ``workflow.variables.final_result`` instead of pattern-matching
        // unresolved ``${_noplan_fallback.output.result}``.
        WorkflowTask noPlanSet = new WorkflowTask();
        noPlanSet.setType("SET_VARIABLE");
        noPlanSet.setTaskReferenceName(prefix + "_noplan_fallback_set");
        noPlanSet.setInputParameters(
                Map.of("final_result", "${" + noPlanFallbackRef + ".output.result}"));
        tasks.add(noPlanSet);

        return tasks;
    }
}
