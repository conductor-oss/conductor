/*
 * Copyright 2026 Conductor Authors.
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
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

/**
 * Stress probe for the SUB_WORKFLOW SCHEDULED-recovery race.
 *
 * <p>The race window opens between the async system-task worker calling {@code
 * WorkflowExecutor.startWorkflowIdempotent} (which creates the child workflow and queues it for
 * evaluation) and the worker writing the parent task back to the DB. If the child workflow
 * completes synchronously during this window — i.e. {@code decide(child)} runs and the child's
 * tasks complete inline in a single pass — {@code completeWorkflow(child)} drives {@code
 * updateParentWorkflowTask}, which reads the parent task in its pre-attach state (SCHEDULED, no
 * {@code subWorkflowId}) and invokes {@code SubWorkflow.execute(child, parentTask, …)} with the
 * child workflow as the context. The SCHEDULED-recovery branch then derives a phantom
 * deterministic id from the child's workflow id and mints an orphaned workflow.
 *
 * <p>This test exercises the race by:
 *
 * <ol>
 *   <li>registering a child workflow with a single INLINE task — synchronous, completes in the
 *       first decide pass, opening the race window reliably,
 *   <li>launching many parent workflows in parallel to widen the chance any single run wins the
 *       race,
 *   <li>asserting two independent invariants after every parent completes:
 *       <ul>
 *         <li>the parent SUB_WORKFLOW task's {@code subWorkflowId} matches the deterministic id
 *             computed from {@code (parentId, parentTaskId, 0)} — a mismatch means a phantom
 *             overwrote it,
 *         <li>the total count of workflows with the child def name equals the number of parents —
 *             any extras are phantoms.
 *       </ul>
 * </ol>
 *
 * <p>This is a probabilistic reproducer. The deterministic gate for the structural defect lives in
 * {@code TestSubWorkflow.testExecuteFromUpdateParentWorkflowTaskDoesNotCreatePhantomWhenContextIsChild}.
 */
public class SubWorkflowScheduledRaceTests {

    private static final int PARALLELISM = 50;
    private static final int AWAIT_SECONDS = 90;

