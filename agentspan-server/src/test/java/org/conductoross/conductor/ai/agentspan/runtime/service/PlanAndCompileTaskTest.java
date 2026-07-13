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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlanAndCompileTask}.
 *
 * <p>Each test drives {@code task.start(workflow, taskModel, executor)} directly — the task does
 * not touch the workflow or executor, so passing a fresh {@link WorkflowModel} and {@code null}
 * executor is sufficient.
 */
class PlanAndCompileTaskTest {

    private final PlanAndCompileTask task = new PlanAndCompileTask();

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> compilePlan(String planJson) {
        Map<String, Object> output = run(planJson, null);
        Object error = output.get("error");
        if (error != null) {
            throw new AssertionError("Plan compilation failed: " + error);
        }
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        assertThat(wf).as("workflowDef should be non-null on success").isNotNull();
        return wf;
    }

    private String compilePlanExpectError(String planJson) {
        Map<String, Object> output = run(planJson, null);
        Object error = output.get("error");
        if (error == null) {
            throw new AssertionError(
                    "Expected compile error but got workflowDef: " + output.get("workflowDef"));
        }
        return String.valueOf(error);
    }

    private Map<String, Object> run(String planJson, Integer harnessTimeoutSeconds) {
        return runWithKnownTools(planJson, harnessTimeoutSeconds, null);
    }

    private Map<String, Object> runWithKnownTools(
            String planJson, Integer harnessTimeoutSeconds, List<String> knownToolNames) {
        return runWithParentTools(planJson, harnessTimeoutSeconds, knownToolNames, null);
    }

    private Map<String, Object> runWithParentTools(
            String planJson,
            Integer harnessTimeoutSeconds,
            List<String> knownToolNames,
            List<Map<String, Object>> parentTools) {
        TaskModel taskModel = new TaskModel();
        Map<String, Object> input = new HashMap<>();
        input.put("planJson", planJson);
        input.put("parentName", "test_harness");
        input.put("model", "openai/gpt-4o-mini");
        if (harnessTimeoutSeconds != null) {
            input.put("harnessTimeoutSeconds", harnessTimeoutSeconds);
        }
        if (knownToolNames != null) {
            input.put("knownToolNames", knownToolNames);
        }
        if (parentTools != null) {
            input.put("parentTools", parentTools);
        }
        taskModel.setInputData(input);
        task.start(new WorkflowModel(), taskModel, null);
        return taskModel.getOutputData();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allTasks(Map<String, Object> wf) {
        List<Map<String, Object>> all = new ArrayList<>();
        collectTasks((List<Map<String, Object>>) wf.get("tasks"), all);
        return all;
    }

    @SuppressWarnings("unchecked")
    private void collectTasks(List<Map<String, Object>> tasks, List<Map<String, Object>> out) {
        if (tasks == null) return;
        for (Map<String, Object> t : tasks) {
            out.add(t);
            String type = String.valueOf(t.get("type"));
            if ("FORK_JOIN".equals(type)) {
                List<List<Map<String, Object>>> forkTasks =
                        (List<List<Map<String, Object>>>) t.get("forkTasks");
                if (forkTasks != null) forkTasks.forEach(branch -> collectTasks(branch, out));
            } else if ("SWITCH".equals(type)) {
                Map<String, List<Map<String, Object>>> decisionCases =
                        (Map<String, List<Map<String, Object>>>) t.get("decisionCases");
                if (decisionCases != null)
                    decisionCases.values().forEach(branch -> collectTasks(branch, out));
                List<Map<String, Object>> defaultCase =
                        (List<Map<String, Object>>) t.get("defaultCase");
                if (defaultCase != null) collectTasks(defaultCase, out);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Validation block — eval task shapes
    // -----------------------------------------------------------------------

    @Test
    void testSuccessConditionProducesEvalInlineTask() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "parallel": false, "operations": [
                    {"tool": "run_cmd", "args": {"command": "echo hello"}}
                  ]}],
                  "validation": [{"tool": "run_tests", "success_condition": "$.exit_code === 0"}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);

        boolean hasEvalTask =
                tasks.stream()
                        .filter(t -> "INLINE".equals(t.get("type")))
                        .anyMatch(
                                t -> {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> inputs =
                                            (Map<String, Object>) t.get("inputParameters");
                                    if (inputs == null) return false;
                                    String expr =
                                            String.valueOf(inputs.getOrDefault("expression", ""));
                                    return expr.contains("exit_code") && expr.contains("passed");
                                });
        assertThat(hasEvalTask).isTrue();

        Map<String, Object> valSimpleTask =
                tasks.stream()
                        .filter(
                                t ->
                                        "SIMPLE".equals(t.get("type"))
                                                && "run_tests".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "No SIMPLE validation task found for run_tests"));
        String simpleRef = (String) valSimpleTask.get("taskReferenceName");

        Map<String, Object> evalTask =
                tasks.stream()
                        .filter(t -> "INLINE".equals(t.get("type")))
                        .filter(
                                t -> {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> inp =
                                            (Map<String, Object>) t.get("inputParameters");
                                    if (inp == null) return false;
                                    String expr =
                                            String.valueOf(inp.getOrDefault("expression", ""));
                                    return expr.contains("exit_code") && expr.contains("passed");
                                })
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> evalInputs = (Map<String, Object>) evalTask.get("inputParameters");
        String toolOutRef = (String) evalInputs.get("toolOut");
        assertThat(toolOutRef).contains(simpleRef).contains(".output.result");
    }

