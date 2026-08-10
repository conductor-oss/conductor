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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.GuardrailCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.conductoross.conductor.ai.agentspan.runtime.util.SafeConditionInterpreter;
import org.conductoross.conductor.ai.agentspan.runtime.util.SafeConditionParseException;
import org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowTaskUtils;
import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * System task that compiles an LLM-produced plan (JSON describing tools, args, dependencies, and
 * validation steps) into a fully-formed Conductor {@code WorkflowDef} that downstream {@code
 * SUB_WORKFLOW} (or {@code DYNAMIC_FORK}) tasks can execute.
 *
 * <h3>Input</h3>
 *
 * <ul>
 *   <li>{@code planJson} — the plan as JSON string or already-parsed Map
 *   <li>{@code parentName} — used to derive the compiled workflow's name
 *   <li>{@code model} — default LLM model for any {@code generate} ops
 *   <li>{@code harnessTimeoutSeconds} — propagated to compiled WorkflowDef.timeoutSeconds
 * </ul>
 *
 * <h3>Output</h3>
 *
 * <pre>{@code
 * {
 *   "workflowDef": { ... } | null,        // valid Conductor WorkflowDef when error is null
 *   "workflowName": "pe_<parent>_plan",
 *   "error": null | "human readable",     // non-null on validation failure
 *   "warnings": [ "..." ],
 *   "stats": { "stepCount": N, "taskCount": M }
 * }
 * }</pre>
 *
 * <p>Validation failures complete the task with status {@code COMPLETED} and a non-null {@code
 * error} field. Downstream SWITCH routes on {@code ${plan_and_compile.output.error}} so retry
 * semantics stay simple.
 *
 * <p>Statically-typed Java replacement for the previous GraalJS-string compiler — fully
 * unit-testable without a Graal context.
 */
public class PlanAndCompileTask extends WorkflowSystemTask {

    public static final String TASK_TYPE = "PLAN_AND_COMPILE";

    private static final Logger logger = LoggerFactory.getLogger(PlanAndCompileTask.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Keys that imply an LLM emitted a real JSON Schema instead of an instance shape. */
    private static final List<String> SCHEMA_LIKE_KEYS =
            Arrays.asList(
                    "$schema",
                    "properties",
                    "required",
                    "additionalProperties",
                    "definitions",
                    "$defs",
                    "$ref",
                    "allOf",
                    "anyOf",
                    "oneOf",
                    "patternProperties");

    public PlanAndCompileTask() {
        super(TASK_TYPE);
        logger.debug("PlanAndCompileTask registered (task type={})", TASK_TYPE);
    }

    // -----------------------------------------------------------------------
    //  Entry point
    // -----------------------------------------------------------------------

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Map<String, Object> input = task.getInputData() == null ? Map.of() : task.getInputData();

        Object planJsonRaw = input.get("planJson");
        String parentName = stringOr(input.get("parentName"), "plan");
        String model = stringOr(input.get("model"), "openai/gpt-4o-mini");
        int harnessTimeout = intOr(input.get("harnessTimeoutSeconds"), 600);
        if (harnessTimeout <= 0) harnessTimeout = 600;

        // Optional allowlist of tool names. Empty/null disables the check
        // (the plan compiles regardless of tool names — legacy callers).
        // The recommended path is for compilePlanExecute to pass the parent
        // agent's ``tools`` list here, so unknown names route to fallback
        // instead of compiling into a SCHEDULED-forever SIMPLE task.
        Set<String> knownToolNames = parseKnownToolNames(input.get("knownToolNames"));

        // Full tool configs (with guardrails). Built into a name→ToolConfig
        // lookup map so each emitted SIMPLE can be wrapped with the tool's
        // input guardrails. Without this, plan-mode tool calls bypass the
        // guardrails the LLM-loop path enforces — same call site, same tool,
        // different safety posture. PAC closes that gap by wrapping the
        // SIMPLE in a guardrail gate when the tool declares any.
        Map<String, ToolConfig> parentToolsByName = parseParentTools(input.get("parentTools"));

        String workflowName = "pe_" + parentName.replaceAll("[^a-zA-Z0-9_]", "_") + "_plan";

        Map<String, Object> plan;
        try {
            plan = parsePlan(planJsonRaw);
        } catch (Exception e) {
            completeWithError(task, workflowName, "Invalid plan JSON: " + e.getMessage());
            return;
        }
        if (plan == null) {
            completeWithError(task, workflowName, "Plan must be a JSON object");
            return;
        }

        try {
            CompileResult result =
                    compile(
                            plan,
                            workflowName,
                            model,
                            harnessTimeout,
                            knownToolNames,
                            parentToolsByName);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("workflowDef", result.workflowDef);
            output.put("workflowName", workflowName);
            output.put("error", result.error);
            output.put("warnings", result.warnings);
            output.put("stats", result.stats);
            task.setOutputData(output);
            task.setStatus(TaskModel.Status.COMPLETED);
            if (result.error == null) {
                logger.debug(
                        "PLAN_AND_COMPILE ok: name={} steps={} tasks={}",
                        workflowName,
                        result.stats.get("stepCount"),
                        result.stats.get("taskCount"));
            } else {
                logger.debug("PLAN_AND_COMPILE validation failed: {}", result.error);
            }
        } catch (Exception e) {
            // Truly unexpected — bug in the compiler. Surface as task error.
            logger.error("PLAN_AND_COMPILE crashed for parent={}", parentName, e);
            completeWithError(task, workflowName, "Compiler internal error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  Compilation
    // -----------------------------------------------------------------------

    private static final class CompileResult {
        Map<String, Object> workflowDef;
        String error;
        List<String> warnings = new ArrayList<>();
        Map<String, Object> stats = new LinkedHashMap<>();
    }

    /**
     * Public DTO returned by {@link #inspectPlan(java.util.Map, String, String, int, java.util.Set,
     * java.util.Map)} — gives external callers (the inspect-plan REST endpoint, /dg #6) visibility
     * into PAC's compile output without dispatching the SUB_WORKFLOW.
     *
     * <p>Mirrors the fields ``start()`` puts on the task's outputData. The inner {@code
     * workflowDef} is the same Conductor {@code WorkflowDef} shape that would be templated into
     * ``subWorkflowParam.workflowDefinition`` at SUB_WORKFLOW dispatch.
     */
    public static final class InspectResult {
        public final Map<String, Object> workflowDef;
        public final String error;
        public final List<String> warnings;
        public final Map<String, Object> stats;

        InspectResult(CompileResult r) {
            this.workflowDef = r.workflowDef;
            this.error = r.error;
            this.warnings = r.warnings == null ? List.of() : List.copyOf(r.warnings);
            this.stats = r.stats == null ? Map.of() : Map.copyOf(r.stats);
        }
    }

    /**
     * /dg #6: inspect what PAC would compile from a given plan without actually dispatching the
     * SUB_WORKFLOW. The REST endpoint at {@code POST /api/agent/inspect-plan} surfaces this through
     * the server's HTTP layer.
     *
     * <p>Same parameter set as the internal {@link #compile(Map, String, String, int, Set, Map)} so
     * the inspect path uses the production compile logic — there's exactly one compiler, not a
     * divergent inspect-only fork.
     */
    public InspectResult inspectPlan(
            Map<String, Object> plan,
            String workflowName,
            String model,
            int harnessTimeout,
            Set<String> knownToolNames,
            Map<String, ToolConfig> parentToolsByName) {
        try {
            CompileResult r =
                    compile(
                            plan,
                            workflowName,
                            model,
                            harnessTimeout,
                            knownToolNames,
                            parentToolsByName);
            return new InspectResult(r);
        } catch (Exception e) {
            CompileResult r = new CompileResult();
            // Use exception class name as fallback when getMessage() is
            // null — NPE etc. have null messages, and "Compiler internal
            // error: null" is useless feedback.
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = e.getClass().getSimpleName();
            }
            r.error = "Compiler internal error: " + msg;
            return new InspectResult(r);
        }
    }

    /**
     * State carried through compilation. Mirrors the JS path's globals. Kept as a per-invocation
     * instance so the task itself remains stateless.
     */
    private static final class CompileCtx {
        final String defaultProvider;
        final String defaultModel;
        final String fullModel;

        /**
         * name → ToolConfig lookup for guardrail wrapping. Empty when the caller didn't pass
         * parentTools (legacy / no-guardrail callers).
         */
        final Map<String, ToolConfig> parentToolsByName;

        int counter = 0;

        /**
         * Maps a wrapper task ref (SWITCH, JOIN) to the actual inner tool ref whose ``.result``
         * should be referenced by downstream consumers. SWITCHes output the case decision and JOINs
         * output a per-branch map, neither of which carry the tool's payload at ``.result``.
         */
        final Map<String, String> innerRefMap = new HashMap<>();

        /**
         * Per-step "primary output template" — the full Conductor expression a downstream {@code
         * Ref("<stepId>")} resolves to. Populated by {@link #emitStepTasks} after a step's tasks
         * are appended.
         *
         * <p>The value carries the right path component per task type, so users always see "the
         * whole output of step X":
         *
         * <ul>
         *   <li>SIMPLE worker (sequential step's last op) → {@code ${ref.output}} — Python
         *       {@code @tool} dicts land at {@code .output} top-level; there is no {@code .result}
         *       wrapping.
         *   <li>INLINE parallel aggregator → {@code ${ref.output.result}} — INLINE wraps its script
         *       return under {@code result}.
         *   <li>SUB_WORKFLOW (agent_tool) → {@code ${ref.output}} — the child workflow's {@code
         *       outputParameters} surface at {@code .output} on the parent task.
         * </ul>
         *
         * Without this per-type discrimination, ``Ref("simple_step")`` resolves to {@code null} (no
         * {@code .result} key on the worker dict) — silently broken.
         */
        final Map<String, String> stepOutputRefs = new HashMap<>();

        String lastOpRef = null;
        String lastAggRef = null;

        CompileCtx(String fullModel, Map<String, ToolConfig> parentToolsByName) {
            this.fullModel = fullModel;
            String[] parts = fullModel.split("/", 2);
            this.defaultProvider = parts.length > 1 ? parts[0] : "openai";
            this.defaultModel = parts.length > 1 ? parts[1] : fullModel;
            this.parentToolsByName = parentToolsByName != null ? parentToolsByName : Map.of();
        }

        String uid(String base) {
            return base + "_" + (counter++);
        }

        /** Return the ref whose ``.result`` actually contains a tool payload. */
        String terminalRef(Map<String, Object> task) {
            String name = (String) task.get("taskReferenceName");
            return innerRefMap.getOrDefault(name, name);
        }
    }

    @SuppressWarnings("unchecked")
    private CompileResult compile(
            Map<String, Object> plan,
            String workflowName,
            String model,
            int harnessTimeout,
            Set<String> knownToolNames,
            Map<String, ToolConfig> parentToolsByName) {
        CompileResult result = new CompileResult();

        Object stepsObj = plan.get("steps");
        if (!(stepsObj instanceof List) || ((List<?>) stepsObj).isEmpty()) {
            result.error = "Plan must have a non-empty steps array";
            return result;
        }
        List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsObj;

        // Pass 1 — auto-id missing steps. Done before dependency validation so
        // depends_on can resolve to the auto-ids.
        List<String> errors = new ArrayList<>();
        Set<String> stepIds = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            Object idObj = s.get("id");
            String id = idObj == null ? null : String.valueOf(idObj);
            if (id == null || id.isEmpty()) {
                id = "step_" + i;
                s.put("id", id);
                result.warnings.add("Auto-generated id for step at index " + i + ": " + id);
            }
            if (!stepIds.add(id)) {
                errors.add("Duplicate step id: " + id);
            }
        }

        // Build a step-id → parallel flag map for downstream Ref-shape checks.
        // A Ref to a parallel step resolves to an aggregator ARRAY at run
        // time; a Ref to a sequential step resolves to that step's single
        // result. Pass-2 uses this to catch type-mismatches between a
        // producer's output shape and a consumer's declared inputSchema.
        Map<String, Boolean> stepIsParallel = new HashMap<>();
        for (Map<String, Object> s : steps) {
            String sid = String.valueOf(s.get("id"));
            stepIsParallel.put(sid, Boolean.TRUE.equals(s.get("parallel")));
        }

        // Pass 2 — validate operations + filter dangling depends_on. A
        // fabricated dep is harmless: the step still runs in declared order.
        for (Map<String, Object> s : steps) {
            String id = String.valueOf(s.get("id"));
            Object opsObj = s.get("operations");
            if (!(opsObj instanceof List) || ((List<?>) opsObj).isEmpty()) {
                errors.add("Step " + id + " has no operations");
            } else {
                List<Map<String, Object>> ops = (List<Map<String, Object>>) opsObj;
                for (int oi = 0; oi < ops.size(); oi++) {
                    Map<String, Object> op = ops.get(oi);
                    String toolName = op.get("tool") instanceof String ts ? ts : null;
                    if (toolName == null || toolName.isEmpty()) {
                        errors.add("Step " + id + " op " + oi + " missing tool");
                    } else if (!knownToolNames.isEmpty() && !knownToolNames.contains(toolName)) {
                        // Allowlist check: caller passed a non-empty
                        // ``knownToolNames`` and this tool is not in it.
                        // Hallucinated tool names (e.g. Claude emitting
                        // ``str_replace`` from training memory) end up here
                        // instead of compiling into a SCHEDULED-forever
                        // SIMPLE task. The compile-fail SWITCH then routes
                        // to the fallback agent.
                        errors.add(
                                "Step "
                                        + id
                                        + " op "
                                        + oi
                                        + " uses unknown tool '"
                                        + toolName
                                        + "'");
                    }
                    if (op.get("args") == null && op.get("generate") == null) {
                        errors.add("Step " + id + " op " + oi + " needs args or generate");
                    }
                    // Parallel-Ref shape check: a top-level args.<argName> =
                    // {"$ref": "<step>"} where <step> is parallel produces
                    // an array; the consumer's inputSchema must accept array
                    // (or be missing/unspecified, in which case we don't
                    // know the shape and skip the check).
                    if (toolName != null && op.get("args") instanceof Map<?, ?> argsMap) {
                        String mismatch =
                                checkParallelRefShape(
                                        toolName, argsMap, stepIsParallel, parentToolsByName);
                        if (mismatch != null) {
                            errors.add("Step " + id + " op " + oi + " " + mismatch);
                        }
                    }
                }
            }
            Object rawDeps = s.get("depends_on");
            List<String> liveDeps = new ArrayList<>();
            if (rawDeps instanceof List) {
                for (Object d : (List<Object>) rawDeps) {
                    String ds = String.valueOf(d);
                    if (stepIds.contains(ds)) {
                        liveDeps.add(ds);
                    } else {
                        result.warnings.add("Step " + id + " dropped unknown dep: " + ds);
                        // continue (fall through to dropped-dep handling)
                    }
                }
            }
            s.put("depends_on", liveDeps);

            // Cross-step Ref validation: every {"$ref": "<step_id>"} must
            // point at an existing step that's in *this* step's depends_on.
            // Implicit dependencies via Conductor template resolution work,
            // but requiring the explicit depends_on keeps the data flow
            // visible in the plan and lets the scheduler topo-sort correctly.
            Set<String> refTargets = new LinkedHashSet<>();
            Object opsForRefs = s.get("operations");
            if (opsForRefs instanceof List<?>) {
                for (Object o : (List<?>) opsForRefs) {
                    if (o instanceof Map<?, ?> opMap) {
                        collectRefTargets(opMap, refTargets);
                    }
                }
            }
            for (String target : refTargets) {
                if (!stepIds.contains(target)) {
                    errors.add("Step " + id + " has $ref to unknown step '" + target + "'");
                } else if (target.equals(id)) {
                    errors.add("Step " + id + " has self-referential $ref to '" + target + "'");
                } else if (!liveDeps.contains(target)) {
                    errors.add(
                            "Step "
                                    + id
                                    + " $refs step '"
                                    + target
                                    + "' but does not declare it in depends_on");
                }
            }
        }
        if (!errors.isEmpty()) {
            result.error = "Plan validation: " + String.join("; ", errors);
            return result;
        }

        // Validation block: success_condition strings must parse cleanly
        // under SafeConditionInterpreter's whitelist grammar (dg-review F14
        // / recommendation #14). The Java AST parser replaces the old
        // regex denylist + GraalJS pipeline — same string, two layers
        // wasn't real defence in depth. A grammar parser cannot emit a
        // node type outside its whitelist.
        Object validationObj = plan.get("validation");
        List<Map<String, Object>> validations =
                validationObj instanceof List
                        ? (List<Map<String, Object>>) validationObj
                        : List.of();
        for (int vi = 0; vi < validations.size(); vi++) {
            Map<String, Object> v = validations.get(vi);
            Object cond = v.get("success_condition");
            if (cond instanceof String && !((String) cond).isEmpty()) {
                try {
                    SafeConditionInterpreter.parse((String) cond);
                } catch (SafeConditionParseException ex) {
                    result.error =
                            "Validation "
                                    + vi
                                    + " has unsafe success_condition: "
                                    + ex.getMessage();
                    return result;
                }
            }
        }

        // Topological sort. Cycles are a hard error — silent partial DAG
        // emission was the previous behavior and made bad plans look benign.
        List<Map<String, Object>> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        String[] cycle = new String[] {null};
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> s : steps) byId.put(String.valueOf(s.get("id")), s);
        for (Map<String, Object> s : steps) {
            topoVisit(s, byId, visited, visiting, sorted, new ArrayList<>(), cycle);
            if (cycle[0] != null) break;
        }
        if (cycle[0] != null) {
            result.error = "Cycle in depends_on: " + cycle[0];
            return result;
        }

        // Build tasks.
        CompileCtx ctx = new CompileCtx(model, parentToolsByName);
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map<String, Object> step : sorted) {
            String stepError = emitStepTasks(step, ctx, tasks);
            if (stepError != null) {
                result.error = stepError;
                return result;
            }
        }

