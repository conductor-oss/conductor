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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.ModelParser;
import org.conductoross.conductor.common.metadata.agent.ModelParser.ParsedModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * Compiles a LangGraph-style node/edge graph (an {@code AgentConfig} with a {@code
 * _graph_structure} metadata entry) into Conductor FORK_JOIN / FORK_JOIN_DYNAMIC / DO_WHILE /
 * SWITCH primitives.
 */
class GraphStructureCompiler {

    private static final Logger log = LoggerFactory.getLogger(GraphStructureCompiler.class);

    private final AgentCompiler agentCompiler;

    GraphStructureCompiler(AgentCompiler agentCompiler) {
        this.agentCompiler = agentCompiler;
    }

    /**
     * Find the join point where all fork branches reconverge. Returns the first node reachable from
     * ALL branch targets, or null.
     */
    private static String findJoinPoint(
            List<String> forkTargets, Map<String, List<String>> adjacency) {
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
    private static WorkflowTask buildForkMergeTask(
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
            WorkflowDef subWf = agentCompiler.compile(subAgent);
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
        if (retryPolicy != null) AgentCompiler.applyRetryPolicy(prepTask, retryPolicy);
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

    static boolean appliesTo(AgentConfig config) {
        return config.getMetadata() != null
                && config.getMetadata().get("_graph_structure") instanceof Map;
    }

    @SuppressWarnings("unchecked")
    WorkflowDef compile(AgentConfig config) {
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
                AgentCompiler.applyRetryPolicy(nodeTask, retryPolicies.get(nodeName));
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
                                agentCompiler.buildDoWhile(
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
        wf.setInputParameters(new ArrayList<>(AgentCompiler.WORKFLOW_INPUTS));
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
}
