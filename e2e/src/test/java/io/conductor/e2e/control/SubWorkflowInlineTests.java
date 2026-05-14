/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.control;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubWorkflowInlineTests {

    @Test
    public void testSubWorkflow0version() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String parentWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        registerInlineWorkflowDef(parentWorkflowName, metadataClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // User1 should be able to complete task/workflow
        String taskId = workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(taskId);
        taskClient.updateTask(taskResult);

        // Workflow will be still running state
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus(), Task.Status.COMPLETED);
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus(),
                                    Task.Status.IN_PROGRESS);
                        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        String subWorkflowId = workflow.getTasks().get(1).getSubWorkflowId();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subWorkflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(workflow.getTasks().get(1).getTaskId());
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus(), Task.Status.COMPLETED);
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus(), Task.Status.COMPLETED);
                        });

        // Cleanup
        metadataClient.unregisterWorkflowDef(parentWorkflowName, 1);
    }

    /**
     * Tests that SubWorkflowParams.workflowDefinition resolved from a runtime String expression
     * executes a complex inline sub-workflow with DO_WHILE, SWITCH, INLINE, and SIMPLE tasks.
     *
     * <p>Setup: The parent workflow has two tasks:
     *
     * <ol>
     *   <li>wf_builder (SIMPLE) — the test completes this task with a full WorkflowDef Map as
     *       output["result"]. This simulates a planner agent that generates an execution plan.
     *   <li>exec (SUB_WORKFLOW) — subWorkflowParam.workflowDefinition =
     *       "${wf_builder.output.result}" (a String expression). SubWorkflowTaskMapper resolves the
     *       expression at runtime, so SubWorkflow.start() receives the concrete Map and converts it
     *       to a WorkflowDef without any prior HTTP registration.
     * </ol>
     *
     * <p>Inline sub-workflow structure (iterations=2, threshold=5):
     *
     * <ul>
     *   <li>DO_WHILE (do_loop): runs 2 iterations driven by workflow.input.iterations
     *       <ul>
     *         <li>INLINE compute: product = iteration × threshold (JavaScript)
     *         <li>SWITCH route (JavaScript): product > threshold?
     *             <ul>
     *               <li>true → SIMPLE high_task (manually completed by the test — iteration 2 only:
     *                   product=10 > threshold=5)
     *               <li>default → INLINE low_result (auto-executes — iteration 1: product=5 =
     *                   threshold=5)
     *             </ul>
     *       </ul>
     *   <li>INLINE final_result: produces {loopsDone: 2, allDone: true}
     * </ul>
     */
    @Test
    public void testSubWorkflowDefinitionFromStringExpression() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        // Use random suffixes to avoid naming conflicts across test runs
        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "inline_expr_parent_" + suffix;
        String wfBuilderTaskName = "wf_builder_task_" + suffix;
        String highPathTaskName = "high_path_task_" + suffix;

        // Register task definitions for the two SIMPLE tasks used in the test
        TaskDef wfBuilderDef = new TaskDef(wfBuilderTaskName);
        wfBuilderDef.setOwnerEmail("test@conductor.io");
        TaskDef highPathDef = new TaskDef(highPathTaskName);
        highPathDef.setOwnerEmail("test@conductor.io");
        metadataClient.registerTaskDefs(List.of(wfBuilderDef, highPathDef));

        // Build the complex sub-workflow definition as a plain Map.
        // This is the value the wf_builder SIMPLE task will return as output["result"].
        // SubWorkflow.start() receives it via inputData["subWorkflowDefinition"] and converts it
        // to a WorkflowDef via ObjectMapper.convertValue() — no prior registration needed.
        Map<String, Object> subWfDef = buildComplexSubWfDefMap(highPathTaskName);

        // Register the parent workflow.  The SUB_WORKFLOW task's workflowDefinition is a
        // String expression "${wf_builder.output.result}" rather than a hardcoded object.
        WorkflowDef parentDef = buildParentWorkflowDef(parentWfName, wfBuilderTaskName);
        metadataClient.updateWorkflowDefs(List.of(parentDef));

        // Start the workflow
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(parentWfName);
        request.setVersion(1);
        request.setInput(Map.of("iterations", 2, "threshold", 5));
        String workflowId = workflowClient.startWorkflow(request);

        try {
            // Step 1: wf_builder SIMPLE task is SCHEDULED — wait then complete it with the
            //         sub-workflow definition Map as the "result" output key.
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(1, wf.getTasks().size());
                                assertEquals(
                                        Task.Status.SCHEDULED, wf.getTasks().get(0).getStatus());
                            });

            String builderTaskId =
                    workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getTaskId();
            TaskResult builderResult = new TaskResult();
            builderResult.setWorkflowInstanceId(workflowId);
            builderResult.setTaskId(builderTaskId);
            builderResult.setStatus(TaskResult.Status.COMPLETED);
            // The sub-workflow definition Map is passed as output["result"].
            // The expression "${wf_builder.output.result}" resolves to this Map at runtime.
            builderResult.setOutputData(Map.of("result", subWfDef));
            taskClient.updateTask(builderResult);

            // Step 2: The SUB_WORKFLOW task must become IN_PROGRESS, meaning the expression was
            //         resolved to the Map and SubWorkflow.start() launched the sub-workflow.
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(2, wf.getTasks().size());
                                assertEquals(
                                        Task.Status.IN_PROGRESS, wf.getTasks().get(1).getStatus());
                                assertNotNull(wf.getTasks().get(1).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(1)
                            .getSubWorkflowId();

            // Step 3: Iteration 1 (product = 1×5 = 5, 5 > 5 = false) runs entirely via system
            //         tasks (INLINE compute + SWITCH route + INLINE low_result) and completes
            //         automatically on the server.  Iteration 2 then starts and schedules
            //         high_task (product = 2×5 = 10, 10 > 5 = true → SIMPLE high_task).
            await().pollInterval(500, TimeUnit.MILLISECONDS)
                    .atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow subWf = workflowClient.getWorkflow(subWorkflowId, true);
                                // high_task__2 is the reference name in iteration 2
                                Task highTask = subWf.getTaskByRefName("high_task__2");
                                assertNotNull(
                                        highTask,
                                        "high_task__2 should be scheduled in iteration 2");
                                assertEquals(Task.Status.SCHEDULED, highTask.getStatus());
                                assertEquals(
                                        "high",
                                        highTask.getInputData().get("category"),
                                        "task input must carry the category parameter mapping");
                            });

            String highTaskId =
                    workflowClient
                            .getWorkflow(subWorkflowId, true)
                            .getTaskByRefName("high_task__2")
                            .getTaskId();
            TaskResult highResult = new TaskResult();
            highResult.setWorkflowInstanceId(subWorkflowId);
            highResult.setTaskId(highTaskId);
            highResult.setStatus(TaskResult.Status.COMPLETED);
            highResult.setOutputData(Map.of("label", "high", "done", true));
            taskClient.updateTask(highResult);

            // Step 4: After high_task completes, the DO_WHILE condition evaluates to false
            //         (2 < 2 = false), the loop exits, final_result INLINE auto-executes, and
            //         the sub-workflow completes with the expected output.
            await().pollInterval(500, TimeUnit.MILLISECONDS)
                    .atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow subWf = workflowClient.getWorkflow(subWorkflowId, true);
                                assertEquals(Workflow.WorkflowStatus.COMPLETED, subWf.getStatus());
                                assertEquals(
                                        2,
                                        subWf.getOutput().get("loopsDone"),
                                        "sub-workflow must report 2 completed loop iterations");
                                assertEquals(
                                        true,
                                        subWf.getOutput().get("allDone"),
                                        "sub-workflow allDone flag must be true");
                            });

            // Step 5: Parent workflow completes once the SUB_WORKFLOW task is done.
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                            });
        } finally {
            // Best-effort cleanup so as not to leave running workflows on the server
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Builds the parent workflow definition programmatically.
     *
     * <p>Task 1 — wf_builder (SIMPLE): The test completes this task with the sub-workflow
     * definition Map as output["result"].
     *
     * <p>Task 2 — exec (SUB_WORKFLOW): subWorkflowParam.workflowDefinition is set to the String
     * expression "${wf_builder.output.result}". SubWorkflowTaskMapper resolves this expression at
     * task-scheduling time via getTaskInputV2 and injects the resolved Map as
     * inputData["subWorkflowDefinition"], which SubWorkflow.start() converts to a WorkflowDef.
     */
    private static WorkflowDef buildParentWorkflowDef(
            String workflowName, String wfBuilderTaskName) {
        WorkflowTask builderTask = new WorkflowTask();
        builderTask.setName(wfBuilderTaskName);
        builderTask.setTaskReferenceName("wf_builder");
        builderTask.setWorkflowTaskType(TaskType.SIMPLE);
        builderTask.setInputParameters(
                Map.of(
                        "tp1", "${workflow.input.threshold}",
                        "tp2", "${workflow.input.iterations}"));

        WorkflowTask execTask = new WorkflowTask();
        execTask.setName("exec_plan");
        execTask.setTaskReferenceName("exec");
        execTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        // Pass workflow inputs through to the sub-workflow
        execTask.setInputParameters(
                Map.of(
                        "threshold", "${workflow.input.threshold}",
                        "iterations", "${workflow.input.iterations}"));

        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName("complex_dynamic_plan_wf");
        subParams.setVersion(1);
        // String expression — SubWorkflowTaskMapper resolves this to the Map at runtime.
        // This is the core feature being tested: no pre-registration of the sub-workflow needed.
        subParams.setWorkflowDefinition("${wf_builder.output.result}");
        execTask.setSubWorkflowParam(subParams);

        WorkflowDef wfDef = new WorkflowDef();
        wfDef.setName(workflowName);
        wfDef.setVersion(1);
        wfDef.setOwnerEmail("test@conductor.io");
        wfDef.setTimeoutSeconds(300);
        wfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        wfDef.setTasks(List.of(builderTask, execTask));
        wfDef.setInputParameters(List.of("iterations", "threshold"));
        wfDef.setOutputParameters(
                Map.of(
                        "loopsDone", "${exec.output.loopsDone}",
                        "allDone", "${exec.output.allDone}"));
        return wfDef;
    }

    /**
     * Builds the complex sub-workflow definition as a plain Map that can be serialized and passed
     * as a SIMPLE task output, then deserialized by SubWorkflow.start() via
     * ObjectMapper.convertValue(Map, WorkflowDef.class).
     *
     * <p>Structure — with iterations=2, threshold=5:
     *
     * <pre>
     * DO_WHILE (do_loop, 2 iterations)
     *   INLINE  compute    — product = iteration × threshold         (JavaScript)
     *   SWITCH  route      — $.product > $.threshold                 (JavaScript)
     *       true    → SIMPLE highPathTaskName  (test polls iteration 2: product=10 > 5)
     *       default → INLINE low_result        (auto — iteration 1: product=5 = threshold=5)
     * INLINE  final_result — {loopsDone: iterationCount, allDone: true}
     * </pre>
     *
     * <p>Parameter mappings are used throughout: workflow.input flows into DO_WHILE inputParameters
     * (iters, threshold), which flow into compute (iteration, threshold), compute.output flows into
     * SWITCH inputParameters and branch tasks, and do_loop.output.iteration flows into
     * final_result.
     */
    private static Map<String, Object> buildComplexSubWfDefMap(String highPathTaskName) {
        // INLINE: compute product = iteration × threshold (JavaScript)
        Map<String, Object> computeInputs = new HashMap<>();
        computeInputs.put("evaluatorType", "javascript");
        computeInputs.put("expression", "function f() { return $.iteration * $.threshold; } f();");
        computeInputs.put("iteration", "${do_loop.output.iteration}");
        computeInputs.put("threshold", "${workflow.input.threshold}");
        Map<String, Object> computeTask = new HashMap<>();
        computeTask.put("name", "compute");
        computeTask.put("taskReferenceName", "compute");
        computeTask.put("type", "INLINE");
        computeTask.put("inputParameters", computeInputs);

        // SIMPLE: high path — scheduled only when product > threshold (iteration 2)
        Map<String, Object> highTaskInputs = new HashMap<>();
        highTaskInputs.put("product", "${compute.output.result}");
        highTaskInputs.put("category", "high");
        Map<String, Object> highTask = new HashMap<>();
        highTask.put("name", highPathTaskName);
        highTask.put("taskReferenceName", "high_task");
        highTask.put("type", "SIMPLE");
        highTask.put("inputParameters", highTaskInputs);

        // INLINE: low path — auto-executes when product <= threshold (iteration 1)
        Map<String, Object> lowTaskInputs = new HashMap<>();
        lowTaskInputs.put("evaluatorType", "javascript");
        lowTaskInputs.put(
                "expression", "function f() { return {label: \"low\", product: $.product}; } f();");
        lowTaskInputs.put("product", "${compute.output.result}");
        Map<String, Object> lowTask = new HashMap<>();
        lowTask.put("name", "low_result");
        lowTask.put("taskReferenceName", "low_result");
        lowTask.put("type", "INLINE");
        lowTask.put("inputParameters", lowTaskInputs);

        // SWITCH: routes on product > threshold using JavaScript evaluator
        Map<String, Object> switchInputs = new HashMap<>();
        switchInputs.put("product", "${compute.output.result}");
        switchInputs.put("threshold", "${workflow.input.threshold}");
        Map<String, Object> routeTask = new HashMap<>();
        routeTask.put("name", "route");
        routeTask.put("taskReferenceName", "route");
        routeTask.put("type", "SWITCH");
        routeTask.put("evaluatorType", "javascript");
        routeTask.put("expression", "$.product > $.threshold");
        routeTask.put("inputParameters", switchInputs);
        routeTask.put("decisionCases", Map.of("true", List.of(highTask)));
        routeTask.put("defaultCase", List.of(lowTask));

        // DO_WHILE: loops $.iters times (2 iterations with iterations=2)
        // loopCondition: $.do_loop['iteration'] < $.iters → runs iterations 1 and 2
        Map<String, Object> loopInputs = new HashMap<>();
        loopInputs.put("iters", "${workflow.input.iterations}");
        loopInputs.put("threshold", "${workflow.input.threshold}");
        Map<String, Object> doLoopTask = new HashMap<>();
        doLoopTask.put("name", "do_loop");
        doLoopTask.put("taskReferenceName", "do_loop");
        doLoopTask.put("type", "DO_WHILE");
        doLoopTask.put("inputParameters", loopInputs);
        doLoopTask.put("loopCondition", "$.do_loop['iteration'] < $.iters");
        doLoopTask.put("loopOver", List.of(computeTask, routeTask));

        // INLINE: summarise results after DO_WHILE exits
        Map<String, Object> finalInputs = new HashMap<>();
        finalInputs.put("evaluatorType", "javascript");
        finalInputs.put(
                "expression",
                "function f() { return {loopsDone: $.loopsDone, allDone: true}; } f();");
        finalInputs.put("loopsDone", "${do_loop.output.iteration}");
        Map<String, Object> finalTask = new HashMap<>();
        finalTask.put("name", "final_result");
        finalTask.put("taskReferenceName", "final_result");
        finalTask.put("type", "INLINE");
        finalTask.put("inputParameters", finalInputs);

        Map<String, Object> subWfDef = new HashMap<>();
        subWfDef.put("name", "complex_dynamic_plan_wf");
        subWfDef.put("version", 1);
        subWfDef.put("schemaVersion", 2);
        subWfDef.put("tasks", List.of(doLoopTask, finalTask));
        subWfDef.put("inputParameters", List.of("iterations", "threshold"));
        subWfDef.put(
                "outputParameters",
                Map.of(
                        "loopsDone", "${final_result.output.result.loopsDone}",
                        "allDone", "${final_result.output.result.allDone}"));
        subWfDef.put("timeoutPolicy", "ALERT_ONLY");
        subWfDef.put("timeoutSeconds", 0);
        return subWfDef;
    }

    /**
     * Verifies that the priority set in SubWorkflowParams is forwarded to the started sub-workflow.
     *
     * <p>SubWorkflow.start() now reads "priority" from task inputData (wired by the mapper from
     * subWorkflowParams.priority) and passes it to StartWorkflowInput. This test confirms the
     * end-to-end propagation: subWorkflowParams.priority=7 → sub-workflow.priority=7.
     */
    @Test
    public void testPriorityPropagatedFromSubWorkflowParams() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "priority_parent_" + suffix;
        String subWfName = "priority_sub_" + suffix;
        String taskName = "priority_task_" + suffix;

        // Register sub-workflow with a single SIMPLE task
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");
        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setName(taskName);
        simpleTask.setTaskReferenceName("t1");
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setVersion(1);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTasks(List.of(simpleTask));
        subWfDef.setTimeoutSeconds(300);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        // Register parent workflow with priority=7 set in subWorkflowParams
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        subParams.setPriority(7);
        subWfTask.setSubWorkflowParam(subParams);
        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(subWfDef, parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            // Wait for the SUB_WORKFLOW task to start and the sub-workflow to be created
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(1, wf.getTasks().size());
                                assertEquals(
                                        Task.Status.IN_PROGRESS, wf.getTasks().get(0).getStatus());
                                assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            // Priority set in subWorkflowParams must be propagated to the sub-workflow instance
            Workflow subWf = workflowClient.getWorkflow(subWorkflowId, false);
            assertEquals(
                    7,
                    subWf.getPriority(),
                    "Sub-workflow priority must match the value set in subWorkflowParams");
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 1);
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that an inline sub-workflow definition resolved from a String expression has
     * "_systemMetadata.dynamic = true" injected into its workflow input by SubWorkflow.start().
     *
     * <p>This allows downstream tasks and tooling to distinguish dynamically-generated
     * sub-workflows from statically-registered ones.
     */
    @Test
    public void testDynamicInlineSubWorkflowMarkedInSystemMetadata() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "dynamic_mark_parent_" + suffix;
        String builderTaskName = "dynamic_mark_builder_" + suffix;

        // Minimal inline sub-workflow def: a single INLINE task that auto-completes
        Map<String, Object> inlineTask = new HashMap<>();
        inlineTask.put("name", "marker_check");
        inlineTask.put("taskReferenceName", "marker_check");
        inlineTask.put("type", "INLINE");
        inlineTask.put(
                "inputParameters", Map.of("evaluatorType", "javascript", "expression", "true;"));

        Map<String, Object> subWfDefMap = new HashMap<>();
        subWfDefMap.put("name", "dynamic_mark_sub_wf");
        subWfDefMap.put("version", 1);
        subWfDefMap.put("schemaVersion", 2);
        subWfDefMap.put("tasks", List.of(inlineTask));
        subWfDefMap.put("inputParameters", List.of());
        subWfDefMap.put("outputParameters", Map.of());
        subWfDefMap.put("timeoutPolicy", "ALERT_ONLY");
        subWfDefMap.put("timeoutSeconds", 0);

        // Parent: wf_builder returns the sub-wf def; exec SUB_WORKFLOW resolves
        // "${wf_builder.output.result}"
        TaskDef builderDef = new TaskDef(builderTaskName);
        builderDef.setOwnerEmail("test@conductor.io");

        WorkflowTask builderTask = new WorkflowTask();
        builderTask.setName(builderTaskName);
        builderTask.setTaskReferenceName("wf_builder");
        builderTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask execTask = new WorkflowTask();
        execTask.setName("exec_dynamic");
        execTask.setTaskReferenceName("exec");
        execTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName("dynamic_mark_sub_wf");
        subParams.setVersion(1);
        subParams.setWorkflowDefinition("${wf_builder.output.result}");
        execTask.setSubWorkflowParam(subParams);

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(builderTask, execTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(builderDef));
        metadataClient.updateWorkflowDefs(List.of(parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            // Complete wf_builder with the inline sub-workflow definition
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(
                                        Task.Status.SCHEDULED, wf.getTasks().get(0).getStatus());
                            });

            String builderTaskId =
                    workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getTaskId();
            TaskResult result = new TaskResult();
            result.setWorkflowInstanceId(workflowId);
            result.setTaskId(builderTaskId);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("result", subWfDefMap));
            taskClient.updateTask(result);

            // Wait until the SUB_WORKFLOW task has a sub-workflow ID — the task may be
            // IN_PROGRESS or already COMPLETED since the inline sub-workflow has only a
            // single auto-executing INLINE task and can finish before we poll.
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(2, wf.getTasks().size());
                                assertNotNull(wf.getTasks().get(1).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(1)
                            .getSubWorkflowId();

            // _systemMetadata.dynamic must be true in the sub-workflow's input
            Workflow subWf = workflowClient.getWorkflow(subWorkflowId, false);
            Object systemMetadata = subWf.getInput().get("_systemMetadata");
            assertNotNull(systemMetadata, "_systemMetadata must be present in sub-workflow input");
            assertTrue(systemMetadata instanceof Map, "_systemMetadata must be a Map");
            assertEquals(
                    true,
                    ((Map<?, ?>) systemMetadata).get("dynamic"),
                    "dynamic flag must be true for inline sub-workflows");
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterTaskDef(builderTaskName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Smoke test: verifies that setting idempotencyKey and idempotencyStrategy in SubWorkflowParams
     * does not break workflow execution. The fields are wired end-to-end through the mapper and
     * executor (StartWorkflowInput); enforcement is implementation-specific and not tested here.
     */
    @Test
    public void testIdempotencyKeyForwardedWithoutBreakingExecution() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "idempotency_parent_" + suffix;
        String subWfName = "idempotency_sub_" + suffix;
        String taskName = "idempotency_task_" + suffix;

        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setName(taskName);
        simpleTask.setTaskReferenceName("t1");
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setVersion(1);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTasks(List.of(simpleTask));
        subWfDef.setTimeoutSeconds(300);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        subParams.setIdempotencyKey("test-idempotency-key-" + suffix);
        subWfTask.setSubWorkflowParam(subParams);

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(subWfDef, parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            // Sub-workflow must start and reach IN_PROGRESS — idempotency key must not prevent
            // normal execution or cause an error when the OSS executor ignores it.
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(1, wf.getTasks().size());
                                assertEquals(
                                        Task.Status.IN_PROGRESS, wf.getTasks().get(0).getStatus());
                                assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            // Complete the task inside the sub-workflow so both workflows finish cleanly
            String innerTaskId =
                    workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId();
            TaskResult innerResult = new TaskResult();
            innerResult.setWorkflowInstanceId(subWorkflowId);
            innerResult.setTaskId(innerTaskId);
            innerResult.setStatus(TaskResult.Status.COMPLETED);
            taskClient.updateTask(innerResult);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    assertEquals(
                                            Workflow.WorkflowStatus.COMPLETED,
                                            workflowClient
                                                    .getWorkflow(workflowId, false)
                                                    .getStatus()));
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 1);
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that an explicit subWorkflowParams.version routes to that exact version, not the
     * latest. This also validates the Number.intValue() fix: the version integer survives the JSON
     * round-trip through the data store (which can deserialise it as a Double) and is parsed
     * correctly. If parseInt("2.0") were used instead, the catch would swallow the error, resolve
     * version=null, and run version 2 (latest) even when version 1 was explicitly requested — a
     * silent correctness bug.
     *
     * <p>Proof structure:
     *
     * <pre>
     * P1. Two versions of the sub-workflow are registered; they differ in their outputParameters:
     *     v1 outputs {version: "v1"}, v2 outputs {version: "v2"}.
     * P2. Parent is configured with subWorkflowParams.version=1.
     * P3. The mapper writes the resolved version into the task's inputData["subWorkflowVersion"].
     * P4. SubWorkflow.start() reads subWorkflowVersion via Number.intValue() → 1.
     * P5. StartWorkflowInput.version=1 → WorkflowExecutor fetches v1 definition.
     * P6. Sub-workflow output must contain {version: "v1"}, not {version: "v2"}.
     * Contrapositive: if we observed {version: "v2"}, the version was not forwarded correctly.
     * </pre>
     */
    @Test
    public void testExplicitVersionRoutesToCorrectSubWorkflowVersion() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "version_parent_" + suffix;
        String subWfName = "version_sub_" + suffix;
        String taskName = "version_task_" + suffix;

        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");

        // v1: task outputs {version: "v1"}
        WorkflowTask taskV1 = new WorkflowTask();
        taskV1.setName(taskName);
        taskV1.setTaskReferenceName("t1");
        taskV1.setWorkflowTaskType(TaskType.SIMPLE);
        WorkflowDef subWfV1 = new WorkflowDef();
        subWfV1.setName(subWfName);
        subWfV1.setVersion(1);
        subWfV1.setOwnerEmail("test@conductor.io");
        subWfV1.setTasks(List.of(taskV1));
        subWfV1.setOutputParameters(Map.of("version", "v1"));
        subWfV1.setTimeoutSeconds(300);
        subWfV1.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        // v2: same task, different output sentinel
        WorkflowTask taskV2 = new WorkflowTask();
        taskV2.setName(taskName);
        taskV2.setTaskReferenceName("t1");
        taskV2.setWorkflowTaskType(TaskType.SIMPLE);
        WorkflowDef subWfV2 = new WorkflowDef();
        subWfV2.setName(subWfName);
        subWfV2.setVersion(2);
        subWfV2.setOwnerEmail("test@conductor.io");
        subWfV2.setTasks(List.of(taskV2));
        subWfV2.setOutputParameters(Map.of("version", "v2"));
        subWfV2.setTimeoutSeconds(300);
        subWfV2.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        // Parent requests version 1 explicitly
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1); // explicitly request v1 even though v2 is latest
        subWfTask.setSubWorkflowParam(subParams);
        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(subWfV1, subWfV2, parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            // Wait for the sub-workflow to be running
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            // Complete the task in the sub-workflow
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow subWf = workflowClient.getWorkflow(subWorkflowId, true);
                                assertNotNull(subWf.getTasks());
                                assertNotNull(subWf.getTasks().get(0).getTaskId());
                            });

            String innerTaskId =
                    workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId();
            TaskResult result = new TaskResult();
            result.setWorkflowInstanceId(subWorkflowId);
            result.setTaskId(innerTaskId);
            result.setStatus(TaskResult.Status.COMPLETED);
            taskClient.updateTask(result);

            // The sub-workflow must be the v1 instance — its output sentinel is "v1"
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow subWf = workflowClient.getWorkflow(subWorkflowId, false);
                                assertEquals(Workflow.WorkflowStatus.COMPLETED, subWf.getStatus());
                                assertEquals(
                                        "v1",
                                        subWf.getOutput().get("version"),
                                        "Sub-workflow must execute version 1, not the latest (v2)");
                            });
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 2);
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that a normally-registered sub-workflow (no inline workflowDefinition) does NOT have
     * "_systemMetadata" injected into its workflow input. The dynamic marking must only appear for
     * inline definitions where workflowDefinition != null after resolution.
     *
     * <p>This is the boundary condition: the "else" branch of {@code if (workflowDefinition !=
     * null)}. If the guard were missing, every sub-workflow would be marked dynamic.
     */
    @Test
    public void testRegisteredSubWorkflowNotMarkedDynamic() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "nodyn_parent_" + suffix;
        String subWfName = "nodyn_sub_" + suffix;
        String taskName = "nodyn_task_" + suffix;

        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");
        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setName(taskName);
        simpleTask.setTaskReferenceName("t1");
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setVersion(1);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTasks(List.of(simpleTask));
        subWfDef.setTimeoutSeconds(300);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        // Parent uses a pre-registered sub-workflow (no workflowDefinition field)
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        // workflowDefinition intentionally NOT set — this is a registered lookup
        subWfTask.setSubWorkflowParam(subParams);
        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(subWfDef, parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            // Registered sub-workflow must NOT have _systemMetadata in its input
            Workflow subWf = workflowClient.getWorkflow(subWorkflowId, false);
            assertNull(
                    subWf.getInput().get("_systemMetadata"),
                    "Registered sub-workflows must not be marked dynamic — "
                            + "_systemMetadata must be absent when workflowDefinition is null");
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 1);
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that a STATIC inline sub-workflow definition (a concrete WorkflowDef object embedded
     * directly in subWorkflowParam.workflowDefinition, NOT a String expression) is also marked
     * dynamic. This covers the object-form path of the dynamic-marking code, which executes for ANY
     * non-null workflowDefinition regardless of whether it was resolved from an expression or
     * embedded at compile time.
     */
    @Test
    public void testStaticInlineSubWorkflowAlsoMarkedDynamic() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "static_dyn_parent_" + suffix;

        // Build the sub-workflow def as a Java object and embed it directly
        WorkflowTask inlineTask = new WorkflowTask();
        inlineTask.setName("check_mark");
        inlineTask.setTaskReferenceName("check_mark");
        inlineTask.setWorkflowTaskType(TaskType.INLINE);
        inlineTask.setInputParameters(Map.of("evaluatorType", "javascript", "expression", "true;"));

        WorkflowDef embeddedSubWfDef = new WorkflowDef();
        embeddedSubWfDef.setName("static_inline_sub");
        embeddedSubWfDef.setVersion(1);
        embeddedSubWfDef.setSchemaVersion(2);
        embeddedSubWfDef.setTasks(List.of(inlineTask));
        embeddedSubWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName("static_inline_sub");
        subParams.setVersion(1);
        subParams.setWorkflowDef(embeddedSubWfDef); // concrete object, not an expression
        subWfTask.setSubWorkflowParam(subParams);

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.updateWorkflowDefs(List.of(parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            // Wait until the sub-workflow has been created (INLINE task completes fast)
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            Workflow subWf = workflowClient.getWorkflow(subWorkflowId, false);
            Object systemMetadata = subWf.getInput().get("_systemMetadata");
            assertNotNull(
                    systemMetadata,
                    "Static inline sub-workflows must also be marked dynamic — "
                            + "_systemMetadata must be present whenever workflowDefinition != null");
            assertTrue(systemMetadata instanceof Map);
            assertEquals(
                    true,
                    ((Map<?, ?>) systemMetadata).get("dynamic"),
                    "dynamic flag must be true for static inline sub-workflow definitions");
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that version=0 in subWorkflowParams is treated as "use the latest version", and that
     * this sentinel value survives the Number.intValue() fix correctly.
     *
     * <p>Proof: SubWorkflow.start() does {@code resolvedVersion = version == 0 ? null : version}. A
     * null version passed to StartWorkflowInput causes WorkflowExecutor to fetch the latest version
     * from MetadataDAO. We register two versions; set subWorkflowParams.version=0; assert that
     * version 2 (latest) executes. If the sentinel were ignored, version 1 could run.
     */
    @Test
    public void testVersionZeroTreatedAsLatest() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "ver0_parent_" + suffix;
        String subWfName = "ver0_sub_" + suffix;
        String taskName = "ver0_task_" + suffix;

        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask t = new WorkflowTask();
        t.setName(taskName);
        t.setTaskReferenceName("t1");
        t.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subV1 = new WorkflowDef();
        subV1.setName(subWfName);
        subV1.setVersion(1);
        subV1.setOwnerEmail("test@conductor.io");
        subV1.setTasks(List.of(t));
        subV1.setOutputParameters(Map.of("version", "v1"));
        subV1.setTimeoutSeconds(300);
        subV1.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        WorkflowDef subV2 = new WorkflowDef();
        subV2.setName(subWfName);
        subV2.setVersion(2);
        subV2.setOwnerEmail("test@conductor.io");
        subV2.setTasks(List.of(t));
        subV2.setOutputParameters(Map.of("version", "v2"));
        subV2.setTimeoutSeconds(300);
        subV2.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(0); // sentinel: use latest
        subWfTask.setSubWorkflowParam(subParams);
        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(subV1, subV2, parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    assertNotNull(
                                            workflowClient
                                                    .getWorkflow(workflowId, true)
                                                    .getTasks()
                                                    .get(0)
                                                    .getSubWorkflowId()));

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            String innerTaskId =
                    workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId();
            TaskResult result = new TaskResult();
            result.setWorkflowInstanceId(subWorkflowId);
            result.setTaskId(innerTaskId);
            result.setStatus(TaskResult.Status.COMPLETED);
            taskClient.updateTask(result);

            // version=0 → resolvedVersion=null → WorkflowExecutor fetches latest (v2)
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow subWf = workflowClient.getWorkflow(subWorkflowId, false);
                                assertEquals(Workflow.WorkflowStatus.COMPLETED, subWf.getStatus());
                                assertEquals(
                                        "v2",
                                        subWf.getOutput().get("version"),
                                        "version=0 must resolve to the latest version (v2)");
                            });
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 2);
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that both idempotencyKey AND idempotencyStrategy are forwarded through the mapper
     * into task inputData and then read by SubWorkflow.start() into StartWorkflowInput without
     * errors. Also validates that the IdempotencyStrategy enum survives serialization (stored as
     * its enum name string, correctly deserialized via IdempotencyStrategy.valueOf).
     */
    @Test
    public void testIdempotencyKeyAndStrategyBothForwardedWithoutBreakingExecution() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentWfName = "idp_strat_parent_" + suffix;
        String subWfName = "idp_strat_sub_" + suffix;
        String taskName = "idp_strat_task_" + suffix;

        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");
        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setName(taskName);
        simpleTask.setTaskReferenceName("t1");
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setVersion(1);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTasks(List.of(simpleTask));
        subWfDef.setTimeoutSeconds(300);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("exec");
        subWfTask.setTaskReferenceName("exec");
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        subParams.setIdempotencyKey("e2e-idempotency-key-" + suffix);
        subParams.setIdempotencyStrategy(
                com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy.RETURN_EXISTING);
        subWfTask.setSubWorkflowParam(subParams);
        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setVersion(1);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTasks(List.of(subWfTask));
        parentWfDef.setTimeoutSeconds(300);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(subWfDef, parentWfDef));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentWfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        try {
            // Both key and strategy must not prevent sub-workflow creation —
            // idempotency enforcement is not implemented in OSS but the plumbing must not crash.
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(
                                        Task.Status.IN_PROGRESS, wf.getTasks().get(0).getStatus());
                                assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            });

            String subWorkflowId =
                    workflowClient
                            .getWorkflow(workflowId, true)
                            .getTasks()
                            .get(0)
                            .getSubWorkflowId();

            // The idempotencyKey must have been forwarded to the task's inputData by the mapper.
            // We verify via the task inputData visible in the workflow execution.
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertEquals(
                    "e2e-idempotency-key-" + suffix,
                    wf.getTasks().get(0).getInputData().get("idempotencyKey"),
                    "idempotencyKey must be forwarded through the mapper into task inputData");
            assertEquals(
                    "RETURN_EXISTING",
                    String.valueOf(wf.getTasks().get(0).getInputData().get("idempotencyStrategy")),
                    "idempotencyStrategy must be forwarded as its enum name string");

            // Complete the inner task to clean up
            String innerTaskId =
                    workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId();
            TaskResult result = new TaskResult();
            result.setWorkflowInstanceId(subWorkflowId);
            result.setTaskId(innerTaskId);
            result.setStatus(TaskResult.Status.COMPLETED);
            taskClient.updateTask(result);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    assertEquals(
                                            Workflow.WorkflowStatus.COMPLETED,
                                            workflowClient
                                                    .getWorkflow(workflowId, false)
                                                    .getStatus()));
        } finally {
            try {
                workflowClient.terminateWorkflow(workflowId, "e2e test cleanup");
            } catch (Exception ignored) {
            }
            try {
                metadataClient.unregisterWorkflowDef(parentWfName, 1);
                metadataClient.unregisterWorkflowDef(subWfName, 1);
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception ignored) {
            }
        }
    }

    private void registerInlineWorkflowDef(String workflowName, MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef("dt1");
        taskDef.setOwnerEmail("test@conductor.io");

        TaskDef taskDef2 = new TaskDef("dt2");
        taskDef2.setOwnerEmail("test@conductor.io");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("dt2");
        workflowTask.setName("dt2");
        workflowTask.setTaskDefinition(taskDef2);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName("dt1");
        inline.setName("dt1");
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask inlineSubworkflow = new WorkflowTask();
        inlineSubworkflow.setTaskReferenceName("dynamicFork");
        inlineSubworkflow.setName("dynamicFork");
        inlineSubworkflow.setTaskDefinition(taskDef);
        inlineSubworkflow.setWorkflowTaskType(TaskType.SUB_WORKFLOW);

        WorkflowDef inlineWorkflowDef = new WorkflowDef();
        inlineWorkflowDef.setName("inline_test_sub_workflow");
        inlineWorkflowDef.setVersion(1);
        inlineWorkflowDef.setTasks(Arrays.asList(inline));
        inlineWorkflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        inlineWorkflowDef.setTimeoutSeconds(600);
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("inline_test_sub_workflow");
        subWorkflowParams.setVersion(1);
        subWorkflowParams.setWorkflowDef(inlineWorkflowDef);
        inlineSubworkflow.setSubWorkflowParam(subWorkflowParams);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test inline sub_workflow definition");
        workflowDef.setTasks(Arrays.asList(workflowTask, inlineSubworkflow));
        try {
            metadataClient1.updateWorkflowDefs(java.util.List.of(workflowDef));
            metadataClient1.registerTaskDefs(Arrays.asList(taskDef, taskDef2));
        } catch (Exception e) {
        }
    }
}