        // Validation block tasks.
        emitValidationTasks(plan, validations, ctx, tasks);

        // outputParameters: prefer validation aggregator, else last op's
        // terminal ref. Empty fallback if neither (unreachable in practice).
        Map<String, Object> outputParameters = new LinkedHashMap<>();
        String resultSource =
                ctx.lastAggRef != null
                        ? "${" + ctx.lastAggRef + ".output.result}"
                        : (ctx.lastOpRef != null ? "${" + ctx.lastOpRef + ".output.result}" : "");
        outputParameters.put("result", resultSource);
        outputParameters.put(
                "status",
                ctx.lastAggRef != null ? "${" + ctx.lastAggRef + ".output.result}" : "completed");

        Map<String, Object> wfDef = new LinkedHashMap<>();
        wfDef.put("name", workflowName);
        wfDef.put("version", 1);
        wfDef.put("tasks", tasks);
        wfDef.put("outputParameters", outputParameters);
        wfDef.put("timeoutPolicy", "TIME_OUT_WF");
        wfDef.put("timeoutSeconds", harnessTimeout);
        wfDef.put("schemaVersion", 2);

        result.workflowDef = wfDef;
        result.stats.put("stepCount", steps.size());
        result.stats.put("taskCount", tasks.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private void topoVisit(
            Map<String, Object> s,
            Map<String, Map<String, Object>> byId,
            Set<String> visited,
            Set<String> visiting,
            List<Map<String, Object>> sorted,
            List<String> path,
            String[] cycleOut) {
        if (cycleOut[0] != null) return;
        String id = String.valueOf(s.get("id"));
        if (visited.contains(id)) return;
        if (visiting.contains(id)) {
            int idx = path.indexOf(id);
            List<String> cyc = new ArrayList<>(path.subList(idx, path.size()));
            cyc.add(id);
            cycleOut[0] = String.join(" -> ", cyc);
            return;
        }
        visiting.add(id);
        path.add(id);
        List<String> deps = (List<String>) s.getOrDefault("depends_on", List.of());
        for (String d : deps) {
            Map<String, Object> dep = byId.get(d);
            if (dep != null) topoVisit(dep, byId, visited, visiting, sorted, path, cycleOut);
            if (cycleOut[0] != null) return;
        }
        path.remove(path.size() - 1);
        visiting.remove(id);
        visited.add(id);
        sorted.add(s);
    }

    @SuppressWarnings("unchecked")
    private String emitStepTasks(
            Map<String, Object> step, CompileCtx ctx, List<Map<String, Object>> tasks) {
        String stepId = String.valueOf(step.get("id"));
        List<Map<String, Object>> ops = (List<Map<String, Object>>) step.get("operations");
        List<List<Map<String, Object>>> branches = new ArrayList<>();

        for (int oi = 0; oi < ops.size(); oi++) {
            Map<String, Object> op = ops.get(oi);
            String tool = (String) op.get("tool");
            List<Map<String, Object>> chain = new ArrayList<>();

            if (op.get("args") != null) {
                // Static op — emit the task type that matches the tool's
                // toolType (SIMPLE / SUB_WORKFLOW / HTTP / CALL_MCP_TOOL / …)
                // via buildToolTask. Variable name kept as ``simpleTask`` so
                // the downstream guardrail wrap (which calls the local var)
                // continues to read; the wrap is type-agnostic.
                Map<String, Object> sArgs = new LinkedHashMap<>();
                Object argsObj = op.get("args");
                if (argsObj instanceof Map) {
                    sArgs.putAll((Map<String, Object>) argsObj);
                }
                // Rewrite {"$ref": "step_id"} markers to Conductor templates
                // pointing at the upstream step's primary output ref. Must run
                // BEFORE injectAmbient so a Ref-referenced step output can't
                // accidentally collide with an ambient key.
                String refErr = resolveRefs(sArgs, ctx);
                if (refErr != null) {
                    return "Step " + stepId + " op " + oi + ": " + refErr;
                }
                injectAmbient(sArgs);
                String simpleRef = ctx.uid("s_" + stepId);
                Map<String, Object> simpleTask = buildToolTask(tool, sArgs, simpleRef, ctx);

                // Guardrail wrap: if the tool declares input/output guardrails,
                // emit format INLINE → guardrail check → SWITCH(pass→SIMPLE,
                // raise→TERMINATE, …) instead of the bare SIMPLE. Without
                // this, plan-mode tool calls bypass the guardrails the
                // LLM-loop path enforces — see ToolCompiler.buildToolGuardrailGate.
                ToolConfig toolConfig = ctx.parentToolsByName.get(tool);
                List<GuardrailConfig> toolGuardrails =
                        toolConfig != null && toolConfig.getGuardrails() != null
                                ? toolConfig.getGuardrails()
                                : List.of();
                if (!toolGuardrails.isEmpty()) {
                    chain.addAll(
                            emitGuardrailWrappedSimple(
                                    stepId,
                                    oi,
                                    tool,
                                    sArgs,
                                    simpleTask,
                                    simpleRef,
                                    toolGuardrails,
                                    ctx));
                } else {
                    chain.add(simpleTask);
                }
            } else {
                // Generated op: LLM → INLINE parse → SWITCH(parse_error) → SIMPLE tool.
                Map<String, Object> gen = (Map<String, Object>) op.get("generate");
                if (gen == null) {
                    return "Step " + stepId + " op " + oi + " has neither args nor generate";
                }
                String om = stringOr(gen.get("model"), ctx.fullModel);
                String[] parts = om.split("/", 2);
                String prov = parts.length > 1 ? parts[0] : ctx.defaultProvider;
                String mdl = parts.length > 1 ? parts[1] : om;
                double temp =
                        gen.get("temperature") instanceof Number
                                ? ((Number) gen.get("temperature")).doubleValue()
                                : 0d;
                int maxTokens = intOr(gen.get("max_tokens"), 4096);
                String outputSchema = stringOr(gen.get("output_schema"), "{}");
                String instructions = stringOr(gen.get("instructions"), "");
                // Resolve Ref({"$ref": ...}) in the generate-op's context
                // first, so `gen.context = Ref("step_a")` becomes a Conductor
                // template the LLM gets a real value injected into at run
                // time, not the literal `{$ref=step_a}` string.
                Object ctxRaw = gen.get("context");
                if (ctxRaw != null) {
                    // Wrap in a single-key map so resolveRefs can replace.
                    Map<String, Object> wrap = new LinkedHashMap<>();
                    wrap.put("_", ctxRaw);
                    String refErr = resolveRefs(wrap, ctx);
                    if (refErr != null) return "Step " + stepId + " op " + oi + ": " + refErr;
                    ctxRaw = wrap.get("_");
                }
                String contextStr = ctxRaw == null ? null : String.valueOf(ctxRaw);

                String llmRef = ctx.uid("llm_" + stepId);
                String sysMsg =
                        "Output ONLY valid JSON matching this shape: "
                                + outputSchema
                                + ". No markdown fences, no explanation, just the JSON object.";
                StringBuilder userMsg = new StringBuilder(instructions);
                if (contextStr != null) {
                    userMsg.append("\n\nContext:\n").append(contextStr);
                }
                // OpenAI Responses API requires the literal "json" in user
                // messages when text.format=json_object. The system prompt's
                // mention is not sufficient for that check.
                userMsg.append("\n\nRespond as json.");

                Map<String, Object> llmInputs = new LinkedHashMap<>();
                llmInputs.put("llmProvider", prov);
                llmInputs.put("model", mdl);
                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "message", sysMsg));
                messages.add(Map.of("role", "user", "message", userMsg.toString()));
                llmInputs.put("messages", messages);
                llmInputs.put("maxTokens", maxTokens);
                llmInputs.put("temperature", temp);
                llmInputs.put("jsonOutput", true);

                Map<String, Object> llmTask = new LinkedHashMap<>();
                llmTask.put("name", "llm_chat_complete");
                llmTask.put("taskReferenceName", llmRef);
                llmTask.put("type", "LLM_CHAT_COMPLETE");
                llmTask.put("inputParameters", llmInputs);
                llmTask.put("retryCount", 1);
                llmTask.put("retryLogic", "FIXED");
                llmTask.put("retryDelaySeconds", 1);
                chain.add(llmTask);

                String parseRef = ctx.uid("p_" + stepId);
                Map<String, Object> parseInputs = new LinkedHashMap<>();
                parseInputs.put("evaluatorType", "graaljs");
                parseInputs.put("llmOut", "${" + llmRef + ".output.result}");
                // /dg #10: extracted from a Java-source string literal into
                // ``JavaScriptBuilder.parseLlmOutputScript()`` so quoting
                // bugs are pinned to one method and tested in isolation.
                parseInputs.put("expression", JavaScriptBuilder.parseLlmOutputScript());
                Map<String, Object> parseTask = new LinkedHashMap<>();
                parseTask.put("name", "INLINE_TASK");
                parseTask.put("taskReferenceName", parseRef);
                parseTask.put("type", "INLINE");
                parseTask.put("inputParameters", parseInputs);
                chain.add(parseTask);

                // dg-review F3 / recommendation #12: validate the parsed
                // LLM output against the consumer tool's inputSchema BEFORE
                // it flows into the SIMPLE's inputParameters. Without this
                // gate, an LLM emitting {"path":"/etc/passwd"} for a
                // write_file op flowed straight to the worker — the
                // ``output_schema`` field was documentation, not a
                // contract. Now: any divergence from the tool's declared
                // inputSchema produces a __parse_error with the field
                // path + the violated rule, and the same parseGate SWITCH
                // routes the whole op to the err branch.
                //
                // ``schemaJson`` is the tool's input schema; when the tool
                // is unknown to PAC (legacy callers without parentTools)
                // or has no schema, the validator is a no-op pass-through.
                ToolConfig vToolConfig = ctx.parentToolsByName.get(tool);
                Map<String, Object> validateInputs = new LinkedHashMap<>();
                validateInputs.put("evaluatorType", "graaljs");
                validateInputs.put("parsed", "${" + parseRef + ".output.result}");
                validateInputs.put(
                        "schema",
                        vToolConfig != null && vToolConfig.getInputSchema() != null
                                ? vToolConfig.getInputSchema()
                                : Map.of());
                validateInputs.put("expression", JavaScriptBuilder.schemaValidatorScript());
                String validateRef = ctx.uid("v_" + stepId);
                Map<String, Object> validateTask = new LinkedHashMap<>();
                validateTask.put("name", "INLINE_TASK");
                validateTask.put("taskReferenceName", validateRef);
                validateTask.put("type", "INLINE");
                validateTask.put("inputParameters", validateInputs);
                chain.add(validateTask);

                // Build LLM-driven tool inputs from output_schema (instance shape),
                // then injectAmbient as forced overrides.
                Map<String, Object> toolInputs = new LinkedHashMap<>();
                String schemaErr = null;
                try {
                    Object parsedSchema = MAPPER.readValue(outputSchema, Object.class);
                    if (parsedSchema instanceof Map) {
                        Map<String, Object> schemaMap = (Map<String, Object>) parsedSchema;
                        boolean looksLikeSchema = false;
                        for (String k : SCHEMA_LIKE_KEYS) {
                            if (schemaMap.containsKey(k)) {
                                looksLikeSchema = true;
                                break;
                            }
                        }
                        if (looksLikeSchema) {
                            schemaErr =
                                    "output_schema looks like a JSON Schema —"
                                            + " use an instance-shape example object instead,"
                                            + " e.g. {\"path\":\"...\",\"content\":\"...\"}";
                        } else {
                            for (String k : schemaMap.keySet()) {
                                // Source from validateRef so the SIMPLE only
                                // ever receives schema-validated values. On
                                // schema failure the SWITCH below routes to
                                // TERMINATE before this expression is read.
                                toolInputs.put(k, "${" + validateRef + ".output.result." + k + "}");
                            }
                        }
                    } else {
                        schemaErr = "output_schema must be a JSON object";
                    }
                } catch (Exception e) {
                    toolInputs.put("_args", "${" + validateRef + ".output.result}");
                }
                injectAmbient(toolInputs);
                if (schemaErr != null) {
                    return "Step " + stepId + " op " + oi + ": " + schemaErr;
                }

                String toolRef = ctx.uid("t_" + stepId);
                // Same toolType-aware routing as the static-args path —
                // generate-op args come from ``${parseRef.output.result.X}``
                // expressions plus injected ambient keys; buildToolTask
                // reshapes them into the right Conductor task shape.
                Map<String, Object> toolTask = buildToolTask(tool, toolInputs, toolRef, ctx);

                Map<String, Object> termTask = new LinkedHashMap<>();
                termTask.put("name", "TERMINATE_TASK");
                termTask.put("taskReferenceName", ctx.uid("p_term_" + stepId));
                termTask.put("type", "TERMINATE");
                Map<String, Object> termInputs = new LinkedHashMap<>();
                termInputs.put("terminationStatus", "FAILED");
                // The reason interpolates the validator/parse INLINE's
                // __parse_error.reason so the failure mode (parse error vs
                // schema violation, with field path) surfaces in the
                // workflow's reasonForIncompletion.
                termInputs.put(
                        "terminationReason",
                        "LLM output rejected for "
                                + tool
                                + ": "
                                + "${"
                                + validateRef
                                + ".output.result.reason}");
                termTask.put("inputParameters", termInputs);

                Map<String, Object> parseGate = new LinkedHashMap<>();
                String gateRef = ctx.uid("pgate_" + stepId);
                parseGate.put("name", "switch");
                parseGate.put("taskReferenceName", gateRef);
                parseGate.put("type", "SWITCH");
                parseGate.put("evaluatorType", "graaljs");
                parseGate.put(
                        "expression",
                        "(function(){ return $.parsed && $.parsed.__parse_error ? \"err\" : \"ok\"; })()");
                Map<String, Object> gateInputs = new LinkedHashMap<>();
                // SWITCH reads validateRef so a schema-failure produces the
                // same ``err`` route as a parse-failure (both set
                // __parse_error). The downstream TERMINATE's reason string
                // is now slightly misleading on a schema fail (says "JSON
                // parse failed"); the validator stamps a more specific
                // reason into __parse_error.reason, which is the
                // user-debuggable artefact.
                gateInputs.put("parsed", "${" + validateRef + ".output.result}");
                parseGate.put("inputParameters", gateInputs);

                // Generate-op guardrail wrap. Static-arg ops are wrapped at
                // the top of this method; without the same lookup here, the
                // generate path bypassed every parent.tools guardrail —
                // exactly inverted from the threat model (LLM-generated
                // args are the ones most needing a gate). The toolTask
                // inside the parseGate's ok branch goes through the same
                // emitGuardrailWrappedSimple gate as its static cousin.
                ToolConfig genToolConfig = ctx.parentToolsByName.get(tool);
                List<GuardrailConfig> genGuardrails =
                        genToolConfig != null && genToolConfig.getGuardrails() != null
                                ? genToolConfig.getGuardrails()
                                : List.of();
                List<Map<String, Object>> okBranch;
                if (!genGuardrails.isEmpty()) {
                    // For the guardrail to inspect actual generated values
                    // it needs a runtime view of the args — but at compile
                    // time we don't have them. Pass the args map as it
                    // stands (literal keys + ``${parseRef.output.result.X}``
                    // expressions for LLM-supplied values). Conductor will
                    // resolve the expressions before the format INLINE
                    // serialises to JSON, so the guardrail sees real values.
                    okBranch =
                            emitGuardrailWrappedSimple(
                                    stepId,
                                    oi,
                                    tool,
                                    toolInputs,
                                    toolTask,
                                    toolRef,
                                    genGuardrails,
                                    ctx);
                } else {
                    okBranch = List.of(toolTask);
                }
                Map<String, List<Map<String, Object>>> decisionCases = new LinkedHashMap<>();
                decisionCases.put("ok", okBranch);
                parseGate.put("decisionCases", decisionCases);
                parseGate.put("defaultCase", List.of(termTask));

                // Record the inner toolRef so terminalRef() can find the real
                // tool task when something downstream needs ``.result``. The
                // SWITCH itself outputs the case decision, not the payload.
                ctx.innerRefMap.put(gateRef, toolRef);
                chain.add(parseGate);
            }

            if (!chain.isEmpty()) branches.add(chain);
        }

