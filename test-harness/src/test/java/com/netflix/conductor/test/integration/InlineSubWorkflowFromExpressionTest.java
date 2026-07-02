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
package com.netflix.conductor.test.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for SubWorkflowTaskMapper's ability to resolve a String expression in
 * SubWorkflowParams.workflowDefinition at runtime.
 *
 * <p>Scenario: A parent workflow first runs a SIMPLE task (wf_builder) whose output contains a
 * complete WorkflowDef Map. The SUB_WORKFLOW task that follows has:
 *
 * <pre>subWorkflowParam.workflowDefinition = "${wf_builder.output.result}"</pre>
 *
 * SubWorkflowTaskMapper resolves the expression via getTaskInputV2, converting the String to the
 * actual Map. SubWorkflow.start() then converts that Map to a WorkflowDef via ObjectMapper and
 * starts the inline sub-workflow — with no prior HTTP registration required.
 *
 * <p>Complex sub-workflow structure (exercises all four requirements):
 *
 * <pre>
 * DO_WHILE (2 iterations, parameterised by workflow.input.iterations):
 *   - INLINE  (compute):    JavaScript — product = iteration × threshold
 *   - SWITCH  (route):      JavaScript — product > threshold?
 *       true branch  → SIMPLE integration_task_1 ("high" path, needs worker)
 *       default      → INLINE low_result (auto-executes)
 * INLINE (final_result):   JavaScript summary after the loop — auto-executes
 * </pre>
 *
 * <p>With iterations=2, threshold=5:
 *
 * <ul>
 *   <li>Iteration 1: product = 1×5 = 5, 5 > 5 = false → low_result (auto)
 *   <li>Iteration 2: product = 2×5 = 10, 10 > 5 = true → integration_task_1 (polled)
 *   <li>After loop: final_result produces {loopsDone: 2, allDone: true}
 * </ul>
 */
class InlineSubWorkflowFromExpressionTest extends AbstractSpecification {

    @Autowired SubWorkflow subWorkflowTask;

    @Test
    @DisplayName(
            "Sub-workflow definition from a String expression runs a complex DO_WHILE with SWITCH, INLINE, and SIMPLE tasks")
    void subWorkflowDefinitionFromStringExpressionRunsComplexDoWhileWithSwitchInlineAndSimpleTasks()
            throws Exception {
        // given: A complex sub-workflow definition built as a runtime Map (DO_WHILE + SWITCH +
        // INLINE + SIMPLE)
        // This Map is exactly what the wf_builder SIMPLE task will return as its output.
        // SubWorkflow.start() converts it to a WorkflowDef via ObjectMapper.convertValue().
        Map<String, Object> subWfDef = buildComplexSubWorkflowDef();

        // and: A parent workflow whose SUB_WORKFLOW task uses a String expression for
        // workflowDefinition
        WorkflowDef parentWfDef = buildParentWorkflowDef();

        // when: The parent workflow is started with iterations=2 and threshold=5
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("iterations", 2);
        workflowInput.put("threshold", 5);
        String workflowId =
                workflowExecutor.startWorkflow(
                        startWorkflowInput(parentWfDef, "inline-subwf-expr-test", workflowInput));

        // then: The wf_builder task (integration_task_1) is SCHEDULED
        Workflow wf = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(1, wf.getTasks().size());
        assertEquals("integration_task_1", wf.getTasks().get(0).getTaskType());
        assertEquals("wf_builder", wf.getTasks().get(0).getReferenceTaskName());
        assertEquals(Task.Status.SCHEDULED, wf.getTasks().get(0).getStatus());

        // when: wf_builder is polled and completed — returns the complex sub-workflow definition
        Map<String, Object> builderOutput = new HashMap<>();
        builderOutput.put("result", subWfDef);
        Task pollBuilder =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "test.builder.worker", builderOutput);

        // then: wf_builder completed and acknowledged
        verifyPolledAndAcknowledgedTask(pollBuilder);