    @Test
    void testNoSuccessConditionUsesDefaultPassCheck() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}],
                  "validation": [{"tool": "check_file"}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        boolean hasDefaultEvalTask =
                tasks.stream()
                        .filter(t -> "INLINE".equals(t.get("type")))
                        .anyMatch(
                                t -> {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> inputs =
                                            (Map<String, Object>) t.get("inputParameters");
                                    if (inputs == null) return false;
                                    return String.valueOf(inputs.getOrDefault("expression", ""))
                                            .contains("passed");
                                });
        assertThat(hasDefaultEvalTask).isTrue();
    }

    @Test
    void testMultipleValidationsUseForkJoin() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}],
                  "validation": [
                    {"tool": "lint", "success_condition": "$.passed === true"},
                    {"tool": "run_tests", "success_condition": "$.exit_code === 0"}
                  ]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topTasks = (List<Map<String, Object>>) wf.get("tasks");

        boolean hasForkJoin = topTasks.stream().anyMatch(t -> "FORK_JOIN".equals(t.get("type")));
        assertThat(hasForkJoin).isTrue();

        Map<String, Object> forkTask =
                topTasks.stream()
                        .filter(t -> "FORK_JOIN".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> forkTasks =
                (List<List<Map<String, Object>>>) forkTask.get("forkTasks");
        assertThat(forkTasks).hasSize(2);
        assertThat(forkTasks.get(0)).hasSize(2);
        assertThat(forkTasks.get(0).get(0).get("type")).isEqualTo("SIMPLE");
        assertThat(forkTasks.get(0).get(1).get("type")).isEqualTo("INLINE");
        assertThat(forkTasks.get(1).get(0).get("type")).isEqualTo("SIMPLE");
        assertThat(forkTasks.get(1).get(1).get("type")).isEqualTo("INLINE");
    }

    @Test
    void testSingleValidationDoesNotUseForkJoin() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}],
                  "validation": [{"tool": "run_tests", "success_condition": "$.exit_code === 0"}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topTasks = (List<Map<String, Object>>) wf.get("tasks");
        boolean hasForkJoin = topTasks.stream().anyMatch(t -> "FORK_JOIN".equals(t.get("type")));
        assertThat(hasForkJoin).isFalse();
    }

    // -----------------------------------------------------------------------
    //  Validation — failure modes
    // -----------------------------------------------------------------------

    @Test
    void testCycleInDependsOnIsRejected() {
        String planJson =
                """
                {
                  "steps": [
                    {"id": "a", "depends_on": ["b"], "operations": [{"tool": "noop", "args": {}}]},
                    {"id": "b", "depends_on": ["a"], "operations": [{"tool": "noop", "args": {}}]}
                  ]
                }""";
        String error = compilePlanExpectError(planJson);
        assertThat(error).contains("Cycle in depends_on").contains("->");
    }

    @Test
    void testDuplicateStepIdIsRejected() {
        String planJson =
                """
                {
                  "steps": [
                    {"id": "s1", "operations": [{"tool": "noop", "args": {}}]},
                    {"id": "s1", "operations": [{"tool": "noop", "args": {}}]}
                  ]
                }""";
        String error = compilePlanExpectError(planJson);
        assertThat(error).contains("Duplicate step id: s1");
    }

    @Test
    void testEmptyStepsArrayIsRejected() {
        String error = compilePlanExpectError("{\"steps\": []}");
        assertThat(error).contains("non-empty steps array");
    }

    // -----------------------------------------------------------------------
    //  Lenient validation — round 6 (the workflow 31baab22 fix)
    // -----------------------------------------------------------------------

    @Test
    void testMissingStepIdsAreAutoGenerated() {
        String planJson =
                """
                {
                  "steps": [
                    {"operations": [{"tool": "noop", "args": {}}]},
                    {"operations": [{"tool": "noop", "args": {}}]}
                  ]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        assertThat(wf.get("name")).isNotNull();
    }

    @Test
    void testUnknownDependsOnIsDroppedNotErrored() {
        String planJson =
                """
                {
                  "steps": [
                    {"id": "s1", "operations": [{"tool": "noop", "args": {}}]},
                    {"id": "s2",
                     "depends_on": ["s1", "ghost_step", "another_phantom"],
                     "operations": [{"tool": "noop", "args": {}}]}
                  ]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        assertThat(wf).isNotEmpty();
    }

    @Test
    void testRealisticBrokenPlanFromLlmCompiles() {
        String planJson =
                """
                {
                  "steps": [
                    {"operations": [{"tool": "write_file",
                                     "args": {"path": "x.java", "content": "..."}}]},
                    {"depends_on": ["create_files"],
                     "operations": [{"tool": "run_unit_tests", "args": {}}]}
                  ]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        assertThat(wf).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    //  success_condition sandbox
    // -----------------------------------------------------------------------

    @Test
    void testUnsafeSuccessConditionIsRejected() {
        // Updated for the SafeConditionInterpreter parser (dg-rec #14).
        // The grammar's whitelist makes node-type-based attacks
        // syntactically impossible. Note: bare property accesses like
        // ``$.constructor`` / ``$.__proto__`` are NOT in this list — in
        // a Java map they're inert lookups returning null. The classes
        // of expressions the parser must reject are: function calls,
        // assignments, computed subscripts via concatenation, ternaries,
        // statement sequencing, template literals, var-declarations,
        // bare identifiers other than {true,false,null}.
        String[] unsafeConditions = {
            "function() { while (true) {} }",
            "$.x === 1; while(1){}",
            "Java.type('java.lang.Runtime')",
            "eval('1+1')",
            "$.x = 5",
            "var foo = 1",
            "$.constructor.constructor('return Java.type(0)')()",
            "$.x === 1, eval('1')",
            "$['c'+'onstructor']",
            "$.\\u0063onstructor",
            "`${$.x}` === '1'",
            "$.x ? 1 : 0",
            "Object.keys($).length > 0",
            "Reflect.get($, 'x')",
            "$.__defineGetter__('x', function(){})",
            "(function(){ x = 1; return $.y; })()",
        };
        for (String unsafe : unsafeConditions) {
            String planJson =
                    "{ \"steps\": [{\"id\": \"s1\", \"operations\": [{\"tool\": \"noop\", \"args\": {}}]}],"
                            + "  \"validation\": [{\"tool\": \"check\", \"success_condition\": "
                            + jsonString(unsafe)
                            + "}] }";
            String error = compilePlanExpectError(planJson);
            assertThat(error)
                    .as("unsafe success_condition '%s' must be rejected", unsafe)
                    .contains("unsafe success_condition");
        }
    }

    @Test
    void testBarePrototypePropertyAccessIsAcceptedAsInertMapLookup() {
        // ``$.constructor`` / ``$.__proto__`` / ``$['constructor']`` are
        // JS prototype-pollution vectors. In Java they're plain Map.get()
        // calls returning null. Document this explicitly so a future
        // reader doesn't re-add a denylist that's not buying anything.
        String[] inertLookups = {
            "$.constructor === null",
            "$.prototype === null",
            "$.__proto__ === null",
            "$['constructor'] === null",
            "$.x['__proto__'] === null",
        };
        for (String cond : inertLookups) {
            String planJson =
                    "{ \"steps\": [{\"id\": \"s1\", \"operations\": [{\"tool\": \"noop\", \"args\": {}}]}],"
                            + "  \"validation\": [{\"tool\": \"check\", \"success_condition\": "
                            + jsonString(cond)
                            + "}] }";
            Map<String, Object> wf = compilePlan(planJson);
            assertThat(wf).isNotNull();
        }
    }

    @Test
    void testSuccessConditionAllowsLiteralBannedWordInString() {
        String[] safeWithLiterals = {
            "$.kind === 'constructor'",
            "$.role !== 'eval-pending'",
            "$.msg === 'Function returned ok'",
        };
        for (String cond : safeWithLiterals) {
            String planJson =
                    "{ \"steps\": [{\"id\": \"s1\", \"operations\": [{\"tool\": \"noop\", \"args\": {}}]}],"
                            + "  \"validation\": [{\"tool\": \"check\", \"success_condition\": "
                            + jsonString(cond)
                            + "}] }";
            Map<String, Object> wf = compilePlan(planJson);
            assertThat(wf).isNotNull();
        }
    }

    @Test
    void testSafeSuccessConditionsAreAccepted() {
        // Real-world success_condition shapes the parser must accept.
        // Replaces the legacy "$.indexOf('passed') >= 0" assertion: that
        // was a JS method call, never grammar-legal under the new parser.
        // The intent (substring match) wasn't actually wired in the old
        // implementation either — it relied on GraalJS interpreting
        // String.prototype.indexOf, which our toolOut wrapping erased on
        // any non-string result. Pragmatic substitute: explicit equality.
        String[] safeConditions = {
            "$.exit_code === 0",
            "$.passed === true",
            "$.passed", // truthy check on a field
            "$.status === 'passed'",
            "$.count > 0 && $.errors === 0",
            "$.status !== 'ERROR'",
            "$.score >= 0.5 && $.score <= 1.0",
            "$.flags.green === true || $.flags.amber === true",
            "!($.failed)",
        };
        for (String safe : safeConditions) {
            String planJson =
                    "{ \"steps\": [{\"id\": \"s1\", \"operations\": [{\"tool\": \"noop\", \"args\": {}}]}],"
                            + "  \"validation\": [{\"tool\": \"check\", \"success_condition\": "
                            + jsonString(safe)
                            + "}] }";
            Map<String, Object> wf = compilePlan(planJson);
            assertThat(wf).as("must accept: %s", safe).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    //  output_schema shape rejection
    // -----------------------------------------------------------------------

    @Test
    void testJsonSchemaAsOutputSchemaIsRejected() {
        String jsonSchemaShape =
                "{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"x\\\":{\\\"type\\\":\\\"string\\\"}}}";
        String planJson =
                "{"
                        + "\"steps\": [{\"id\": \"s1\", \"operations\": [{"
                        + "\"tool\": \"do_thing\","
                        + "\"generate\": {"
                        + "\"instructions\": \"do it\","
                        + "\"output_schema\": \""
                        + jsonSchemaShape
                        + "\""
                        + "}}]}]}";
        String error = compilePlanExpectError(planJson);
        assertThat(error).contains("JSON Schema").contains("example object instead");
    }

    @Test
    void testInstanceShapeOutputSchemaIsAccepted() {
        String planJson =
                "{"
                        + "\"steps\": [{\"id\": \"s1\", \"operations\": [{"
                        + "\"tool\": \"write_file\","
                        + "\"generate\": {"
                        + "\"instructions\": \"write hello\","
                        + "\"output_schema\": \"{\\\"path\\\":\\\"...\\\",\\\"content\\\":\\\"...\\\"}\""
                        + "}}]}]}";
        Map<String, Object> wf = compilePlan(planJson);
        assertThat(wf).isNotNull();
    }

    // -----------------------------------------------------------------------
    //  Generated op structure
    // -----------------------------------------------------------------------

    @Test
    void testGeneratedOpUsesParseGateSwitch() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{
                    "tool": "write_file",
                    "generate": {
                      "instructions": "write",
                      "output_schema": "{\\"path\\":\\"...\\"}"
                    }
                  }]}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        boolean hasParseGate =
                tasks.stream()
                        .anyMatch(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("pgate_"));
        assertThat(hasParseGate).isTrue();
    }

    @Test
    void testNoTaskIsOptional() {
        String planJson =
                """
                {
                  "steps": [
                    {"id": "s1", "operations": [
                      {"tool": "static_op", "args": {"x": 1}},
                      {"tool": "gen_op", "generate": {"instructions": "go", "output_schema": "{\\"y\\":\\"...\\"}"}}
                    ]}
                  ],
                  "validation": [{"tool": "check", "success_condition": "$.passed === true"}],
                  "on_success": [{"tool": "celebrate", "args": {}}],
                  "on_failure": [{"tool": "log_failure", "args": {}}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        long optionalCount =
                tasks.stream().filter(t -> Boolean.TRUE.equals(t.get("optional"))).count();
        assertThat(optionalCount).isZero();
    }

    // -----------------------------------------------------------------------
    //  Validation SWITCH semantics
    // -----------------------------------------------------------------------

    @Test
    void testValidationSwitchFailsClosed() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}],
                  "validation": [{"tool": "check", "success_condition": "$.passed === true"}],
                  "on_success": [{"tool": "celebrate", "args": {}}],
                  "on_failure": [{"tool": "log_failure", "args": {}}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        Map<String, Object> validationSwitch =
                tasks.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("vsw_"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> decisionCases =
                (Map<String, Object>) validationSwitch.get("decisionCases");
        // Both single- and multi-validator plans use the same "passed"
        // string contract — single-validator plans skip val_agg by having
        // val_eval emit the string directly; multi-validator plans still
        // emit val_agg to combine N branches.
        assertThat(decisionCases).containsKey("passed");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> defaultCase =
                (List<Map<String, Object>>) validationSwitch.get("defaultCase");
        boolean defaultHasTerminate =
                defaultCase.stream().anyMatch(t -> "TERMINATE".equals(t.get("type")));
        assertThat(defaultHasTerminate).isTrue();
    }

    @Test
    void testValidationPassedBranchHasNoOpWhenOnSuccessIsEmpty() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}],
                  "validation": [{"tool": "check", "success_condition": "$.passed === true"}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        Map<String, Object> validationSwitch =
                tasks.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("vsw_"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> decisionCases =
                (Map<String, List<Map<String, Object>>>) validationSwitch.get("decisionCases");
        List<Map<String, Object>> passedBranch = decisionCases.get("passed");
        assertThat(passedBranch).isNotEmpty();
        Map<String, Object> first = passedBranch.get(0);
        // Sentinel for "Conductor SWITCH treats empty case as defaultCase
        // fall-through". Lighter primitive than INLINE — SET_VARIABLE is a
        // Conductor system task with no JS engine and no worker.
        assertThat(first.get("type")).isEqualTo("SET_VARIABLE");
        assertThat(String.valueOf(first.get("taskReferenceName"))).startsWith("ok_noop_");
    }

    @Test
    void testValidationPassedBranchPreservesOnSuccessTasks() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}],
                  "validation": [{"tool": "check", "success_condition": "$.passed === true"}],
                  "on_success": [{"tool": "celebrate", "args": {"x": 1}}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        Map<String, Object> validationSwitch =
                tasks.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("vsw_"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> decisionCases =
                (Map<String, List<Map<String, Object>>>) validationSwitch.get("decisionCases");
        List<Map<String, Object>> passedBranch = decisionCases.get("passed");
        assertThat(passedBranch).hasSize(1);
        assertThat(passedBranch.get(0).get("name")).isEqualTo("celebrate");
        assertThat(passedBranch.get(0).get("type")).isEqualTo("SIMPLE");
    }

    // -----------------------------------------------------------------------
    //  Timeout propagation
    // -----------------------------------------------------------------------

    @Test
    void testTimeoutFromHarnessConfig() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{"tool": "noop", "args": {}}]}]
                }""";
        Map<String, Object> output = run(planJson, 1234);
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        assertThat(wf.get("timeoutSeconds")).isEqualTo(1234);
    }

    @Test
    void testDefaultTimeoutWhenHarnessTimeoutAbsent() {
        Map<String, Object> wf =
                compilePlan(
                        "{ \"steps\": [{\"id\": \"s1\", \"operations\": [{\"tool\": \"noop\", \"args\": {}}]}] }");
        assertThat(wf.get("timeoutSeconds")).isEqualTo(600);
    }

    // -----------------------------------------------------------------------
    //  terminalRef invariant — wrapper task .result patterns
    // -----------------------------------------------------------------------

    @Test
    void testSequentialTerminalGeneratedOpResultPointsAtInnerTool() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "generate": {
                      "instructions": "go",
                      "output_schema": "{\\"out\\":\\"...\\"}"
                    }}
                  ]}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) wf.get("outputParameters");
        String result = String.valueOf(outputs.get("result"));
        assertThat(result).contains("t_s1_").doesNotContain("pgate_");
    }

    @Test
    void testParallelTerminalGeneratedOpAggregatorPointsAtInnerTool() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "parallel": true, "operations": [
                    {"tool": "gen_a", "generate": {
                      "instructions": "a",
                      "output_schema": "{\\"x\\":\\"...\\"}"
                    }},
                    {"tool": "gen_b", "generate": {
                      "instructions": "b",
                      "output_schema": "{\\"y\\":\\"...\\"}"
                    }}
                  ]}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        Map<String, Object> aggregator =
                tasks.stream()
                        .filter(
                                t ->
                                        "INLINE".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("parallel_agg_"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> aggInputs = (Map<String, Object>) aggregator.get("inputParameters");
        for (int i = 0; i < 2; i++) {
            String key = "b" + i;
            String ref = String.valueOf(aggInputs.get(key));
            assertThat(ref)
                    .as(
                            "parallel_agg input '%s' must reference inner tool task, not parseGate SWITCH",
                            key)
                    .startsWith("${t_s1_")
                    .endsWith(".output.result}")
                    .doesNotContain("pgate_");
        }
    }

    // -----------------------------------------------------------------------
    //  Ambient injection invariant
    // -----------------------------------------------------------------------

    @Test
    void testEverySimpleTaskHasFiveAmbientKeys() {
        String planJson =
                """
                {
                  "steps": [
                    {"id": "s1", "operations": [
                      {"tool": "static_op", "args": {"x": 1}},
                      {"tool": "gen_op", "generate": {
                        "instructions": "go",
                        "output_schema": "{\\"y\\":\\"...\\"}"
                      }}
                    ]},
                    {"id": "s2", "depends_on": ["s1"], "parallel": true, "operations": [
                      {"tool": "parallel_a", "args": {}},
                      {"tool": "parallel_b", "args": {}}
                    ]}
                  ],
                  "validation": [
                    {"tool": "lint_check", "args": {"path": "/tmp"}, "success_condition": "$.passed === true"},
                    {"tool": "build_check", "args": {}, "success_condition": "$.exit_code === 0"}
                  ],
                  "on_success": [{"tool": "celebrate", "args": {}}],
                  "on_failure": [{"tool": "log_failure", "args": {}}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        List<Map<String, Object>> simpleTasks =
                tasks.stream().filter(t -> "SIMPLE".equals(t.get("type"))).toList();
        assertThat(simpleTasks).hasSizeGreaterThanOrEqualTo(8);

        String[] keys = {"cwd", "credentials", "media", "session_id"};
        String[] refs = {
            "${workflow.input.cwd}",
            "${workflow.input.credentials}",
            "${workflow.input.media}",
            "${workflow.input.session_id}"
        };
        for (Map<String, Object> t : simpleTasks) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputs = (Map<String, Object>) t.get("inputParameters");
            String name = String.valueOf(t.get("name"));
            for (int i = 0; i < keys.length; i++) {
                assertThat(inputs)
                        .as("SIMPLE task '%s' missing ambient key '%s'", name, keys[i])
                        .containsEntry(keys[i], refs[i]);
            }
        }
    }

    @Test
    void testGeneratedOpAmbientKeysWinOverLLMSuppliedSchemaKeys() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{
                    "tool": "do_thing",
                    "generate": {
                      "instructions": "go",
                      "output_schema": "{\\"cwd\\":\\"...\\",\\"credentials\\":\\"...\\",\\"media\\":\\"...\\",\\"safe_field\\":\\"...\\"}"
                    }
                  }]}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        Map<String, Object> doThing =
                tasks.stream()
                        .filter(
                                t ->
                                        "SIMPLE".equals(t.get("type"))
                                                && "do_thing".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) doThing.get("inputParameters");
        assertThat(inputs.get("cwd")).isEqualTo("${workflow.input.cwd}");
        assertThat(inputs.get("credentials")).isEqualTo("${workflow.input.credentials}");
        assertThat(inputs.get("media")).isEqualTo("${workflow.input.media}");
        assertThat(String.valueOf(inputs.get("safe_field"))).contains(".output.result.safe_field");
    }

    // -----------------------------------------------------------------------
    //  outputParameters source
    // -----------------------------------------------------------------------

    @Test
    void testNoValidationPlanResultPointsAtLastTaskNotLiteral() {
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "args": {"x": 1}}
                  ]}]
                }""";
        Map<String, Object> wf = compilePlan(planJson);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) wf.get("outputParameters");
        String result = String.valueOf(outputs.get("result"));
        assertThat(result).startsWith("${").endsWith(".output.result}");
        assertThat(result).doesNotContain("completed");
    }

    @Test
    void testWorkflowDefIsBareObjectNotArrayWrapped() {
        Map<String, Object> wf =
                compilePlan(
                        "{ \"steps\": [{\"id\": \"s1\", \"operations\": [{\"tool\": \"noop\", \"args\": {}}]}] }");
        // It's a Map, not a List — that's the new contract.
        assertThat(wf).isInstanceOf(Map.class);
        assertThat(wf.get("name")).isNotNull();
        assertThat(wf.get("tasks")).isInstanceOf(List.class);
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    //  knownToolNames allowlist (workflow a369f52c regression)
    // -----------------------------------------------------------------------

    @Test
    void testRejectsUnknownToolName() {
        // The bug we're defending against: planner emits a hallucinated tool
        // name (e.g. Claude's training-memory ``str_replace``). Pre-fix, PAC
        // happily compiled a SIMPLE task with that name, no worker polled
        // for it, the workflow hung forever. With knownToolNames passed in,
        // the unknown tool produces a structured compile error which the
        // SWITCH then routes to the fallback.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "str_replace", "args": {"old": "x", "new": "y"}}
                  ]}]
                }""";
        Map<String, Object> output =
                runWithKnownTools(planJson, null, List.of("read_file", "write_file"));
        Object error = output.get("error");
        assertThat(error).isNotNull();
        assertThat(String.valueOf(error)).contains("unknown tool").contains("str_replace");
        assertThat(output.get("workflowDef")).isNull();
    }

    @Test
    void testAcceptsKnownToolName() {
        // Same plan, but ``str_replace`` IS in the allowlist — compiles
        // cleanly. Counter-test for the rejection above.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "str_replace", "args": {"old": "x", "new": "y"}}
                  ]}]
                }""";
        Map<String, Object> output =
                runWithKnownTools(planJson, null, List.of("str_replace", "read_file"));
        assertThat(output.get("error")).isNull();
        assertThat(output.get("workflowDef")).isNotNull();
    }

    @Test
    void testServerBuiltinsImplicitlyAllowed() {
        // ``generate`` ops compile to LLM_CHAT_COMPLETE → INLINE → SIMPLE
        // chains. The SIMPLE step uses ``op.tool`` (the user's tool name);
        // the LLM step uses ``llm_chat_complete`` internally. The user's
        // ``knownToolNames`` only needs to list ``op.tool`` — server-side
        // built-ins like ``llm_chat_complete`` are seeded automatically by
        // PAC so the user never has to know about them.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{
                    "tool": "write_file",
                    "generate": {
                      "instructions": "write",
                      "output_schema": "{\\"path\\":\\"...\\",\\"content\\":\\"...\\"}"
                    }
                  }]}]
                }""";
        // knownToolNames lists only ``write_file`` — does not list
        // ``llm_chat_complete``. Compile should still succeed.
        Map<String, Object> output = runWithKnownTools(planJson, null, List.of("write_file"));
        assertThat(output.get("error")).isNull();
        assertThat(output.get("workflowDef")).isNotNull();
    }

    @Test
    void testEmptyKnownToolNamesDisablesCheck() {
        // Legacy callers pass no allowlist; PAC accepts any tool name.
        // This preserves backward compatibility for older callers and
        // for direct unit tests that don't care about allowlisting.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "anything_goes", "args": {}}
                  ]}]
                }""";
        // No knownToolNames passed.
        Map<String, Object> output = run(planJson, null);
        assertThat(output.get("error")).isNull();
        assertThat(output.get("workflowDef")).isNotNull();
    }

    // -----------------------------------------------------------------------
    //  Tool-level guardrails — wrap SIMPLE with format → check → SWITCH
    // -----------------------------------------------------------------------

    @Test
    void testToolWithoutGuardrailsEmitsBareSimple() {
        // Counter-test: when the tool config has no guardrails, the SIMPLE
        // is emitted as-is (no format INLINE, no SWITCH gate).
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "args": {"x": 1}}
                  ]}]
                }""";
        Map<String, Object> bareTool = Map.of("name", "do_thing", "toolType", "worker");
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("do_thing"), List.of(bareTool));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) wf.get("tasks");
        // Only the SIMPLE plus the per-step step_output wrap INLINE — no
        // guardrail format INLINE or SWITCH gate. The step_output_* wrap
        // is always emitted to normalise dict-vs-string worker returns into
        // a canonical .output.result for downstream Refs.
        long guardrailInlineCount =
                top.stream()
                        .filter(t -> "INLINE".equals(t.get("type")))
                        .filter(
                                t ->
                                        !String.valueOf(t.get("taskReferenceName"))
                                                .startsWith("step_output_"))
                        .count();
        long switchCount = top.stream().filter(t -> "SWITCH".equals(t.get("type"))).count();
        assertThat(guardrailInlineCount).as("no guardrails ⇒ no format INLINE").isZero();
        assertThat(switchCount).as("no guardrails ⇒ no SWITCH gate").isZero();
        assertThat(top)
                .anyMatch(t -> "SIMPLE".equals(t.get("type")) && "do_thing".equals(t.get("name")));
    }

    @Test
    void testToolWithRegexGuardrailEmitsGate() {
        // A tool with a regex blocklist guardrail should compile to:
        //   INLINE format_args
        //   INLINE regex_guardrail (check)
        //   SWITCH guardrail_gate
        //     decisionCases: only the configured case (here: raise) → TERMINATE
        //                    raise is always emitted as the catch-all so
        //                    unexpected on_fail values fail closed.
        //     defaultCase (pass): SIMPLE tool task — INSIDE the gate's default
        // The SIMPLE no longer sits as an outer sibling — it's nested in
        // the SWITCH's defaultCase so any non-pass branch deterministically
        // skips the SIMPLE. Without this, regex guardrail's default
        // OnFail.RETRY would let the SIMPLE run anyway (silent bypass).
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "run_query", "args": {"query": "SELECT 1"}}
                  ]}]
                }""";
        Map<String, Object> guardrail = new java.util.HashMap<>();
        guardrail.put("name", "no_drop");
        guardrail.put("guardrailType", "regex");
        guardrail.put("patterns", List.of("(?i)\\\\bdrop\\\\b"));
        guardrail.put("mode", "block");
        guardrail.put("onFail", "raise");
        guardrail.put("message", "destructive SQL blocked");
        Map<String, Object> guardedTool = new java.util.HashMap<>();
        guardedTool.put("name", "run_query");
        guardedTool.put("toolType", "worker");
        guardedTool.put("guardrails", List.of(guardrail));

        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("run_query"), List.of(guardedTool));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");

        List<Map<String, Object>> all = allTasks(wf);

        // Format INLINE present.
        boolean hasFormat =
                all.stream()
                        .anyMatch(
                                t ->
                                        "INLINE".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .contains("_format"));
        assertThat(hasFormat).as("expected format INLINE for guardrail content").isTrue();

        // Regex guardrail INLINE present.
        boolean hasRegex =
                all.stream()
                        .anyMatch(
                                t ->
                                        "INLINE".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .contains("regex_guardrail"));
        assertThat(hasRegex).as("expected regex guardrail INLINE").isTrue();

        // SWITCH gate present (new ref name: ``guardrail_gate``, not the
        // old ``guardrail_route`` from GuardrailCompiler.compileGuardrailRouting).
        Map<String, Object> gate =
                all.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .contains("guardrail_gate"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "expected SWITCH gate for guardrail; got types: "
                                                        + all.stream()
                                                                .map(t -> t.get("type"))
                                                                .toList()));

        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> cases =
                (Map<String, List<Map<String, Object>>>) gate.get("decisionCases");
        // Guardrail configured with onFail=raise — the SWITCH should emit
        // ONLY the ``raise`` case (catch-all), no dead retry/fix/human
        // entries. Previously every guardrail emitted all four cases
        // unconditionally, which forced Conductor to register TaskDefs for
        // branches the runtime could never reach for this guardrail.
        assertThat(cases).containsOnlyKeys("raise");
        boolean terminates =
                cases.get("raise").stream().anyMatch(t -> "TERMINATE".equals(t.get("type")));
        assertThat(terminates).as("the configured case must TERMINATE in plan mode").isTrue();

        // SIMPLE lives INSIDE the gate's defaultCase (pass branch), not
        // as a sibling. Walk the defaultCase to find it.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> defaultCase = (List<Map<String, Object>>) gate.get("defaultCase");
        boolean simpleInDefault =
                defaultCase.stream()
                        .anyMatch(
                                t ->
                                        "SIMPLE".equals(t.get("type"))
                                                && "run_query".equals(t.get("name")));
        assertThat(simpleInDefault)
                .as("SIMPLE must be nested in the gate's defaultCase, not as an outer sibling")
                .isTrue();
    }

    @Test
    void testGuardrailGateEmitsOnlyConfiguredCase_perOnFailMode() {
        // Verify that PAC emits exactly the SWITCH cases reachable for a
        // given guardrail's on_fail. Previously every guardrail emitted
        // raise+retry+fix+human unconditionally; an ``on_fail=raise``
        // guardrail still produced 4 dead TERMINATE branches.
        java.util.Map<String, java.util.Set<String>> expected =
                java.util.Map.of(
                        "raise", java.util.Set.of("raise"),
                        "retry", java.util.Set.of("raise", "retry"),
                        "fix", java.util.Set.of("raise", "fix"),
                        "human", java.util.Set.of("raise", "human"));

        for (var entry : expected.entrySet()) {
            String onFail = entry.getKey();
            String planJson =
                    """
                    {
                      "steps": [{"id": "s1", "operations": [
                        {"tool": "do_thing", "args": {"x": "y"}}
                      ]}]
                    }""";
            Map<String, Object> guardrail = new java.util.HashMap<>();
            guardrail.put("name", "g_" + onFail);
            guardrail.put("guardrailType", "regex");
            guardrail.put("patterns", List.of("blocked"));
            guardrail.put("mode", "block");
            guardrail.put("onFail", onFail);
            guardrail.put("message", "blocked by " + onFail);
            Map<String, Object> guardedTool = new java.util.HashMap<>();
            guardedTool.put("name", "do_thing");
            guardedTool.put("toolType", "worker");
            guardedTool.put("guardrails", List.of(guardrail));

            Map<String, Object> output =
                    runWithParentTools(planJson, null, List.of("do_thing"), List.of(guardedTool));
            assertThat(output.get("error")).as("compile error for on_fail=" + onFail).isNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");

            Map<String, Object> gate =
                    allTasks(wf).stream()
                            .filter(
                                    t ->
                                            "SWITCH".equals(t.get("type"))
                                                    && String.valueOf(t.get("taskReferenceName"))
                                                            .contains("guardrail_gate"))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "expected guardrail_gate SWITCH for on_fail="
                                                            + onFail));

            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> cases =
                    (Map<String, List<Map<String, Object>>>) gate.get("decisionCases");
            assertThat(cases.keySet())
                    .as(
                            "on_fail=%s should emit exactly %s, got %s",
                            onFail, entry.getValue(), cases.keySet())
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
    }

    @Test
    void testMultipleGuardrailsChainSequentially() {
        // Two guardrails on one tool ⇒ nested SWITCH gates. Outer gate's
        // defaultCase contains the inner gate; inner gate's defaultCase
        // contains the SIMPLE. Same number of SWITCHes (2), now nested.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "args": {"x": "ok"}}
                  ]}]
                }""";
        Map<String, Object> g1 = new java.util.HashMap<>();
        g1.put("name", "g1");
        g1.put("guardrailType", "regex");
        g1.put("patterns", List.of("bad1"));
        g1.put("mode", "block");
        g1.put("onFail", "raise");
        Map<String, Object> g2 = new java.util.HashMap<>();
        g2.put("name", "g2");
        g2.put("guardrailType", "regex");
        g2.put("patterns", List.of("bad2"));
        g2.put("mode", "block");
        g2.put("onFail", "raise");
        Map<String, Object> toolCfg = new java.util.HashMap<>();
        toolCfg.put("name", "do_thing");
        toolCfg.put("toolType", "worker");
        toolCfg.put("guardrails", List.of(g1, g2));

        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("do_thing"), List.of(toolCfg));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);

        long switchGates =
                all.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .contains("guardrail_gate"))
                        .count();
        assertThat(switchGates).as("two guardrails ⇒ two nested SWITCH gates").isEqualTo(2);
    }

    @Test
    void testDefaultRetryGuardrailStillBlocksSimple() {
        // Regression for the silent-bypass bug: a regex guardrail with
        // default OnFail (which is "retry" per RegexGuardrail) used to
        // emit a feedback INLINE, complete the SWITCH, and let the sibling
        // SIMPLE run anyway. v1 of the gate fix collapses retry to
        // TERMINATE in plan mode — the SIMPLE only runs from the
        // defaultCase, which fires only on pass.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "args": {"x": "ok"}}
                  ]}]
                }""";
        Map<String, Object> guardrail = new java.util.HashMap<>();
        guardrail.put("name", "block_bad");
        guardrail.put("guardrailType", "regex");
        guardrail.put("patterns", List.of("bad"));
        guardrail.put("mode", "block");
        // intentionally omit onFail — exercises the default
        Map<String, Object> toolCfg = new java.util.HashMap<>();
        toolCfg.put("name", "do_thing");
        toolCfg.put("toolType", "worker");
        toolCfg.put("guardrails", List.of(guardrail));

        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("do_thing"), List.of(toolCfg));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);

        // Find the SIMPLE for do_thing — it must be inside the gate's
        // defaultCase, NOT at the top level as a sibling. Walk top-level
        // tasks and assert the SIMPLE is nowhere there.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) wf.get("tasks");
        boolean simpleAtTop =
                top.stream()
                        .anyMatch(
                                t ->
                                        "SIMPLE".equals(t.get("type"))
                                                && "do_thing".equals(t.get("name")));
        assertThat(simpleAtTop)
                .as(
                        "SIMPLE must NOT be a top-level sibling of the gate; nesting in defaultCase "
                                + "is what closes the silent-bypass for default OnFail.RETRY")
                .isFalse();

        // The SIMPLE must exist inside the gate's defaultCase.
        Map<String, Object> gate =
                all.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .contains("guardrail_gate"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> defaultCase = (List<Map<String, Object>>) gate.get("defaultCase");
        boolean simpleInDefault =
                defaultCase.stream()
                        .anyMatch(
                                t ->
                                        "SIMPLE".equals(t.get("type"))
                                                && "do_thing".equals(t.get("name")));
        assertThat(simpleInDefault).isTrue();
    }

    @Test
    void testGenerateOpWithGuardrailWrapsInsideParseGate() {
        // Regression for the inverted-threat-model bug: generate-op
        // (LLM-generated args) used to emit a bare SIMPLE inside parseGate's
        // ``ok`` decisionCase with zero guardrail lookup. Now the same
        // emitGuardrailWrappedSimple gate wraps it.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [{
                    "tool": "do_thing",
                    "generate": {
                      "instructions": "go",
                      "output_schema": "{\\"x\\":\\"...\\"}"
                    }
                  }]}]
                }""";
        Map<String, Object> guardrail = new java.util.HashMap<>();
        guardrail.put("name", "block_bad");
        guardrail.put("guardrailType", "regex");
        guardrail.put("patterns", List.of("bad"));
        guardrail.put("mode", "block");
        guardrail.put("onFail", "raise");
        Map<String, Object> toolCfg = new java.util.HashMap<>();
        toolCfg.put("name", "do_thing");
        toolCfg.put("toolType", "worker");
        toolCfg.put("guardrails", List.of(guardrail));

        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("do_thing"), List.of(toolCfg));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);

        // The guardrail gate must exist (proving generate-op went through
        // the wrap). It will live inside the parseGate's ok branch.
        boolean hasGate =
                all.stream()
                        .anyMatch(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .contains("guardrail_gate"));
        assertThat(hasGate)
                .as("generate-op must wrap its SIMPLE with the same guardrail gate as static-args")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    //  Tool-type routing — each toolType compiles to the right task type
    // -----------------------------------------------------------------------

    @Test
    void testWorkerToolOpEmitsSimple_regression() {
        // Counter-test for the routing change: a plain worker tool must
        // still emit a SIMPLE task with the tool name and literal args.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "args": {"x": 1}}
                  ]}]
                }""";
        Map<String, Object> tc = Map.of("name", "do_thing", "toolType", "worker");
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("do_thing"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> task =
                all.stream()
                        .filter(t -> "do_thing".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow();
        assertThat(task.get("type")).isEqualTo("SIMPLE");
    }

    @Test
    void testAgentToolOpEmitsSubWorkflow() {
        // The headline change: a plan op whose tool has toolType=agent_tool
        // must compile to a SUB_WORKFLOW with the workflow name from
        // tool.config.workflowName, NOT a SIMPLE that polls forever.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "subtask_coder", "args": {"request": "implement file X"}}
                  ]}]
                }""";
        Map<String, Object> tc = new HashMap<>();
        tc.put("name", "subtask_coder");
        tc.put("toolType", "agent_tool");
        // workflowName is set by AgentService.registerAgentToolWorkflows
        // before the plan compile runs. Mirror that here.
        tc.put("config", Map.of("workflowName", "subtask_coder_agent_wf"));
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("subtask_coder"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> task =
                all.stream()
                        .filter(t -> "SUB_WORKFLOW".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "expected a SUB_WORKFLOW task for agent_tool op; got: "
                                                        + all.stream()
                                                                .map(
                                                                        t ->
                                                                                t.get("type")
                                                                                        + ":"
                                                                                        + t.get(
                                                                                                "name"))
                                                                .toList()));
        assertThat(task.get("name")).isEqualTo("subtask_coder_agent_wf");
        @SuppressWarnings("unchecked")
        Map<String, Object> sub = (Map<String, Object>) task.get("subWorkflowParam");
        assertThat(sub).as("SUB_WORKFLOW needs subWorkflowParam").isNotNull();
        assertThat(sub.get("name")).isEqualTo("subtask_coder_agent_wf");
        assertThat(sub.get("version")).isEqualTo(1);
        // The op's ``request`` arg becomes the sub-workflow's ``prompt``
        // input, matching the LLM-loop's agent_tool dispatch shape.
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) task.get("inputParameters");
        assertThat(inputs.get("prompt")).isEqualTo("implement file X");
    }

    @Test
    void testMcpToolOpEmitsCallMcpTool() {
        // MCP tools must compile to a CALL_MCP_TOOL system task whose
        // inputParameters include mcpServer, method (= tool name), arguments,
        // and headers. Without this, MCP plan ops poll for a non-existent
        // SIMPLE worker.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "search_docs", "args": {"q": "hello"}}
                  ]}]
                }""";
        Map<String, Object> tc = new HashMap<>();
        tc.put("name", "search_docs");
        tc.put("toolType", "mcp");
        tc.put("config", Map.of("server_url", "https://mcp.example/sse"));
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("search_docs"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> task =
                all.stream()
                        .filter(t -> "CALL_MCP_TOOL".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("expected CALL_MCP_TOOL task for mcp op"));
        assertThat(task.get("name")).isEqualTo("call_mcp_tool");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) task.get("inputParameters");
        assertThat(inputs.get("mcpServer")).isEqualTo("https://mcp.example/sse");
        assertThat(inputs.get("method")).isEqualTo("search_docs");
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpArgs = (Map<String, Object>) inputs.get("arguments");
        assertThat(mcpArgs).containsEntry("q", "hello");
        // Ambient keys must not leak into the MCP arguments payload —
        // they're framework concerns, not part of the user's MCP call.
        assertThat(mcpArgs).doesNotContainKey("__agentspan_ctx__");
        assertThat(mcpArgs).doesNotContainKey("session_id");
    }

    @Test
    void testHttpToolOpEmitsHttp() {
        // HTTP tools must compile to an HTTP system task. The op's literal
        // args become the request body; cfg supplies uri/method/headers.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "post_message", "args": {"channel": "#alerts", "text": "hi"}}
                  ]}]
                }""";
        Map<String, Object> tc = new HashMap<>();
        tc.put("name", "post_message");
        tc.put("toolType", "http");
        tc.put(
                "config",
                Map.of(
                        "url", "https://hooks.example/chat",
                        "method", "POST",
                        "headers", Map.of("Authorization", "Bearer xyz")));
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("post_message"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> task =
                all.stream()
                        .filter(t -> "HTTP".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("expected HTTP task for http op"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) task.get("inputParameters");
        @SuppressWarnings("unchecked")
        Map<String, Object> req = (Map<String, Object>) inputs.get("http_request");
        assertThat(req).as("HTTP task needs http_request inputParameter").isNotNull();
        assertThat(req.get("uri")).isEqualTo("https://hooks.example/chat");
        assertThat(req.get("method")).isEqualTo("POST");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) req.get("body");
        assertThat(body).containsEntry("channel", "#alerts").containsEntry("text", "hi");
        assertThat(body).doesNotContainKey("__agentspan_ctx__");
    }

    @Test
    void testUnknownToolTypeFallsBackToSimple() {
        // Backward compat: a tool with an unrecognized toolType must
        // emit a SIMPLE task so nothing in the existing surface area
        // silently changes type.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "exotic_thing", "args": {"x": 1}}
                  ]}]
                }""";
        Map<String, Object> tc =
                Map.of("name", "exotic_thing", "toolType", "experimental_new_type");
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("exotic_thing"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> task =
                all.stream()
                        .filter(t -> "exotic_thing".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow();
        assertThat(task.get("type")).isEqualTo("SIMPLE");
    }

    @Test
    void testGenerateOpWithAgentToolEmitsSubWorkflow() {
        // LLM-args path: when the tech-lead style planner emits a ``generate``
        // op for an agent_tool, the compiled plan must emit a SUB_WORKFLOW
        // whose ``prompt`` is the parse-gate's ``request`` expression — not
        // a SIMPLE that polls nowhere. This is the path that turns "tech
        // lead generates N subtask prompts" into N FORK_JOIN'd sub-workflows.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "subtask_coder",
                     "generate": {
                       "instructions": "Generate a request for the subtask coder.",
                       "output_schema": "{\\"request\\": \\"...\\"}"
                     }}
                  ]}]
                }""";
        Map<String, Object> tc = new HashMap<>();
        tc.put("name", "subtask_coder");
        tc.put("toolType", "agent_tool");
        tc.put("config", Map.of("workflowName", "subtask_coder_agent_wf"));
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("subtask_coder"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        // Generate path emits LLM_CHAT_COMPLETE + INLINE parse + SWITCH +
        // SUB_WORKFLOW (in pass branch) + TERMINATE (in err branch).
        Map<String, Object> sub =
                all.stream()
                        .filter(t -> "SUB_WORKFLOW".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "generate-op with agent_tool must emit SUB_WORKFLOW"));
        assertThat(sub.get("name")).isEqualTo("subtask_coder_agent_wf");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) sub.get("inputParameters");
        // ``prompt`` is the parse-gate expression for ``request`` — the LLM's
        // generated value flows through here at runtime.
        assertThat(String.valueOf(inputs.get("prompt")))
                .startsWith("${")
                .contains(".output.result.request");
    }

    @Test
    void testGenerateOpWithMcpEmitsCallMcpTool() {
        // Same LLM-args path, mcp toolType: arguments map points at the
        // parse-gate expressions per field of the output_schema.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "search_docs",
                     "generate": {
                       "instructions": "Pick a query string.",
                       "output_schema": "{\\"q\\": \\"...\\"}"
                     }}
                  ]}]
                }""";
        Map<String, Object> tc = new HashMap<>();
        tc.put("name", "search_docs");
        tc.put("toolType", "mcp");
        tc.put("config", Map.of("server_url", "https://mcp.example/sse"));
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("search_docs"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> mcp =
                all.stream()
                        .filter(t -> "CALL_MCP_TOOL".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "generate-op with mcp must emit CALL_MCP_TOOL"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) mcp.get("inputParameters");
        assertThat(inputs.get("method")).isEqualTo("search_docs");
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpArgs = (Map<String, Object>) inputs.get("arguments");
        assertThat(String.valueOf(mcpArgs.get("q"))).startsWith("${").contains(".output.result.q");
        assertThat(mcpArgs).doesNotContainKey("__agentspan_ctx__");
    }

    @Test
    void testAgentToolRetryOverrideFromConfigPropagates() {
        // The agent_tool config carries per-tool retry policy from the SDK
        // (scatter_gather + agent_tool both let users tune these). The
        // compiled SUB_WORKFLOW must honour those overrides — otherwise
        // a fail_fast=false coordinator silently becomes fail_fast=true.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "flaky_agent", "args": {"request": "x"}}
                  ]}]
                }""";
        Map<String, Object> tc = new HashMap<>();
        tc.put("name", "flaky_agent");
        tc.put("toolType", "agent_tool");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("workflowName", "flaky_wf");
        cfg.put("retryCount", 5);
        cfg.put("retryDelaySeconds", 7);
        cfg.put("optional", true);
        tc.put("config", cfg);
        Map<String, Object> output =
                runWithParentTools(planJson, null, List.of("flaky_agent"), List.of(tc));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        Map<String, Object> sub =
                allTasks(wf).stream()
                        .filter(t -> "SUB_WORKFLOW".equals(t.get("type")))
                        .findFirst()
                        .orElseThrow();
        assertThat(sub.get("retryCount")).isEqualTo(5);
        assertThat(sub.get("retryDelaySeconds")).isEqualTo(7);
        assertThat(sub.get("optional")).isEqualTo(true);
    }

    @Test
    void testCompileIsDeterministicAcrossInvocations() throws Exception {
        // Determinism proof for the toolType routing change. A plan that
        // exercises every routed task type (SIMPLE / SUB_WORKFLOW / HTTP /
        // CALL_MCP_TOOL / HUMAN) plus parallel + sequential steps must
        // compile to a byte-identical WorkflowDef every time. Any drift
        // (Map iteration order, transient state, time-based ids) would
        // surface as a flaky diff across compiles.
        String planJson =
                """
                {
                  "steps": [
                    {"id": "fanout", "parallel": true, "operations": [
                      {"tool": "coder", "args": {"request": "do A"}},
                      {"tool": "coder", "args": {"request": "do B"}},
                      {"tool": "doc_search", "args": {"q": "rfc"}}
                    ]},
                    {"id": "report", "depends_on": ["fanout"], "operations": [
                      {"tool": "publish", "args": {"channel": "#rel"}},
                      {"tool": "approve", "args": {"reason": "ship?"}}
                    ]}
                  ],
                  "validation": [
                    {"tool": "check_word_count", "args": {"min_words": 10}}
                  ]
                }""";
        Map<String, Object> coder = new HashMap<>();
        coder.put("name", "coder");
        coder.put("toolType", "agent_tool");
        coder.put("config", Map.of("workflowName", "coder_wf"));
        Map<String, Object> mcp = new HashMap<>();
        mcp.put("name", "doc_search");
        mcp.put("toolType", "mcp");
        mcp.put("config", Map.of("server_url", "https://mcp.example"));
        Map<String, Object> http = new HashMap<>();
        http.put("name", "publish");
        http.put("toolType", "http");
        http.put("config", Map.of("url", "https://hooks.example", "method", "POST"));
        Map<String, Object> human = new HashMap<>();
        human.put("name", "approve");
        human.put("toolType", "human");
        Map<String, Object> validator = Map.of("name", "check_word_count", "toolType", "worker");
        List<String> names =
                List.of("coder", "doc_search", "publish", "approve", "check_word_count");
        List<Map<String, Object>> tools = List.of(coder, mcp, http, human, validator);

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        // Compile 10 times in a tight loop — same input every iteration.
        String reference = null;
        Map<String, Object> referenceWf = null;
        for (int i = 0; i < 10; i++) {
            Map<String, Object> output = runWithParentTools(planJson, null, names, tools);
            assertThat(output.get("error"))
                    .as("iteration " + i + " must compile without error")
                    .isNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
            String serialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wf);
            if (reference == null) {
                reference = serialized;
                referenceWf = wf;
                // One-time visual proof: dump the compiled WorkflowDef to
                // stdout. When ``./gradlew test --info`` is used, this lands
                // in the build log so reviewers can see the structure.
                System.out.println(
                        "\n=== PAC determinism proof: compiled WorkflowDef (printed once, "
                                + "all 10 iterations are byte-equal) ===");
                System.out.println(reference);
                System.out.println("=== end proof ===\n");
            } else {
                assertThat(serialized)
                        .as("iteration " + i + " must be byte-equal to iteration 0")
                        .isEqualTo(reference);
            }
        }

        // Sanity: the compiled output actually contains every task type we
        // claimed to route to. Without this the determinism check could
        // pass trivially on a degenerate plan.
        List<Map<String, Object>> all = allTasks(referenceWf);
        long subCount = all.stream().filter(t -> "SUB_WORKFLOW".equals(t.get("type"))).count();
        long mcpCount = all.stream().filter(t -> "CALL_MCP_TOOL".equals(t.get("type"))).count();
        long httpCount = all.stream().filter(t -> "HTTP".equals(t.get("type"))).count();
        long humanCount = all.stream().filter(t -> "HUMAN".equals(t.get("type"))).count();
        long simpleCount = all.stream().filter(t -> "SIMPLE".equals(t.get("type"))).count();
        assertThat(subCount).as("two agent_tool ops → two SUB_WORKFLOWs").isEqualTo(2);
        assertThat(mcpCount).as("one mcp op → one CALL_MCP_TOOL").isEqualTo(1);
        assertThat(httpCount).as("one http op → one HTTP").isEqualTo(1);
        assertThat(humanCount).as("one human op → one HUMAN").isEqualTo(1);
        assertThat(simpleCount).as("one worker validator → one SIMPLE").isGreaterThanOrEqualTo(1);
    }

    @Test
    void testAgentToolValidationOpEmitsSubWorkflow() {
        // The validation block must also route by tool type. A validator
        // that points at an agent_tool needs SUB_WORKFLOW too — otherwise
        // a validator backed by an agent (e.g. a judge agent) silently
        // becomes a SIMPLE that hangs.
        String planJson =
                """
                {
                  "steps": [{"id": "s1", "operations": [
                    {"tool": "do_thing", "args": {}}
                  ]}],
                  "validation": [
                    {"tool": "judge", "args": {"request": "is it good?"}, "success_condition": "$.passed === true"}
                  ]
                }""";
        Map<String, Object> worker = Map.of("name", "do_thing", "toolType", "worker");
        Map<String, Object> judge = new HashMap<>();
        judge.put("name", "judge");
        judge.put("toolType", "agent_tool");
        judge.put("config", Map.of("workflowName", "judge_agent_wf"));
        Map<String, Object> output =
                runWithParentTools(
                        planJson, null, List.of("do_thing", "judge"), List.of(worker, judge));
        assertThat(output.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) output.get("workflowDef");
        List<Map<String, Object>> all = allTasks(wf);
        // Exactly one SUB_WORKFLOW task: the judge validator.
        long subCount = all.stream().filter(t -> "SUB_WORKFLOW".equals(t.get("type"))).count();
        assertThat(subCount)
                .as("validation block should route judge through SUB_WORKFLOW")
                .isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    //  $ref — cross-step data flow (Ref helper on the SDK side)
    // -----------------------------------------------------------------------

    /**
     * The happy path: step B reads {"$ref": "a"} from step A's output and the compiler rewrites it
     * to ${<a_ref>.output.result}. Verifies the rewritten value is a Conductor template, not the
     * literal $ref dict.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testRefRewritesToConductorTemplate() {
        String plan =
                "{ \"steps\": ["
                        + "  {\"id\": \"a\", \"operations\": [{\"tool\": \"producer\", \"args\": {\"x\": 1}}]},"
                        + "  {\"id\": \"b\", \"depends_on\": [\"a\"], \"operations\": ["
                        + "    {\"tool\": \"consumer\", \"args\": {\"input\": {\"$ref\": \"a\"}}}"
                        + "  ]}"
                        + "] }";
        Map<String, Object> wf = compilePlan(plan);
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> consumer =
                all.stream()
                        .filter(t -> "consumer".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow();
        Object input = ((Map<String, Object>) consumer.get("inputParameters")).get("input");
        assertThat(input)
                .as(
                        "$ref must be replaced by a Conductor ${...} template, not left as {\"$ref\":...}")
                .isInstanceOf(String.class);
        // Sequential steps end with a step_output_<id> INLINE that normalises
        // dict-vs-string returns; Ref resolves to its `.output.result`.
        assertThat((String) input).startsWith("${step_output_").endsWith(".output.result}");
    }

    /** $ref to a step that doesn't exist in the plan is a hard compile error. */
    @Test
    void testRefToUnknownStepIsCompileError() {
        String plan =
                "{ \"steps\": ["
                        + "  {\"id\": \"a\", \"operations\": ["
                        + "    {\"tool\": \"consumer\", \"args\": {\"x\": {\"$ref\": \"missing\"}}}"
                        + "  ]}"
                        + "] }";
        String err = compilePlanExpectError(plan);
        assertThat(err).contains("$ref").contains("missing");
    }

    /** $ref to a step that exists but isn't in depends_on is a compile error. */
    @Test
    void testRefWithoutDependsOnIsCompileError() {
        String plan =
                "{ \"steps\": ["
                        + "  {\"id\": \"a\", \"operations\": [{\"tool\": \"producer\", \"args\": {}}]},"
                        + "  {\"id\": \"b\", \"operations\": ["
                        // depends_on intentionally missing — must error.
                        + "    {\"tool\": \"consumer\", \"args\": {\"input\": {\"$ref\": \"a\"}}}"
                        + "  ]}"
                        + "] }";
        String err = compilePlanExpectError(plan);
        assertThat(err).contains("$refs").contains("depends_on");
    }

    /**
     * Self-Ref is meaningless and surfaces as a compile error instead of an infinite-template trap.
     */
    @Test
    void testSelfRefIsCompileError() {
        String plan =
                "{ \"steps\": ["
                        + "  {\"id\": \"a\", \"depends_on\": [\"a\"], \"operations\": ["
                        + "    {\"tool\": \"consumer\", \"args\": {\"x\": {\"$ref\": \"a\"}}}"
                        + "  ]}"
                        + "] }";
        String err = compilePlanExpectError(plan);
        assertThat(err).contains("self-referential").contains("a");
    }

    /**
     * For a parallel step, $ref resolves to the FORK_JOIN aggregator INLINE (which carries an array
     * of per-branch results), not to one specific branch's task.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testRefToParallelStepUsesAggregator() {
        String plan =
                "{ \"steps\": ["
                        + "  {\"id\": \"a\", \"parallel\": true, \"operations\": ["
                        + "    {\"tool\": \"producer\", \"args\": {\"i\": 0}},"
                        + "    {\"tool\": \"producer\", \"args\": {\"i\": 1}}"
                        + "  ]},"
                        + "  {\"id\": \"b\", \"depends_on\": [\"a\"], \"operations\": ["
                        + "    {\"tool\": \"consumer\", \"args\": {\"all\": {\"$ref\": \"a\"}}}"
                        + "  ]}"
                        + "] }";
        Map<String, Object> wf = compilePlan(plan);
        List<Map<String, Object>> all = allTasks(wf);
        Map<String, Object> consumer =
                all.stream()
                        .filter(t -> "consumer".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow();
        Object all_ = ((Map<String, Object>) consumer.get("inputParameters")).get("all");
        assertThat(all_).isInstanceOf(String.class);
        assertThat((String) all_)
                .as("parallel-step $ref must point at the parallel_agg_<stepId> INLINE aggregator")
                .contains("parallel_agg_a");
    }

    // -----------------------------------------------------------------------
    //  Parallel-Ref consumer-shape type check (dg-review #5)
    // -----------------------------------------------------------------------
    //
    // A parallel step's primary output is an aggregator array. A downstream
    // op that pipes that step's whole output via Ref into an arg whose
    // ToolConfig.inputSchema declares a scalar/object type is a type error.
    // PAC catches this at compile time so the user fails fast in the doc
    // surface instead of 5 task-references deep at run time.

    @Test
    void testParallelRef_intoScalarArg_failsCompile() {
        // Consumer 'consume_doc' declares args.document as type:object.
        // Producer 's_fan' is parallel — its $ref resolves to an array.
        // Compile must reject the plan with a clear diagnostic.
        Map<String, Object> consumerInputSchema =
                Map.of(
                        "type", "object",
                        "properties", Map.of("document", Map.of("type", "object")),
                        "required", List.of("document"));
        Map<String, Object> consumerTool =
                Map.of(
                        "name", "consume_doc",
                        "toolType", "worker",
                        "inputSchema", consumerInputSchema);
        Map<String, Object> producerTool =
                Map.of(
                        "name", "fan_op",
                        "toolType", "worker",
                        "inputSchema", Map.of("type", "object"));
        List<Map<String, Object>> parentTools = List.of(consumerTool, producerTool);

        String planJson =
                """
                {
                  "steps": [
                    {"id": "s_fan", "parallel": true, "operations": [
                      {"tool": "fan_op", "args": {"i": 0}},
                      {"tool": "fan_op", "args": {"i": 1}}
                    ]},
                    {"id": "s_consume", "depends_on": ["s_fan"], "operations": [
                      {"tool": "consume_doc", "args": {"document": {"$ref": "s_fan"}}}
                    ]}
                  ]
                }""";

        Map<String, Object> output =
                runWithParentTools(
                        planJson, /* harnessTimeoutSeconds */
                        null, /* knownToolNames */
                        null,
                        parentTools);
        Object error = output.get("error");
        assertThat(error).as("parallel-Ref into scalar arg must fail compile").isNotNull();
        assertThat(String.valueOf(error))
                .contains("s_fan")
                .contains("parallel")
                .contains("consume_doc");
    }

    @Test
    void testParallelRef_intoArrayArg_compilesOk() {
        // Same producer/consumer wiring, but the consumer declares
        // args.documents as type:array — that matches a parallel agg's
        // shape, so the plan must compile.
        Map<String, Object> consumerInputSchema =
                Map.of(
                        "type", "object",
                        "properties", Map.of("documents", Map.of("type", "array")),
                        "required", List.of("documents"));
        Map<String, Object> consumerTool =
                Map.of(
                        "name", "consume_docs",
                        "toolType", "worker",
                        "inputSchema", consumerInputSchema);
        Map<String, Object> producerTool =
                Map.of(
                        "name", "fan_op",
                        "toolType", "worker",
                        "inputSchema", Map.of("type", "object"));
        List<Map<String, Object>> parentTools = List.of(consumerTool, producerTool);

        String planJson =
                """
                {
                  "steps": [
                    {"id": "s_fan", "parallel": true, "operations": [
                      {"tool": "fan_op", "args": {"i": 0}},
                      {"tool": "fan_op", "args": {"i": 1}}
                    ]},
                    {"id": "s_consume", "depends_on": ["s_fan"], "operations": [
                      {"tool": "consume_docs", "args": {"documents": {"$ref": "s_fan"}}}
                    ]}
                  ]
                }""";

        Map<String, Object> output =
                runWithParentTools(
                        planJson, /* harnessTimeoutSeconds */
                        null, /* knownToolNames */
                        null,
                        parentTools);
        assertThat(output.get("error"))
                .as("array-typed consumer must accept parallel-Ref")
                .isNull();
    }

    @Test
    void testSequentialRef_intoScalarArg_compilesOk() {
        // Producer 's_seq' is NOT parallel — its $ref resolves to a scalar
        // (whatever shape the step's single op returns). Object-typed
        // consumer must compile fine — only parallel producers trigger the
        // type mismatch.
        Map<String, Object> consumerInputSchema =
                Map.of(
                        "type", "object",
                        "properties", Map.of("document", Map.of("type", "object")),
                        "required", List.of("document"));
        Map<String, Object> consumerTool =
                Map.of(
                        "name", "consume_doc",
                        "toolType", "worker",
                        "inputSchema", consumerInputSchema);
        Map<String, Object> producerTool =
                Map.of(
                        "name", "seq_op",
                        "toolType", "worker",
                        "inputSchema", Map.of("type", "object"));
        List<Map<String, Object>> parentTools = List.of(consumerTool, producerTool);

        String planJson =
                """
                {
                  "steps": [
                    {"id": "s_seq", "operations": [
                      {"tool": "seq_op", "args": {"i": 0}}
                    ]},
                    {"id": "s_consume", "depends_on": ["s_seq"], "operations": [
                      {"tool": "consume_doc", "args": {"document": {"$ref": "s_seq"}}}
                    ]}
                  ]
                }""";

        Map<String, Object> output =
                runWithParentTools(
                        planJson, /* harnessTimeoutSeconds */
                        null, /* knownToolNames */
                        null,
                        parentTools);
        assertThat(output.get("error"))
                .as("sequential-Ref into scalar arg must compile ok")
                .isNull();
    }

    // -----------------------------------------------------------------------
    //  Schema validator INLINE for generate ops (dg-review #12 / F3)
    // -----------------------------------------------------------------------
    //
    // PAC inserts a ``v_<stepId>`` INLINE between the parse INLINE and the
    // SIMPLE so the LLM's output is validated against the consumer tool's
    // ``inputSchema`` before it flows into the tool's task. The same SWITCH
    // routes parse OR schema failures to TERMINATE.

    @Test
    void testGenerateOp_emits_schema_validator_inline_between_parse_and_switch() {
        Map<String, Object> writeFileInputSchema =
                Map.of(
                        "type", "object",
                        "properties",
                                Map.of(
                                        "path",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "pattern",
                                                        "^out/.+\\.md$"),
                                        "content", Map.of("type", "string", "minLength", 1)),
                        "required", List.of("path", "content"));
        Map<String, Object> writeFile =
                Map.of(
                        "name", "write_file",
                        "toolType", "worker",
                        "inputSchema", writeFileInputSchema);

        String planJson =
                """
                {
                  "steps": [{"id": "write", "operations": [
                    {"tool": "write_file", "generate": {
                      "instructions": "compose a section",
                      "output_schema": "{\\"path\\":\\"out/intro.md\\",\\"content\\":\\"...\\"}"
                    }}
                  ]}]
                }""";

        Map<String, Object> wf = compilePlanWithParentTools(planJson, List.of(writeFile));
        List<Map<String, Object>> tasks = allTasks(wf);

        // Validator INLINE must exist with ref starting v_write_
        Map<String, Object> validator =
                tasks.stream()
                        .filter(
                                t ->
                                        "INLINE".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("v_write_"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "missing v_<stepId> validator INLINE — schema-validator not emitted"));
        Map<String, Object> vInputs = (Map<String, Object>) validator.get("inputParameters");
        // The validator's schema input must be the tool's inputSchema (not an empty Map).
        Map<String, Object> schemaInput = (Map<String, Object>) vInputs.get("schema");
        assertThat(schemaInput).containsKey("properties");
        assertThat((Map<String, Object>) schemaInput.get("properties"))
                .containsKeys("path", "content");
        // Expression must reference the validator helper (we just check
        // for a few key tokens — the full JS is exercised in the live tests).
        String expr = String.valueOf(vInputs.get("expression"));
        assertThat(expr)
                .contains("function validate(")
                .contains("__parse_error")
                .contains("schema:");

        // The downstream SWITCH (pgate_) must consume validateRef, not parseRef.
        Map<String, Object> gate =
                tasks.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("pgate_write_"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing pgate_ SWITCH"));
        Map<String, Object> gInputs = (Map<String, Object>) gate.get("inputParameters");
        String parsedExpr = String.valueOf(gInputs.get("parsed"));
        assertThat(parsedExpr)
                .as("parseGate must read validator output (v_), not parse output (p_)")
                .contains("v_write_");

        // The compiled SIMPLE / wrap branch must template values from v_, not p_.
        boolean anySimpleUsesValidator =
                tasks.stream()
                        .filter(
                                t ->
                                        "SIMPLE".equals(t.get("type"))
                                                || "write_file"
                                                        .equals(String.valueOf(t.get("type"))))
                        .anyMatch(
                                t -> {
                                    Object inputs = t.get("inputParameters");
                                    return inputs != null && inputs.toString().contains("v_write_");
                                });
        assertThat(anySimpleUsesValidator)
                .as("the tool task must source args from the validator's output, not raw parse")
                .isTrue();
    }

    @Test
    void testGenerateOp_validator_passes_through_when_tool_has_no_input_schema() {
        // Legacy callers without parentTools (no inputSchema) must NOT
        // accidentally fail compilation just because we added the validator.
        // Without an inputSchema the validator is a structural no-op (passes
        // ``parsed`` through unchanged on the happy path).
        String planJson =
                """
                {
                  "steps": [{"id": "write", "operations": [
                    {"tool": "write_file", "generate": {
                      "instructions": "compose",
                      "output_schema": "{\\"path\\":\\"x\\",\\"content\\":\\"y\\"}"
                    }}
                  ]}]
                }""";

        Map<String, Object> wf = compilePlan(planJson);
        List<Map<String, Object>> tasks = allTasks(wf);
        // The validator INLINE should still be emitted — even if the schema
        // is empty Map.of(), the structural slot exists for consistent
        // task graphs.
        boolean hasValidator =
                tasks.stream()
                        .anyMatch(
                                t ->
                                        "INLINE".equals(t.get("type"))
                                                && String.valueOf(t.get("taskReferenceName"))
                                                        .startsWith("v_write_"));
        assertThat(hasValidator).isTrue();
    }

    private Map<String, Object> compilePlanWithParentTools(
            String planJson, List<Map<String, Object>> parentTools) {
        Map<String, Object> output =
                runWithParentTools(
                        planJson, /* harnessTimeoutSeconds */
                        null, /* knownToolNames */
                        null,
                        parentTools);
        Object error = output.get("error");
        if (error != null) {
            throw new AssertionError("Plan compilation failed: " + error);
        }
        return (Map<String, Object>) output.get("workflowDef");
    }

    // ── /dg #6: inspectPlan public API ──────────────────────────

    /**
     * Build a mutable plan map — PAC's compile mutates steps in-place (auto-IDs missing entries,
     * threads ordinal numbers through), so {@code Map.of()} immutable literals trip {@code
     * UnsupportedOperationException}. Tests use this helper for any plan handed to {@link
     * PlanAndCompileTask#inspectPlan}.
     */
    private static Map<String, Object> mutablePlan(Map<String, Object> arg, String tool) {
        HashMap<String, Object> op = new HashMap<>();
        op.put("tool", tool);
        op.put("args", arg);
        HashMap<String, Object> step = new HashMap<>();
        step.put("id", "step_1");
        step.put("operations", new ArrayList<>(List.of(op)));
        HashMap<String, Object> plan = new HashMap<>();
        plan.put("steps", new ArrayList<>(List.of(step)));
        return plan;
    }

    @Test
    void inspectPlan_returnsCompiledWorkflowDefForValidPlan() {
        // Same compile logic that PAC runs at workflow-execution time,
        // exposed for callers (the inspect-plan REST endpoint) that
        // want to see what would be produced without running it.
        HashMap<String, Object> args = new HashMap<>();
        args.put("text", "hello");
        Map<String, Object> plan = mutablePlan(args, "echo");
        PlanAndCompileTask.InspectResult r =
                task.inspectPlan(
                        plan,
                        "inspect_test_wf",
                        "openai/gpt-4o-mini",
                        60,
                        java.util.Set.of("echo"),
                        Map.of());
        assertThat(r.error).isNull();
        assertThat(r.workflowDef).isNotNull();
        assertThat(r.workflowDef).containsKey("tasks");
        assertThat(r.stats).containsKey("stepCount");
    }

    @Test
    void inspectPlan_surfacesErrorForBadPlan() {
        // Missing ``steps`` → compile rejects with a clear error
        // message. inspectPlan must surface that error rather than
        // throwing, so the REST endpoint can return it as a structured
        // 200 response (the request was valid, the plan wasn't).
        HashMap<String, Object> badPlan = new HashMap<>();
        badPlan.put("not_steps", new ArrayList<>());
        PlanAndCompileTask.InspectResult r =
                task.inspectPlan(
                        badPlan,
                        "inspect_test_wf",
                        "openai/gpt-4o-mini",
                        60,
                        java.util.Set.of("echo"),
                        Map.of());
        assertThat(r.error).isNotNull().contains("steps");
        assertThat(r.workflowDef).isNull();
    }

    @Test
    void inspectPlan_surfacesUnknownToolError() {
        // The PAC tool whitelist is the same in the inspect path as in
        // the runtime path — an op.tool not in knownToolNames must
        // produce a compile error even from inspect.
        Map<String, Object> plan = mutablePlan(new HashMap<>(), "send_email");
        PlanAndCompileTask.InspectResult r =
                task.inspectPlan(
                        plan,
                        "inspect_test_wf",
                        "openai/gpt-4o-mini",
                        60,
                        java.util.Set.of("echo"),
                        Map.of());
        assertThat(r.error).isNotNull().contains("send_email").contains("unknown tool");
    }

    /** Quick JSON string-encode for inlining into a plan literal. */
    private String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