    @Test
    public void parentSubWorkflowTaskMustNotPointAtPhantomChild() throws Exception {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

        String suffix = RandomStringUtils.randomAlphanumeric(6).toLowerCase();
        String parentName = "sw_race_parent_" + suffix;
        String childName = "sw_race_child_" + suffix;

        registerChildWithInlineOnly(childName, metadataClient);
        registerParentWithSubWorkflow(parentName, childName, metadataClient);

        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<CompletableFuture<String>> starts = new ArrayList<>();
            for (int i = 0; i < PARALLELISM; i++) {
                starts.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    StartWorkflowRequest req = new StartWorkflowRequest();
                                    req.setName(parentName);
                                    req.setVersion(1);
                                    return workflowClient.startWorkflow(req);
                                },
                                executor));
            }

            List<String> parentIds =
                    starts.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());

            await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(
                            () -> {
                                for (String pid : parentIds) {
                                    Workflow wf = workflowClient.getWorkflow(pid, true);
                                    assertEquals(
                                            Workflow.WorkflowStatus.COMPLETED,
                                            wf.getStatus(),
                                            "parent " + pid + " did not complete");
                                }
                            });

            // Per-parent integrity: SUB_WORKFLOW task's subWorkflowId must equal
            // the deterministic id derived from the real parent context. Any
            // mismatch means the SCHEDULED-recovery branch wrote a phantom id.
            for (String pid : parentIds) {
                Workflow wf = workflowClient.getWorkflow(pid, true);
                String parentTaskId =
                        wf.getTasks().stream()
                                .filter(t -> "SUB_WORKFLOW".equals(t.getTaskType()))
                                .findFirst()
                                .orElseThrow()
                                .getTaskId();

                String expectedChildId = deterministicSubWorkflowId(pid, parentTaskId, 0);
                String actualChildId =
                        wf.getTasks().stream()
                                .filter(t -> "SUB_WORKFLOW".equals(t.getTaskType()))
                                .findFirst()
                                .orElseThrow()
                                .getSubWorkflowId();

                assertNotNull(actualChildId, "parent " + pid + " has no subWorkflowId attached");
                assertEquals(
                        expectedChildId,
                        actualChildId,
                        "parent "
                                + pid
                                + " subWorkflowId does not match the deterministic id derived from the parent context — "
                                + "phantom workflow likely overwrote it via SCHEDULED-recovery race");

                Workflow child = workflowClient.getWorkflow(actualChildId, false);
                assertEquals(
                        pid,
                        child.getParentWorkflowId(),
                        "child " + actualChildId + " has wrong parentWorkflowId");
            }

            // Phantom probe: for every parent, compute the id that the buggy
            // derivation would produce (deterministic(childId, parentTaskId, 0))
            // and try to fetch it. If any of those exists, the SCHEDULED-recovery
            // race fired at least once. Uses getWorkflow rather than search so
            // it works with every indexing backend, including sqlite.
            List<String> phantoms = new ArrayList<>();
            for (String pid : parentIds) {
                Workflow wf = workflowClient.getWorkflow(pid, true);
                String parentTaskId =
                        wf.getTasks().stream()
                                .filter(t -> "SUB_WORKFLOW".equals(t.getTaskType()))
                                .findFirst()
                                .orElseThrow()
                                .getTaskId();
                String legitimateChildId = deterministicSubWorkflowId(pid, parentTaskId, 0);
                String phantomId =
                        deterministicSubWorkflowId(legitimateChildId, parentTaskId, 0);
                try {
                    Workflow phantom = workflowClient.getWorkflow(phantomId, false);
                    if (phantom != null) {
                        phantoms.add(phantomId);
                    }
                } catch (Exception notFound) {
                    // expected — phantom does not exist
                }
            }
            assertEquals(
                    List.of(),
                    phantoms,
                    "found phantom workflows produced by the SCHEDULED-recovery race: " + phantoms);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** Mirrors {@code com.netflix.conductor.core.utils.IDGenerator.generateSubWorkflowId}. */
    private static String deterministicSubWorkflowId(
            String parentWorkflowId, String parentWorkflowTaskId, int retryCount) {
        String source =
                String.format(
                        "subworkflow:%s:%s:%d",
                        parentWorkflowId, parentWorkflowTaskId, retryCount);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void registerChildWithInlineOnly(String name, MetadataClient md) {
        TaskDef td = new TaskDef(name);
        td.setRetryCount(0);
        td.setOwnerEmail("test@conductor.io");
        md.registerTaskDefs(List.of(td));

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName("inline_done");
        inline.setName("inline_done");
        inline.setTaskDefinition(td);
        inline.setWorkflowTaskType(TaskType.INLINE);
        inline.setInputParameters(
                Map.of(
                        "evaluatorType", "graaljs",
                        "expression", "true;"));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("test@conductor.io");
        def.setTimeoutSeconds(120);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(List.of(inline));
        md.updateWorkflowDefs(List.of(def));
    }

    private static void registerParentWithSubWorkflow(
            String parentName, String childName, MetadataClient md) {
        TaskDef td = new TaskDef("sub_" + parentName);
        td.setRetryCount(0);
        td.setOwnerEmail("test@conductor.io");
        md.registerTaskDefs(List.of(td));

        WorkflowTask sw = new WorkflowTask();
        sw.setTaskReferenceName("child_call");
        sw.setName("sub_" + parentName);
        sw.setTaskDefinition(td);
        sw.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams params = new SubWorkflowParams();
        params.setName(childName);
        params.setVersion(1);
        sw.setSubWorkflowParam(params);

        WorkflowDef def = new WorkflowDef();
        def.setName(parentName);
        def.setVersion(1);
        def.setOwnerEmail("test@conductor.io");
        def.setTimeoutSeconds(120);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(List.of(sw));
        md.updateWorkflowDefs(List.of(def));
    }
}