        // and: The async SUB_WORKFLOW system task is executed
        Task exprSubWfTask =
                workflowExecutionService.getExecutionStatus(workflowId, true).getTasks().stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (exprSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, exprSubWfTask.getTaskId());
        }

        // and: The SUB_WORKFLOW task starts (expression resolved → inline WorkflowDef created)
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow status =
                                    workflowExecutionService.getExecutionStatus(workflowId, true);
                            assertEquals(2, status.getTasks().size());
                            assertEquals("SUB_WORKFLOW", status.getTasks().get(1).getTaskType());
                            assertEquals(
                                    Task.Status.IN_PROGRESS, status.getTasks().get(1).getStatus());
                        });

        // when: The sub-workflow ID is retrieved from the SUB_WORKFLOW task
        String subWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(workflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();

        // then: The sub-workflow is RUNNING — the inline WorkflowDef was resolved and started
        Workflow subWf = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());

        // Iteration 1 (product=5, 5>5=false) runs entirely via system tasks (INLINE + SWITCH +
        // INLINE) and advances automatically. Iteration 2 (product=10, 10>5=true) schedules the
        // SIMPLE task.
        // and: integration_task_1 is SCHEDULED for the high-value path in iteration 2 (product >
        // threshold)
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow status =
                                    workflowExecutionService.getExecutionStatus(
                                            subWorkflowId, true);
                            boolean hasHighTask =
                                    status.getTasks().stream()
                                            .anyMatch(
                                                    t ->
                                                            t.getTaskType()
                                                                            .equals(
                                                                                    "integration_task_1")
                                                                    && t.getStatus()
                                                                            == Task.Status.SCHEDULED
                                                                    && "high"
                                                                            .equals(
                                                                                    t.getInputData()
                                                                                            .get(
                                                                                                    "category")));
                            assertTrue(
                                    hasHighTask,
                                    "Expected integration_task_1 SCHEDULED with category=high");
                        });

        // when: The integration_task_1 for the high-value path (iteration 2) is polled and
        // completed
        Map<String, Object> highTaskOutput = new HashMap<>();
        highTaskOutput.put("label", "high");
        highTaskOutput.put("done", true);
        Task pollHighTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "test.high.worker", highTaskOutput);

        // then: integration_task_1 completed and acknowledged
        verifyPolledAndAcknowledgedTask(pollHighTask);

        // After iteration 2 completes: condition 2 < 2 = false → DO_WHILE exits.
        // final_result INLINE then auto-executes and produces {loopsDone: 2, allDone: true}.
        // and: The sub-workflow COMPLETES with the expected loop summary output
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow status =
                                    workflowExecutionService.getExecutionStatus(
                                            subWorkflowId, true);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, status.getStatus());
                            // outputParameters mapped from final_result INLINE output
                            assertEquals(2, status.getOutput().get("loopsDone"));
                            assertEquals(true, status.getOutput().get("allDone"));
                        });

        // and: The parent workflow COMPLETES — SUB_WORKFLOW task succeeded
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow status =
                                    workflowExecutionService.getExecutionStatus(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, status.getStatus());
                            assertEquals("SUB_WORKFLOW", status.getTasks().get(1).getTaskType());
                            assertEquals(
                                    Task.Status.COMPLETED, status.getTasks().get(1).getStatus());
                        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds the parent workflow definition programmatically.
     *
     * <p>Task 1 — wf_builder (SIMPLE, integration_task_1): Worker returns the complex sub-workflow
     * definition as output["result"].
     *
     * <p>Task 2 — exec (SUB_WORKFLOW): subWorkflowParam.workflowDefinition is set to the String
     * expression "${wf_builder.output.result}". SubWorkflowTaskMapper resolves this expression at
     * runtime to the actual Map and injects it as the inline sub-workflow definition.
     */
    private static WorkflowDef buildParentWorkflowDef() {
        // Task 1: SIMPLE task that produces the sub-workflow definition
        WorkflowTask builderTask = new WorkflowTask();
        builderTask.setName("integration_task_1");
        builderTask.setTaskReferenceName("wf_builder");
        builderTask.setType("SIMPLE");
        Map<String, Object> builderInputParams = new HashMap<>();
        builderInputParams.put("tp1", "${workflow.input.threshold}");
        builderInputParams.put("tp2", "${workflow.input.iterations}");
        builderTask.setInputParameters(builderInputParams);

        // Task 2: SUB_WORKFLOW whose workflowDefinition is a String expression
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec_plan");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setType("SUB_WORKFLOW");
        // These inputParameters become the sub-workflow's workflow.input
        Map<String, Object> subWfInputParams = new HashMap<>();
        subWfInputParams.put("threshold", "${workflow.input.threshold}");
        subWfInputParams.put("iterations", "${workflow.input.iterations}");
        subWfTask.setInputParameters(subWfInputParams);

        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName("complex_dynamic_plan_wf");
        subParams.setVersion(1);
        // String expression — resolved by the fixed SubWorkflowTaskMapper
        subParams.setWorkflowDefinition("${wf_builder.output.result}");
        subWfTask.setSubWorkflowParam(subParams);

        WorkflowDef wfDef = new WorkflowDef();
        wfDef.setName("test_inline_subwf_expr_parent_wf");
        wfDef.setVersion(1);
        wfDef.setSchemaVersion(2);
        wfDef.setOwnerEmail("test@harness.com");
        wfDef.setTasks(List.of(builderTask, subWfTask));
        wfDef.setInputParameters(List.of("iterations", "threshold"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("loopsDone", "${exec.output.loopsDone}");
        outputParameters.put("allDone", "${exec.output.allDone}");
        wfDef.setOutputParameters(outputParameters);
        return wfDef;
    }

    /**
     * Builds the complex sub-workflow definition as a plain Map.
     *
     * <p>This Map is returned as the output of the wf_builder SIMPLE task. SubWorkflow.start()
     * receives it via inputData["subWorkflowDefinition"] and converts it to a WorkflowDef via
     * ObjectMapper.convertValue().
     *
     * <p>Structure:
     *
     * <pre>
     * DO_WHILE (do_loop):
     *   loopCondition: $.do_loop['iteration'] < $.iters   (parametrised by input)
     *   loopOver:
     *     INLINE  compute    — product = iteration × threshold   (JavaScript)
     *     SWITCH  route      — $.product > $.threshold           (JavaScript)
     *       case "true" → SIMPLE integration_task_1 ref=high_task  (needs worker)
     *       default     → INLINE low_result                         (auto)
     * INLINE  final_result   — {loopsDone: iteration, allDone: true}
     * </pre>
     *
     * inputParameters : ["iterations", "threshold"] outputParameters: loopsDone, allDone (mapped
     * from final_result output)
     */
    private static Map<String, Object> buildComplexSubWorkflowDef() {
        // INLINE: compute product = iteration * threshold
        Map<String, Object> computeInputParams = new HashMap<>();
        computeInputParams.put("evaluatorType", "javascript");
        computeInputParams.put(
                "expression", "function f() { return $.iteration * $.threshold; } f();");
        computeInputParams.put("iteration", "${do_loop.output.iteration}");
        computeInputParams.put("threshold", "${workflow.input.threshold}");
        Map<String, Object> computeTask = new HashMap<>();
        computeTask.put("name", "compute");
        computeTask.put("taskReferenceName", "compute");
        computeTask.put("type", "INLINE");
        computeTask.put("inputParameters", computeInputParams);

        // SIMPLE: executed only when product > threshold (high-value branch)
        Map<String, Object> highTaskInputParams = new HashMap<>();
        highTaskInputParams.put("product", "${compute.output.result}");
        highTaskInputParams.put("category", "high");
        Map<String, Object> highTask = new HashMap<>();
        highTask.put("name", "integration_task_1");
        highTask.put("taskReferenceName", "high_task");
        highTask.put("type", "SIMPLE");
        highTask.put("inputParameters", highTaskInputParams);

        // INLINE: executed when product <= threshold (low-value branch, auto-completes)
        Map<String, Object> lowResultInputParams = new HashMap<>();
        lowResultInputParams.put("evaluatorType", "javascript");
        lowResultInputParams.put(
                "expression", "function f() { return {label: \"low\", product: $.product}; } f();");
        lowResultInputParams.put("product", "${compute.output.result}");
        Map<String, Object> lowResultTask = new HashMap<>();
        lowResultTask.put("name", "low_result");
        lowResultTask.put("taskReferenceName", "low_result");
        lowResultTask.put("type", "INLINE");
        lowResultTask.put("inputParameters", lowResultInputParams);

        // SWITCH: routes on product > threshold using JavaScript evaluator
        Map<String, Object> routeInputParams = new HashMap<>();
        routeInputParams.put("product", "${compute.output.result}");
        routeInputParams.put("threshold", "${workflow.input.threshold}");
        Map<String, Object> decisionCases = new HashMap<>();
        decisionCases.put("true", List.of(highTask));
        Map<String, Object> routeTask = new HashMap<>();
        routeTask.put("name", "route");
        routeTask.put("taskReferenceName", "route");
        routeTask.put("type", "SWITCH");
        routeTask.put("evaluatorType", "javascript");
        routeTask.put("expression", "$.product > $.threshold");
        routeTask.put("inputParameters", routeInputParams);
        routeTask.put("decisionCases", decisionCases);
        routeTask.put("defaultCase", List.of(lowResultTask));

        // DO_WHILE: loops $.iters times; iters and threshold come from sub-workflow input
        Map<String, Object> doLoopInputParams = new HashMap<>();
        doLoopInputParams.put("iters", "${workflow.input.iterations}");
        doLoopInputParams.put("threshold", "${workflow.input.threshold}");
        Map<String, Object> doLoopTask = new HashMap<>();
        doLoopTask.put("name", "do_loop");
        doLoopTask.put("taskReferenceName", "do_loop");
        doLoopTask.put("type", "DO_WHILE");
        doLoopTask.put("inputParameters", doLoopInputParams);
        doLoopTask.put("loopCondition", "$.do_loop['iteration'] < $.iters");
        doLoopTask.put("loopOver", List.of(computeTask, routeTask));

        // INLINE: summarises results after DO_WHILE exits
        Map<String, Object> finalResultInputParams = new HashMap<>();
        finalResultInputParams.put("evaluatorType", "javascript");
        finalResultInputParams.put(
                "expression",
                "function f() { return {loopsDone: $.loopsDone, allDone: true}; } f();");
        finalResultInputParams.put("loopsDone", "${do_loop.output.iteration}");
        Map<String, Object> finalResultTask = new HashMap<>();
        finalResultTask.put("name", "final_result");
        finalResultTask.put("taskReferenceName", "final_result");
        finalResultTask.put("type", "INLINE");
        finalResultTask.put("inputParameters", finalResultInputParams);

        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("loopsDone", "${final_result.output.result.loopsDone}");
        outputParameters.put("allDone", "${final_result.output.result.allDone}");

        Map<String, Object> subWfDef = new HashMap<>();
        subWfDef.put("name", "complex_dynamic_plan_wf");
        subWfDef.put("version", 1);
        subWfDef.put("schemaVersion", 2);
        subWfDef.put("tasks", List.of(doLoopTask, finalResultTask));
        subWfDef.put("inputParameters", List.of("iterations", "threshold"));
        subWfDef.put("outputParameters", outputParameters);
        subWfDef.put("timeoutPolicy", "ALERT_ONLY");
        subWfDef.put("timeoutSeconds", 0);
        return subWfDef;
    }
}
