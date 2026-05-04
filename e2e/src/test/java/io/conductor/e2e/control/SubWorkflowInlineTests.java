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