        boolean parallel = Boolean.TRUE.equals(step.get("parallel")) && branches.size() > 1;

        if (parallel) {
            String forkRef = ctx.uid("fork_" + stepId);
            String joinRef = ctx.uid("join_" + stepId);
            List<String> joinOn = new ArrayList<>();
            for (List<Map<String, Object>> b : branches) {
                // joinOn must reach the actual terminal task — the SIMPLE
                // for unguardrailed ops, or the inner SIMPLE inside the
                // guardrail SWITCH/parseGate SWITCH for wrapped ops. Today
                // single-task ``decisionCases`` make joining on the SWITCH
                // ref work transitively (SWITCH completes when its case
                // completes); using terminalRef makes the dependency
                // explicit and prevents future "I added a post-tool task
                // to the case and JOIN now misses it" surprises.
                Map<String, Object> bTerm = b.get(b.size() - 1);
                joinOn.add(ctx.terminalRef(bTerm));
            }
            Map<String, Object> forkTask = new LinkedHashMap<>();
            forkTask.put("name", "fork_join");
            forkTask.put("taskReferenceName", forkRef);
            forkTask.put("type", "FORK_JOIN");
            forkTask.put("forkTasks", branches);
            tasks.add(forkTask);

            Map<String, Object> joinTask = new LinkedHashMap<>();
            joinTask.put("name", "join");
            joinTask.put("taskReferenceName", joinRef);
            joinTask.put("type", "JOIN");
            joinTask.put("joinOn", joinOn);
            tasks.add(joinTask);

            // Aggregate branch results into an array. JOIN's output is
            // ``{taskRef → outputMap}`` with no top-level ``result`` — we need
            // a real ``.result`` so lastOpRef points at something useful.
            String pAggRef = ctx.uid("parallel_agg_" + stepId);
            Map<String, Object> pAggInputs = new LinkedHashMap<>();
            pAggInputs.put("evaluatorType", "graaljs");
            pAggInputs.put("count", branches.size());
            for (int j = 0; j < branches.size(); j++) {
                Map<String, Object> bTerminal = branches.get(j).get(branches.get(j).size() - 1);
                pAggInputs.put("b" + j, "${" + ctx.terminalRef(bTerminal) + ".output.result}");
            }
            pAggInputs.put(
                    "expression",
                    "(function(){ var out = []; for (var i = 0; i < $.count; i++) out.push($['b' + i]); return out; })()");
            Map<String, Object> pAggTask = new LinkedHashMap<>();
            pAggTask.put("name", "INLINE_TASK");
            pAggTask.put("taskReferenceName", pAggRef);
            pAggTask.put("type", "INLINE");
            pAggTask.put("inputParameters", pAggInputs);
            tasks.add(pAggTask);
            ctx.lastOpRef = pAggRef;
            // Parallel step's "primary output" is the aggregator INLINE,
            // which wraps its array under `.output.result`.
            ctx.stepOutputRefs.put(stepId, "${" + pAggRef + ".output.result}");
        } else {
            for (List<Map<String, Object>> b : branches) {
                for (Map<String, Object> t : b) {
                    tasks.add(t);
                    ctx.lastOpRef = ctx.terminalRef(t);
                }
            }
            // Append a "step output" INLINE that normalises the last op's
            // result into a canonical `.output.result` value, regardless of
            // what the op was:
            //   • dict-returning worker — outputData is the dict directly,
            //     no `.result` wrapping (see _dispatch.py:493 in the Python
            //     SDK). Without normalisation, `${ref.output.result}` is
            //     undefined.
            //   • string/scalar-returning worker — _dispatch.py wraps as
            //     `{"result": <value>}`, so `.output.result` does exist.
            //   • INLINE / SUB_WORKFLOW / HTTP / MCP — each task type has
            //     its own conventional output shape; we don't want to bake
            //     that into the Ref resolver.
            // The INLINE picks the right one with a single fallback rule:
            // "if the upstream task has a `.result` key, use it; otherwise
            // use the whole `.output` map." Downstream `Ref("<stepId>")`
            // always resolves to `${step_output_<stepId>.output.result}`
            // which carries the user-visible payload.
            String wrapRef = ctx.uid("step_output_" + stepId);
            Map<String, Object> wrapInputs = new LinkedHashMap<>();
            wrapInputs.put("evaluatorType", "graaljs");
            wrapInputs.put("simpleResult", "${" + ctx.lastOpRef + ".output.result}");
            wrapInputs.put("fullOutput", "${" + ctx.lastOpRef + ".output}");
            wrapInputs.put(
                    "expression",
                    "(function(){"
                            + " var r = $.simpleResult;"
                            + " if (r !== null && r !== undefined && r !== '') return r;"
                            + " return $.fullOutput;"
                            + " })()");
            Map<String, Object> wrapTask = new LinkedHashMap<>();
            wrapTask.put("name", "INLINE_TASK");
            wrapTask.put("taskReferenceName", wrapRef);
            wrapTask.put("type", "INLINE");
            wrapTask.put("inputParameters", wrapInputs);
            tasks.add(wrapTask);
            // The wrap INLINE is now the step's "primary output" — Refs
            // resolve to `${wrapRef.output.result}`. Don't advance
            // ctx.lastOpRef (its consumers want the raw last op's terminal).
            ctx.stepOutputRefs.put(stepId, "${" + wrapRef + ".output.result}");
        }
        return null;
    }

    /**
     * Wrap a tool SIMPLE task with the tool's guardrail gate, sized for deterministic plan
     * execution.
     *
     * <p><b>Shape (single guardrail):</b>
     *
     * <pre>{@code
     * INLINE  format_args    // pre-serialised JSON of the SIMPLE's args
     * INLINE  guardrail_check // (or LLM_CHAT_COMPLETE+INLINE / SIMPLE)
     * SWITCH  guardrail_gate
     *   case "raise" / "retry" / "fix" / "human": TERMINATE
     *   default (pass): SIMPLE tool task           ← only runs when guardrail passed
     * }</pre>
     *
     * <p><b>Multiple guardrails:</b> SWITCHes nest. The outer SWITCH's defaultCase contains the
     * next inner SWITCH; the innermost SWITCH's defaultCase contains the SIMPLE. Each non-pass case
     * still TERMINATEs. The SIMPLE only runs when every guardrail's default fires.
     *
     * <p><b>Why TERMINATE on retry/fix/human in plan mode:</b> in the LLM-loop path, retry feeds
     * back to the next iteration, fix replaces the LLM output, human routes to approve/reject. None
     * of these primitives exist in a deterministic plan: there's no loop to retry into, no LLM
     * output to substitute, and no in-plan way to gate on a human approval before a SIMPLE that's
     * already been compiled. v1 of this gate fails closed for any non-pass case. The previous shape
     * — SIMPLE as an outer sibling that ran "after the SWITCH terminated" — silently bypassed the
     * guardrail when {@link OnFail#RETRY} (the default for {@link RegexGuardrail}) fired. Anyone
     * who didn't explicitly set {@link OnFail#RAISE} got no protection. v1 closes that bypass at
     * the cost of treating retry/fix/human as raise.
     *
     * <p><b>Parallel + guardrails:</b> a guardrail SWITCH that fires TERMINATE inside a {@code
     * FORK_JOIN} branch terminates the whole workflow — sibling parallel branches die mid-flight.
     * This is fail-fast across the step. {@link OnFail#HUMAN} would issue N HumanTasks for N
     * parallel guardrailed ops in the same step (one per branch); v1 collapses these to TERMINATE.
     * v2 (follow-up) restores proper HumanTask routing in the gate's "human" case.
     */
    private List<Map<String, Object>> emitGuardrailWrappedSimple(
            String stepId,
            int opIndex,
            String toolName,
            Map<String, Object> simpleArgs,
            Map<String, Object> simpleTask,
            String simpleRef,
            List<GuardrailConfig> guardrails,
            CompileCtx ctx) {
        List<Map<String, Object>> emitted = new ArrayList<>();
        String baseRef =
                "s_" + stepId + "_" + opIndex + "_" + toolName.replaceAll("[^a-zA-Z0-9_]", "_");
        String agentNameForRefs = "pac_" + baseRef;

        // 1. Format the args as a JSON string for the guardrail to inspect.
        //
        // Per-key runtime iteration: pass the args Map (with whatever
        // Conductor expressions it carries — literal values for static
        // ops, ``${parseRef.output.result.X}`` for generate ops) plus a
        // compile-time-known list of keys. The script reads each key
        // explicitly and assembles a plain JS object before stringifying.
        // This avoids two GraalJS pitfalls in one shot:
        //   (a) ``JSON.stringify(hostMap)`` returns ``"{}"`` because the
        //       Java Map host bridge doesn't expose own-property enumeration.
        //   (b) Generate-op args contain Conductor expressions; those need
        //       to resolve before the guardrail sees them. Pre-serialising
        //       on the Java side would freeze the expression as a literal
        //       string, hiding the actual LLM-generated values.
        //
        // The guardrail sees exactly what the downstream SIMPLE will see
        // (Conductor resolves both inputs identically). One safety
        // consequence: any user-supplied ``${X}`` substring inside an arg
        // value gets resolved before the guardrail runs — that's a
        // Conductor-wide behaviour, not a guardrail-specific one, and
        // pretending the guardrail saw the literal would misrepresent
        // what the worker is about to be invoked with.
        Map<String, Object> userArgs = stripAmbientForGuardrail(simpleArgs);
        List<String> argKeys = new ArrayList<>(userArgs.keySet());
        String formatRef = ctx.uid(baseRef + "_format");
        Map<String, Object> formatTask = new LinkedHashMap<>();
        formatTask.put("name", "INLINE_TASK");
        formatTask.put("taskReferenceName", formatRef);
        formatTask.put("type", "INLINE");
        Map<String, Object> formatInputs = new LinkedHashMap<>();
        formatInputs.put("evaluatorType", "graaljs");
        formatInputs.put("argKeys", argKeys);
        formatInputs.put("args", userArgs);
        formatInputs.put(
                "expression",
                "(function(){"
                        + "  var keys = $.argKeys || []; var a = $.args || {};"
                        + "  var out = {};"
                        + "  for (var i = 0; i < keys.length; i++) { var k = keys[i]; out[k] = a[k]; }"
                        + "  try { return {formatted: JSON.stringify(out)}; }"
                        + "  catch(e) { return {formatted: String(out)}; }"
                        + "})()");
        formatTask.put("inputParameters", formatInputs);
        emitted.add(formatTask);

        // 2. Compile each guardrail's check task(s). Reuse GuardrailCompiler
        // for the regex/llm/custom/external check shapes — those produce
        // ``{passed, on_fail, message}`` outputs we route on. We do NOT use
        // GuardrailCompiler.compileGuardrailRouting; its retry/fix branches
        // are non-terminal (they exist for the LLM-loop's DO_WHILE
        // re-iteration path) and would re-introduce the bypass we just fixed.
        GuardrailCompiler gc = new GuardrailCompiler();
        String contentRef = "${" + formatRef + ".output.result.formatted}";
        List<GuardrailCompiler.GuardrailTaskResult> grResults =
                gc.compileToolGuardrailTasks(guardrails, agentNameForRefs, contentRef);

        // Schedule every check task as a sibling before the SWITCH chain;
        // they're independent (each reads the same content) and Conductor
        // schedules them in order.
        for (GuardrailCompiler.GuardrailTaskResult gr : grResults) {
            for (WorkflowTask t : gr.getTasks()) {
                emitted.add(workflowTaskToMap(t));
            }
        }

        // 3. Build a nested SWITCH chain. Innermost defaultCase is the
        // SIMPLE; each outer SWITCH's defaultCase wraps the next inner
        // SWITCH. Any non-pass branch TERMINATEs.
        List<Map<String, Object>> innerTasks = new ArrayList<>();
        innerTasks.add(simpleTask);
        for (int i = grResults.size() - 1; i >= 0; i--) {
            GuardrailCompiler.GuardrailTaskResult gr = grResults.get(i);
            GuardrailConfig guard = guardrails.get(i);
            String suffix = grResults.size() > 1 ? "_pg_" + i : "_pg";
            String outPath =
                    gr.isInline()
                            ? gr.getRefName() + ".output.result"
                            : gr.getRefName() + ".output";

            // Synthesise a TERMINATE per non-pass branch so each case
            // surfaces a guardrail-specific failure reason instead of a
            // single shared one. The reason field reads the guardrail's own
            // ``message`` from its output.
            Map<String, Object> sw = new LinkedHashMap<>();
            String swRef = agentNameForRefs + "_guardrail_gate" + suffix;
            sw.put("name", "switch");
            sw.put("taskReferenceName", swRef);
            sw.put("type", "SWITCH");
            sw.put("evaluatorType", "value-param");
            sw.put("expression", "switchCaseValue");
            Map<String, Object> swInputs = new LinkedHashMap<>();
            swInputs.put("switchCaseValue", "${" + outPath + ".on_fail}");
            sw.put("inputParameters", swInputs);

            Map<String, List<Map<String, Object>>> cases = new LinkedHashMap<>();
            // Emit only the cases that are reachable given this guardrail's
            // configured on_fail. ``raise`` is always present as the catch-all
            // — retry-exhaustion, fix coerced to raise by the regex/llm script,
            // and any unexpected on_fail value all flow through here. The
            // configured-specific case is added on top so the per-case
            // TERMINATE message + refName reflects the actual policy that
            // triggered the block (better UX in workflow inspectors).
            //
            // All non-pass cases TERMINATE in plan-mode v1: there's no LLM
            // loop to feed retry feedback into, no LLM output to substitute
            // for ``fix``, and no in-plan way to await a human approval. The
            // fallback agent (configured on the PLAN_EXECUTE harness) is the
            // adaptive recovery path for plan-mode guardrail trips.
            String onFail = guard.getOnFail() != null ? guard.getOnFail().toLowerCase() : "raise";
            cases.put(
                    "raise",
                    List.of(buildGuardrailTerminate(agentNameForRefs, "raise" + suffix, outPath)));
            if ("retry".equals(onFail)) {
                cases.put(
                        "retry",
                        List.of(
                                buildGuardrailTerminate(
                                        agentNameForRefs, "retry" + suffix, outPath)));
            } else if ("fix".equals(onFail)) {
                cases.put(
                        "fix",
                        List.of(
                                buildGuardrailTerminate(
                                        agentNameForRefs, "fix" + suffix, outPath)));
            } else if ("human".equals(onFail)) {
                cases.put(
                        "human",
                        List.of(
                                buildGuardrailTerminate(
                                        agentNameForRefs, "human" + suffix, outPath)));
            }
            sw.put("decisionCases", cases);
            // defaultCase = pass — wrap the inner SIMPLE (or the next inner SWITCH).
            sw.put("defaultCase", new ArrayList<>(innerTasks));

            // Map the SWITCH's ref → inner SIMPLE ref so terminalRef() can
            // resolve when this guardrailed op is the final task in a
            // sequential chain or a parallel branch. Without this, a
            // downstream parallel_agg would read ``${gateRef.output.result}``
            // — the SWITCH case decision, not the tool's payload.
            ctx.innerRefMap.put(swRef, simpleRef);

            innerTasks = new ArrayList<>();
            innerTasks.add(sw);
            // Suppress duplicate-key warning by ignoring the (intentional)
            // unused ``guard`` reference; kept above for clarity / future
            // per-guardrail policy hooks.
            if (guard == null) {
                /* unreachable */
            }
        }

        // 4. Add the outermost SWITCH (which contains the nested chain).
        emitted.addAll(innerTasks);
        return emitted;
    }

    /** Build a TERMINATE task whose reason carries the guardrail message. */
    private Map<String, Object> buildGuardrailTerminate(
            String agentName, String suffix, String guardrailOutPath) {
        Map<String, Object> term = new LinkedHashMap<>();
        term.put("name", "TERMINATE_TASK");
        term.put("taskReferenceName", agentName + "_guardrail_term_" + suffix);
        term.put("type", "TERMINATE");
        Map<String, Object> termInputs = new LinkedHashMap<>();
        termInputs.put("terminationStatus", "FAILED");
        termInputs.put("terminationReason", "${" + guardrailOutPath + ".message}");
        term.put("inputParameters", termInputs);
        return term;
    }

    /** Strip ambient-injection keys before showing args to a guardrail. */
    private static Map<String, Object> stripAmbientForGuardrail(Map<String, Object> args) {
        Map<String, Object> clean = new LinkedHashMap<>(args);
        clean.remove("session_id");
        clean.remove("cwd");
        clean.remove("credentials");
        clean.remove("media");
        return clean;
    }

    /**
     * Convert a Conductor {@link WorkflowTask} to a serialisable Map.
     *
     * <p>Backfills task names via {@link WorkflowTaskUtils#ensureTaskName} before serialisation.
     * Without this, {@link GuardrailCompiler}-emitted tasks (which leave {@code name=null}) trip
     * Conductor's WorkflowSweeper with {@code NullPointerException: TaskDef name cannot be null}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> workflowTaskToMap(WorkflowTask t) {
        WorkflowTaskUtils.ensureTaskName(t);
        return MAPPER.convertValue(t, LinkedHashMap.class);
    }

    @SuppressWarnings("unchecked")
    private void emitValidationTasks(
            Map<String, Object> plan,
            List<Map<String, Object>> validations,
            CompileCtx ctx,
            List<Map<String, Object>> tasks) {
        if (validations.isEmpty()) return;

        List<List<Map<String, Object>>> valChains = new ArrayList<>();
        List<String> evalRefs = new ArrayList<>();
        // For single-validator plans we emit val_eval with a STRING shape
        // ("passed"/"failed") and skip the val_agg INLINE entirely. The
        // SWITCH below + the workflow's outputParameters both consume a
        // string identically, so the {passed: bool} Map shape is wasted
        // ceremony when count=1. count>1 still uses the Map shape so
        // val_agg can inspect each branch's pass status.
        boolean singleValidator = validations.size() == 1;

        for (Map<String, Object> v : validations) {
            String vTool = stringOr(v.get("tool"), "");
            String vRef = ctx.uid("val");
            Map<String, Object> vArgs = new LinkedHashMap<>();
            if (v.get("args") instanceof Map) {
                vArgs.putAll((Map<String, Object>) v.get("args"));
            }
            injectAmbient(vArgs);
            // Validators also route by toolType — a validator backed by an
            // agent_tool (e.g. a judge agent) needs SUB_WORKFLOW; an
            // mcp-backed validator needs CALL_MCP_TOOL.
            Map<String, Object> simpleTask = buildToolTask(vTool, vArgs, vRef, ctx);

            String evalRef = ctx.uid("val_eval");
            String evalExpr;
            Object cond = v.get("success_condition");
            if (cond instanceof String && !((String) cond).isEmpty()) {
                String c = (String) cond;
                if (singleValidator) {
                    evalExpr =
                            "(function(){"
                                    + "  var raw = $.toolOut;"
                                    + "  var out; try { out = typeof raw === 'string' ? JSON.parse(raw) : (raw || {}); } catch(e) { out = raw; }"
                                    + "  try { var ok = (function($){ return ("
                                    + c
                                    + "); })(out);"
                                    + "  return ok ? 'passed' : 'failed'; } catch(e) { return 'failed'; }"
                                    + "})()";
                } else {
                    evalExpr =
                            "(function(){"
                                    + "  var raw = $.toolOut;"
                                    + "  var out; try { out = typeof raw === 'string' ? JSON.parse(raw) : (raw || {}); } catch(e) { out = raw; }"
                                    + "  try { var ok = (function($){ return ("
                                    + c
                                    + "); })(out);"
                                    + "  return {passed: !!ok}; } catch(e) { return {passed: false, reason: 'condition error: ' + e.message}; }"
                                    + "})()";
                }
            } else {
                if (singleValidator) {
                    evalExpr =
                            "(function(){"
                                    + "  var raw = $.toolOut;"
                                    + "  if (raw == null) return 'failed';"
                                    + "  var d; try { d = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch(e) { d = raw; }"
                                    + "  if (typeof d === 'object' && d !== null && d.passed === false) return 'failed';"
                                    + "  if (typeof d === 'string' && d.indexOf('ERROR') >= 0) return 'failed';"
                                    + "  return 'passed';"
                                    + "})()";
                } else {
                    evalExpr =
                            "(function(){"
                                    + "  var raw = $.toolOut;"
                                    + "  if (raw == null) return {passed: false, reason: 'null output'};"
                                    + "  var d; try { d = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch(e) { d = raw; }"
                                    + "  if (typeof d === 'object' && d !== null && d.passed === false) return {passed: false, reason: d.reason || 'passed=false'};"
                                    + "  if (typeof d === 'string' && d.indexOf('ERROR') >= 0) return {passed: false, reason: d};"
                                    + "  return {passed: true};"
                                    + "})()";
                }
            }
            Map<String, Object> evalInputs = new LinkedHashMap<>();
            evalInputs.put("evaluatorType", "graaljs");
            evalInputs.put("toolOut", "${" + vRef + ".output.result}");
            evalInputs.put("expression", evalExpr);
            Map<String, Object> evalTask = new LinkedHashMap<>();
            evalTask.put("name", "INLINE_TASK");
            evalTask.put("taskReferenceName", evalRef);
            evalTask.put("type", "INLINE");
            evalTask.put("inputParameters", evalInputs);

            List<Map<String, Object>> pair = new ArrayList<>();
            pair.add(simpleTask);
            pair.add(evalTask);
            valChains.add(pair);
            evalRefs.add(evalRef);
        }

        if (valChains.size() > 1) {
            String forkRef = ctx.uid("val_fork");
            String joinRef = ctx.uid("val_join");
            List<String> joinOn = new ArrayList<>();
            for (List<Map<String, Object>> chain : valChains) {
                joinOn.add((String) chain.get(chain.size() - 1).get("taskReferenceName"));
            }
            Map<String, Object> forkTask = new LinkedHashMap<>();
            forkTask.put("name", "val_fork");
            forkTask.put("taskReferenceName", forkRef);
            forkTask.put("type", "FORK_JOIN");
            forkTask.put("forkTasks", valChains);
            tasks.add(forkTask);

            Map<String, Object> joinTask = new LinkedHashMap<>();
            joinTask.put("name", "val_join");
            joinTask.put("taskReferenceName", joinRef);
            joinTask.put("type", "JOIN");
            joinTask.put("joinOn", joinOn);
            tasks.add(joinTask);
        } else {
            tasks.add(valChains.get(0).get(0));
            tasks.add(valChains.get(0).get(1));
        }

        // Aggregator: collapse N validator results into "passed"/"failed".
        //
        // For count=1 (the common single-validator case), val_eval and
        // val_agg do almost the same work — eval normalises to {passed,
        // reason}, agg then re-checks ``passed`` and emits the string. Skip
        // the agg INLINE; the SWITCH below reads ``${val_eval.output.result.passed}``
        // directly and value-matches "true"/"false" (Conductor toString()'s
        // booleans for value-param SWITCH). Saves one INLINE per plan.
        String aggRef;
        if (evalRefs.size() == 1) {
            aggRef = evalRefs.get(0);
            ctx.lastAggRef = aggRef;
            // No agg INLINE emitted; vsw below switches on .passed (boolean).
        } else {
            aggRef = ctx.uid("val_agg");
            ctx.lastAggRef = aggRef;
            Map<String, Object> aggInputs = new LinkedHashMap<>();
            aggInputs.put("evaluatorType", "graaljs");
            aggInputs.put("count", evalRefs.size());
            for (int i = 0; i < evalRefs.size(); i++) {
                aggInputs.put("v" + i, "${" + evalRefs.get(i) + ".output.result}");
            }
            aggInputs.put(
                    "expression",
                    "(function(){ "
                            + "var all = true; "
                            + "for (var i = 0; i < $.count; i++) { "
                            + "  var r = $['v' + i]; "
                            + "  if (r == null) { all = false; continue; } "
                            + "  var d; try { d = typeof r === 'string' ? JSON.parse(r) : r; } catch(e) { d = r; } "
                            + "  if (typeof d === 'object' && d !== null && d.passed === false) all = false; "
                            + "  else if (typeof d === 'string' && d.indexOf('ERROR') >= 0) all = false; "
                            + "} "
                            + "return all ? 'passed' : 'failed'; "
                            + "})()");
            Map<String, Object> aggTask = new LinkedHashMap<>();
            aggTask.put("name", "INLINE_TASK");
            aggTask.put("taskReferenceName", aggRef);
            aggTask.put("type", "INLINE");
            aggTask.put("inputParameters", aggInputs);
            tasks.add(aggTask);
        }

        // Build on_success / on_failure branches.
        List<Map<String, Object>> onSuccess = new ArrayList<>();
        Object saObj = plan.get("on_success");
        if (saObj instanceof List) {
            for (Map<String, Object> sAct : (List<Map<String, Object>>) saObj) {
                Map<String, Object> sActArgs = new LinkedHashMap<>();
                if (sAct.get("args") instanceof Map) {
                    sActArgs.putAll((Map<String, Object>) sAct.get("args"));
                }
                injectAmbient(sActArgs);
                // on_success actions follow the same toolType routing as
                // step operations — no silent SIMPLE for agent_tool/mcp/http.
                Map<String, Object> okTask =
                        buildToolTask(
                                String.valueOf(sAct.get("tool")), sActArgs, ctx.uid("ok"), ctx);
                onSuccess.add(okTask);
            }
        }
        List<Map<String, Object>> onFailure = new ArrayList<>();
        Object faObj = plan.get("on_failure");
        if (faObj instanceof List) {
            for (Map<String, Object> fAct : (List<Map<String, Object>>) faObj) {
                Map<String, Object> fActArgs = new LinkedHashMap<>();
                if (fAct.get("args") instanceof Map) {
                    fActArgs.putAll((Map<String, Object>) fAct.get("args"));
                }
                injectAmbient(fActArgs);
                Map<String, Object> failTask =
                        buildToolTask(
                                String.valueOf(fAct.get("tool")), fActArgs, ctx.uid("fail"), ctx);
                onFailure.add(failTask);
            }
        }
        Map<String, Object> termTask = new LinkedHashMap<>();
        termTask.put("name", "TERMINATE_TASK");
        termTask.put("taskReferenceName", ctx.uid("term"));
        termTask.put("type", "TERMINATE");
        Map<String, Object> termInputs = new LinkedHashMap<>();
        termInputs.put("terminationStatus", "FAILED");
        termInputs.put("terminationReason", "Plan validation failed");
        termTask.put("inputParameters", termInputs);
        onFailure.add(termTask);

        // Conductor SWITCH falls through to defaultCase when the matched
        // case branch is EMPTY. With the common ``onSuccess`` empty case,
        // val_agg='passed' would land in defaultCase and TERMINATE — a
        // fail-closed bug dressed up as a feature. Insert a SET_VARIABLE
        // sentinel — Conductor system task, no JS engine, no worker. The
        // earlier shape was an INLINE returning a literal map; SET_VARIABLE
        // is the right primitive for "do nothing but exist".
        if (onSuccess.isEmpty()) {
            Map<String, Object> noop = new LinkedHashMap<>();
            noop.put("name", "SET_VARIABLE");
            noop.put("taskReferenceName", ctx.uid("ok_noop"));
            noop.put("type", "SET_VARIABLE");
            Map<String, Object> noopInputs = new LinkedHashMap<>();
            noopInputs.put("_validation", "passed");
            noop.put("inputParameters", noopInputs);
            onSuccess.add(noop);
        }

        Map<String, Object> vsw = new LinkedHashMap<>();
        vsw.put("name", "switch");
        vsw.put("taskReferenceName", ctx.uid("vsw"));
        vsw.put("type", "SWITCH");
        vsw.put("evaluatorType", "value-param");
        vsw.put("expression", "switchCaseValue");
        Map<String, Object> vswInputs = new LinkedHashMap<>();
        // ``aggRef`` points at val_agg for count>1 (string output) or
        // directly at val_eval for count=1 (also string output — the eval
        // emits "passed"/"failed" directly when there's no agg). Same
        // SWITCH semantics either way.
        vswInputs.put("switchCaseValue", "${" + aggRef + ".output.result}");
        vsw.put("inputParameters", vswInputs);
        Map<String, List<Map<String, Object>>> vswCases = new LinkedHashMap<>();
        vswCases.put("passed", onSuccess);
        vsw.put("decisionCases", vswCases);
        vsw.put("defaultCase", onFailure);
        tasks.add(vsw);
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /**
     * Recursively rewrite plan-level {@code {"$ref": "<step_id>"}} markers in a value tree into
     * Conductor template strings that reach that step's primary output (whatever {@link
     * CompileCtx#stepOutputRefs} recorded for it). Lets users wire the whole output of one step
     * into the args of another without learning the internal task-ref naming scheme — the SDK-side
     * {@code Ref("step_id")} helper serialises to this exact JSON shape.
     *
     * <p>A bare {@code {"$ref": ...}} dict is replaced with a single string; dicts that mix {@code
     * $ref} with other keys are not unwrapped (treated as data). Lists and nested maps are
     * traversed in place.
     *
     * @return null on success, or an error string when a Ref points at an unknown step. Callers
     *     propagate the error as a plan-validation failure so the workflow doesn't try to compile a
     *     Ref to a missing producer.
     */
    @SuppressWarnings("unchecked")
    private static String resolveRefs(Object node, CompileCtx ctx) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            // {"$ref": "step_id"} with no sibling keys → replaced by parent.
            // Iterate entries; replace nested $ref objects in-place.
            List<String> keys = new ArrayList<>(map.keySet());
            for (String k : keys) {
                Object v = map.get(k);
                if (v instanceof Map<?, ?> vm) {
                    Map<String, Object> child = (Map<String, Object>) vm;
                    if (child.size() == 1 && child.containsKey("$ref")) {
                        Object refTarget = child.get("$ref");
                        if (!(refTarget instanceof String stepId) || stepId.isEmpty()) {
                            return "$ref must be a non-empty string, got: " + refTarget;
                        }
                        String template = ctx.stepOutputRefs.get(stepId);
                        if (template == null) {
                            return "$ref points at unknown step: '"
                                    + stepId
                                    + "' (must be in depends_on and exist in the plan)";
                        }
                        map.put(k, template);
                        continue;
                    }
                    String err = resolveRefs(child, ctx);
                    if (err != null) return err;
                } else if (v instanceof List<?> vl) {
                    String err = resolveRefs(vl, ctx);
                    if (err != null) return err;
                }
            }
            return null;
        }
        if (node instanceof List<?> list) {
            List<Object> mutList = (List<Object>) list;
            for (int i = 0; i < mutList.size(); i++) {
                Object v = mutList.get(i);
                if (v instanceof Map<?, ?> vm) {
                    Map<String, Object> child = (Map<String, Object>) vm;
                    if (child.size() == 1 && child.containsKey("$ref")) {
                        Object refTarget = child.get("$ref");
                        if (!(refTarget instanceof String stepId) || stepId.isEmpty()) {
                            return "$ref must be a non-empty string, got: " + refTarget;
                        }
                        String template = ctx.stepOutputRefs.get(stepId);
                        if (template == null) {
                            return "$ref points at unknown step: '" + stepId + "'";
                        }
                        mutList.set(i, template);
                        continue;
                    }
                    String err = resolveRefs(child, ctx);
                    if (err != null) return err;
                } else if (v instanceof List<?> vl) {
                    String err = resolveRefs(vl, ctx);
                    if (err != null) return err;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Top-level shape check: when an op's {@code args.<argName>} is a direct {@code $ref} to a
     * parallel step, the consumer's tool inputSchema must declare the arg as {@code type: "array"}
     * (or omit the type — we only fail on a known-bad mismatch). Returns a diagnostic suffix when
     * the mismatch is detectable, or null when the shape is fine or unknown.
     *
     * <p>This catches the dg-review footgun where a user writes {@code args={"document":
     * Ref("write_all")}} expecting a single dict but the parallel producer returns the FORK_JOIN
     * aggregator array. The error surfaces at compile time with a clear suggestion.
     */
    @SuppressWarnings("unchecked")
    private static String checkParallelRefShape(
            String consumerToolName,
            Map<?, ?> argsMap,
            Map<String, Boolean> stepIsParallel,
            Map<String, ToolConfig> parentToolsByName) {
        ToolConfig consumer = parentToolsByName.get(consumerToolName);
        if (consumer == null) return null; // unknown tool — handled elsewhere
        Map<String, Object> inputSchema = consumer.getInputSchema();
        if (inputSchema == null) return null; // no schema — can't type-check

        Map<String, Object> properties =
                inputSchema.get("properties") instanceof Map<?, ?> p
                        ? (Map<String, Object>) p
                        : null;
        if (properties == null) return null;

        for (Map.Entry<?, ?> entry : argsMap.entrySet()) {
            if (!(entry.getKey() instanceof String argName)) continue;
            if (!(entry.getValue() instanceof Map<?, ?> valMap)) continue;
            // Direct top-level $ref only — nested cases are a different
            // type model (LLM-composed values) and we don't claim to know
            // their shapes.
            if (valMap.size() != 1 || !valMap.containsKey("$ref")) continue;
            Object target = valMap.get("$ref");
            if (!(target instanceof String targetStepId)) continue;
            if (!Boolean.TRUE.equals(stepIsParallel.get(targetStepId))) continue;

            Object propSchema = properties.get(argName);
            if (!(propSchema instanceof Map<?, ?> p2)) continue;
            Object declaredType = ((Map<String, Object>) p2).get("type");
            if (declaredType == null) continue;
            String dt = String.valueOf(declaredType);
            if ("array".equals(dt)) continue; // shape matches — fine.

            return "$refs parallel step '"
                    + targetStepId
                    + "' (output is an array) into arg '"
                    + argName
                    + "' of tool '"
                    + consumerToolName
                    + "' which declares type='"
                    + dt
                    + "' — either remove parallel=true on '"
                    + targetStepId
                    + "', drop the Ref into an array-typed consumer, or aggregate first";
        }
        return null;
    }

    /**
     * Walk a plan op's value tree and collect every {@code $ref} step id reference. Used at plan
     * validation time so we can verify users only Ref steps they've declared in {@code depends_on}.
     */
    @SuppressWarnings("unchecked")
    private static void collectRefTargets(Object node, Set<String> out) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            if (map.size() == 1 && map.containsKey("$ref")) {
                Object t = map.get("$ref");
                if (t instanceof String s && !s.isEmpty()) out.add(s);
                return;
            }
            for (Object v : map.values()) collectRefTargets(v, out);
            return;
        }
        if (node instanceof List<?> list) {
            for (Object v : list) collectRefTargets(v, out);
        }
    }

    /**
     * Forced-override ambient inputs every emitted SIMPLE task receives. Mirrors {@code
     * compileSubAgent} so a tool inside the dynamic plan sees the same execution context the parent
     * harness received. LLM-supplied args cannot redirect these.
     */
    private static void injectAmbient(Map<String, Object> args) {
        // Credentials are runtime metadata, never workflow/task input. Remove this reserved key
        // before adding the ambient execution inputs in case it came from an LLM-generated schema.
        args.remove("credentials");
        args.put("session_id", "${workflow.input.session_id}");
        args.put("cwd", "${workflow.input.cwd}");
        args.put("media", "${workflow.input.media}");
    }

    // credentials remains reserved so an LLM-generated schema cannot smuggle a credential-shaped
    // argument into task input. Credential values are delivered only through Task.runtimeMetadata.
    private static final Set<String> AMBIENT_KEYS =
            Set.of("session_id", "cwd", "credentials", "media");

    /**
     * Build the Conductor task for a single plan op, routed by the tool's {@code toolType}. Mirrors
     * the runtime LLM-loop dispatch in {@link JavaScriptBuilder#enrichToolsScript} so a plan op
     * invokes the same task type the agent-loop would have scheduled for the same tool.
     *
     * <p>Supported toolType → Conductor task type:
     *
     * <ul>
     *   <li>{@code worker} / {@code cli} / (null/unknown) → {@code SIMPLE}
     *   <li>{@code agent_tool} → {@code SUB_WORKFLOW} with {@code subWorkflowParam}; the op's
     *       {@code request} (or fallback {@code prompt}/{@code message}/{@code input}/{@code
     *       query}) field becomes the sub-workflow's {@code prompt} input
     *   <li>{@code http} / {@code api} → {@code HTTP}; op args become the request body;
     *       uri/method/headers come from tool config
     *   <li>{@code mcp} → {@code CALL_MCP_TOOL} ({@code name=call_mcp_tool}); op args become {@code
     *       arguments}, mcpServer + headers come from tool config
     *   <li>{@code human} → {@code HUMAN}
     *   <li>{@code generate_image} / {@code generate_audio} / {@code generate_video} / {@code
     *       generate_pdf} → the matching media task type
     *   <li>{@code rag_index} → {@code LLM_INDEX_TEXT}; {@code rag_search} → {@code
     *       LLM_SEARCH_INDEX}
     *   <li>{@code pull_workflow_messages} → {@code PULL_WORKFLOW_MESSAGES}
     * </ul>
     *
     * <p>Before this method existed, every plan op compiled to a {@code SIMPLE} regardless of
     * toolType. {@code agent_tool} ops then polled for a worker that never existed (the agent's
     * compiled workflow has a different name) and {@code mcp}/{@code http} ops hit the same
     * dead-letter path. The fix is to route at compile time the same way the LLM-loop path routes
     * at run time.
     *
     * @param toolName plan op's {@code tool} field
     * @param args final inputParameters from caller (literal values or Conductor {@code ${...}}
     *     expressions for generate ops); may contain ambient keys which are stripped from nested
     *     payloads (HTTP body, MCP arguments)
     * @param taskRef task reference name to assign
     * @param ctx compile context (provides parentToolsByName)
     * @return the assembled task Map, ready for {@code tasks.add(...)} or {@code
     *     emitGuardrailWrappedSimple} wrapping
     */
    private Map<String, Object> buildToolTask(
            String toolName, Map<String, Object> args, String taskRef, CompileCtx ctx) {
        ToolConfig tc = ctx.parentToolsByName.get(toolName);
        String toolType =
                (tc != null && tc.getToolType() != null && !tc.getToolType().isEmpty())
                        ? tc.getToolType()
                        : "worker";
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg =
                (tc != null && tc.getConfig() != null)
                        ? (Map<String, Object>) (Map<?, ?>) tc.getConfig()
                        : Map.of();

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskReferenceName", taskRef);

        switch (toolType) {
            case "agent_tool":
                {
                    String workflowName =
                            cfg.get("workflowName") instanceof String wn && !wn.isEmpty()
                                    ? wn
                                    : toolName + "_agent_wf";
                    task.put("name", workflowName);
                    task.put("type", "SUB_WORKFLOW");
                    Map<String, Object> subParam = new LinkedHashMap<>();
                    subParam.put("name", workflowName);
                    subParam.put("version", 1);
                    task.put("subWorkflowParam", subParam);
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    inputs.put("prompt", pickPromptField(args));
                    // Preserve the parent session across the sub-workflow boundary.
                    inputs.put("session_id", "${workflow.input.session_id}");
                    task.put("inputParameters", inputs);
                    // Per-tool resilience overrides flow through cfg, matching
                    // ToolCompiler's enrichToolsScript path.
                    if (cfg.containsKey("retryCount")) {
                        task.put("retryCount", cfg.get("retryCount"));
                    } else {
                        task.put("retryCount", 1);
                    }
                    if (cfg.containsKey("retryDelaySeconds")) {
                        task.put("retryDelaySeconds", cfg.get("retryDelaySeconds"));
                    } else {
                        task.put("retryDelaySeconds", 2);
                    }
                    task.put("retryLogic", "FIXED");
                    if (cfg.containsKey("optional")) {
                        task.put("optional", cfg.get("optional"));
                    }
                    return task;
                }
            case "http":
            case "api":
                {
                    task.put("name", toolName);
                    task.put("type", "HTTP");
                    Map<String, Object> req = new LinkedHashMap<>();
                    req.put("uri", cfg.getOrDefault("url", cfg.getOrDefault("base_url", "")));
                    req.put("method", cfg.getOrDefault("method", "GET"));
                    req.put("headers", cfg.getOrDefault("headers", Map.of()));
                    req.put("body", stripAmbient(args));
                    req.put("accept", cfg.getOrDefault("accept", "application/json"));
                    req.put("contentType", cfg.getOrDefault("contentType", "application/json"));
                    req.put("connectionTimeOut", 30000);
                    req.put("readTimeOut", 30000);
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    inputs.put("http_request", req);
                    task.put("inputParameters", inputs);
                    task.put("retryCount", 1);
                    task.put("retryLogic", "FIXED");
                    task.put("retryDelaySeconds", 2);
                    return task;
                }
            case "mcp":
                {
                    task.put("name", "call_mcp_tool");
                    task.put("type", "CALL_MCP_TOOL");
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    inputs.put("mcpServer", cfg.getOrDefault("server_url", ""));
                    inputs.put("method", toolName);
                    inputs.put("arguments", stripAmbient(args));
                    inputs.put("headers", cfg.getOrDefault("headers", Map.of()));
                    task.put("inputParameters", inputs);
                    task.put("retryCount", 1);
                    task.put("retryLogic", "FIXED");
                    task.put("retryDelaySeconds", 2);
                    return task;
                }
            case "human":
                {
                    task.put("name", toolName);
                    task.put("type", "HUMAN");
                    Map<String, Object> hDef = new LinkedHashMap<>();
                    hDef.put("assignmentCompletionStrategy", "LEAVE_OPEN");
                    hDef.put("displayName", toolName);
                    hDef.put("userFormTemplate", Map.of("version", 0));
                    Map<String, Object> inputs = new LinkedHashMap<>(args);
                    inputs.put("__humanTaskDefinition", hDef);
                    task.put("inputParameters", inputs);
                    return task;
                }
            case "generate_image":
            case "generate_audio":
            case "generate_video":
            case "generate_pdf":
                {
                    String taskType = toolType.toUpperCase();
                    task.put("name", toolType);
                    task.put("type", taskType);
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    // cfg defaults first, op args override.
                    for (Map.Entry<String, Object> e : cfg.entrySet())
                        inputs.put(e.getKey(), e.getValue());
                    for (Map.Entry<String, Object> e : args.entrySet())
                        inputs.put(e.getKey(), e.getValue());
                    task.put("inputParameters", inputs);
                    task.put("retryCount", 1);
                    task.put("retryLogic", "FIXED");
                    task.put("retryDelaySeconds", 2);
                    return task;
                }
            case "rag_index":
            case "rag_search":
                {
                    String taskType =
                            "rag_index".equals(toolType) ? "LLM_INDEX_TEXT" : "LLM_SEARCH_INDEX";
                    task.put("name", taskType.toLowerCase());
                    task.put("type", taskType);
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : cfg.entrySet())
                        inputs.put(e.getKey(), e.getValue());
                    for (Map.Entry<String, Object> e : args.entrySet())
                        inputs.put(e.getKey(), e.getValue());
                    task.put("inputParameters", inputs);
                    task.put("retryCount", 1);
                    task.put("retryLogic", "FIXED");
                    task.put("retryDelaySeconds", 2);
                    return task;
                }
            case "pull_workflow_messages":
                {
                    task.put("name", toolName);
                    task.put("type", "PULL_WORKFLOW_MESSAGES");
                    Map<String, Object> inputs = new LinkedHashMap<>(args);
                    if (!inputs.containsKey("batchSize")) {
                        inputs.put("batchSize", cfg.getOrDefault("batchSize", 1));
                    }
                    task.put("inputParameters", inputs);
                    task.put("retryCount", 1);
                    task.put("retryLogic", "FIXED");
                    task.put("retryDelaySeconds", 2);
                    return task;
                }
            case "worker":
            case "cli":
            default:
                {
                    // SIMPLE fallback. Unknown toolType lands here too — preserves
                    // backward compat for any caller that passes an exotic type
                    // we don't yet route (vs. emitting an INLINE error).
                    task.put("name", toolName);
                    task.put("type", "SIMPLE");
                    task.put("inputParameters", args);
                    task.put("retryCount", 1);
                    task.put("retryLogic", "FIXED");
                    task.put("retryDelaySeconds", 2);
                    return task;
                }
        }
    }

    /** Extract the natural prompt/request field from agent_tool args. */
    private static Object pickPromptField(Map<String, Object> args) {
        for (String k : new String[] {"request", "prompt", "message", "input", "query"}) {
            Object v = args.get(k);
            if (v != null) return v;
        }
        return "";
    }

    /** Return a copy of {@code args} with framework ambient keys removed. */
    private static Map<String, Object> stripAmbient(Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            if (!AMBIENT_KEYS.contains(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePlan(Object planJsonRaw) throws Exception {
        if (planJsonRaw == null) return null;
        if (planJsonRaw instanceof Map) return (Map<String, Object>) planJsonRaw;
        String s = String.valueOf(planJsonRaw);
        return MAPPER.readValue(s, Map.class);
    }

    private void completeWithError(TaskModel task, String workflowName, String error) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("workflowDef", null);
        output.put("workflowName", workflowName);
        output.put("error", error);
        output.put("warnings", List.of());
        output.put("stats", Map.of());
        task.setOutputData(output);
        task.setStatus(TaskModel.Status.COMPLETED);
    }

    private static String stringOr(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isEmpty() ? def : s;
    }

    private static int intOr(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    /**
     * Coerce the ``parentTools`` input field (a List of Map representations of {@link ToolConfig})
     * into a name→ToolConfig lookup map. Returns an empty map when no tools were passed (degrades
     * gracefully — guardrail wrapping is then a no-op).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, ToolConfig> parseParentTools(Object raw) {
        Map<String, ToolConfig> byName = new HashMap<>();
        if (!(raw instanceof List<?> list)) return byName;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            try {
                ToolConfig tc = MAPPER.convertValue(m, ToolConfig.class);
                if (tc.getName() != null && !tc.getName().isEmpty()) {
                    byName.put(tc.getName(), tc);
                }
            } catch (Exception e) {
                logger.debug("PAC: skipping unparseable parentTools entry: {}", e.getMessage());
            }
        }
        return byName;
    }

    /**
     * Coerce the ``knownToolNames`` input field (JSON list / Java List) into a Set. Server-side
     * built-in task names that the compiler emits itself are seeded automatically — callers don't
     * need to include them. ``llm_chat_complete`` is the only user-visible built-in: ``generate``
     * ops compile to LLM_CHAT_COMPLETE → INLINE → SIMPLE chains where the LLM step uses that name.
     * Everything else (INLINE_TASK, TERMINATE_TASK, switch, fork_join, join) is wrapper structure,
     * never user-supplied.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> parseKnownToolNames(Object raw) {
        Set<String> names = new HashSet<>();
        // Server-side built-ins always allowed.
        names.add("llm_chat_complete");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    String s = o.toString();
                    if (!s.isEmpty()) names.add(s);
                }
            }
        }
        // If only the built-ins are present (raw was empty/null), treat as
        // disabled — preserves legacy behaviour where any tool name compiles.
        if (names.size() == 1) {
            return new HashSet<>();
        }
        return names;
    }
}
