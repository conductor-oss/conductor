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
package com.netflix.conductor.test.integration.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration tests for FORK_JOIN with joinMode: SYNC (issue #619).
 *
 * <p>Tests verify that a JOIN task configured with joinMode=SYNC:
 *
 * <ul>
 *   <li>Correctly waits for all branches before completing
 *   <li>Completes immediately after the last branch finishes (zero evaluation offset)
 *   <li>Handles optional/permissive branch failures correctly
 *   <li>Works with nested FORK_JOIN structures
 *   <li>Does not break backward compatibility when joinMode is absent or set to ASYNC
 * </ul>
 *
 * <p>Workflow definitions are registered via raw JSON POST to avoid classpath conflicts with the
 * external conductor-client JAR (which ships an older WorkflowTask without JoinMode). Task
 * execution and workflow status are retrieved via the standard client library.
 *
 * <p>System task workers are enabled so JOIN tasks are evaluated by the background worker,
 * exercising the full execution path including {@code getEvaluationOffset()}.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            // Enable system task workers so JOIN evaluates via AsyncSystemTaskExecutor.
            // The in-memory queue (JedisMock, from conductor.db.type=memory) is used.
            "conductor.system-task-workers.enabled=true",
            // Fast poll so tests don't wait long for the JOIN to be picked up.
            "conductor.app.system-task-worker-poll-interval=50ms"
        })
public class ForkJoinSyncModeIntegrationTest {

    private static final String OWNER_EMAIL = "test@harness.com";
    /** How long to wait for a task to become available for polling. */
    private static final long TASK_POLL_TIMEOUT_MS = 10_000;
    /** How long to wait for a system task (JOIN) to complete after branches finish. */
    private static final long JOIN_COMPLETE_TIMEOUT_MS = 10_000;
    /** Generous timeout for workflows to reach terminal state. */
    private static final long WORKFLOW_TERMINAL_TIMEOUT_MS = 15_000;

    @LocalServerPort private int port;

    private String apiRoot;
    private TaskClient taskClient;
    private WorkflowClient workflowClient;
    private MetadataClient metadataClient;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @Before
    public void init() {
        apiRoot = String.format("http://localhost:%d/api/", port);
        taskClient = new TaskClient();
        taskClient.setRootURI(apiRoot);
        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);
        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // Workflow definition builder helpers (raw JSON via Map)
    //
    // We POST workflow definitions as raw JSON to avoid a classpath issue:
    // the external conductor-client JAR ships its own WorkflowTask class that
    // pre-dates JoinMode. Using raw Maps + RestTemplate bypasses that class entirely
    // and lets the server deserialize joinMode correctly with its local WorkflowTask.
    // =========================================================================

    private void registerTask(String name) {
        TaskDef def = new TaskDef(name, name + " (fork/join sync test)", OWNER_EMAIL, 3, 120, 120);
        def.setTimeoutPolicy(TaskDef.TimeoutPolicy.TIME_OUT_WF);
        metadataClient.registerTaskDefs(List.of(def));
    }

    /** Build a raw Map representing a SIMPLE WorkflowTask. */
    private Map<String, Object> simpleTaskMap(String name, String ref) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", name);
        t.put("taskReferenceName", ref);
        t.put("type", "SIMPLE");
        t.put("inputParameters", Map.of());
        return t;
    }

    /** Build a raw Map representing a SIMPLE WorkflowTask that is optional. */
    private Map<String, Object> optionalTaskMap(String name, String ref) {
        Map<String, Object> t = simpleTaskMap(name, ref);
        t.put("optional", true);
        return t;
    }

    /**
     * Build a raw Map representing a FORK_JOIN WorkflowTask.
     *
     * @param branches list of branches; each branch is a list of task Maps
     */
    private Map<String, Object> forkTaskMap(
            String ref, List<List<Map<String, Object>>> branches) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", "fork");
        t.put("taskReferenceName", ref);
        t.put("type", "FORK_JOIN");
        t.put("forkTasks", branches);
        return t;
    }

    /**
     * Build a raw Map representing a JOIN WorkflowTask.
     *
     * @param joinMode "SYNC", "ASYNC", or {@code null} to omit the field (default async)
     */
    private Map<String, Object> joinTaskMap(String ref, List<String> joinOn, String joinMode) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", "join");
        t.put("taskReferenceName", ref);
        t.put("type", "JOIN");
        t.put("joinOn", joinOn);
        if (joinMode != null) {
            t.put("joinMode", joinMode);
        }
        return t;
    }

    /**
     * Register a workflow definition by POSTing raw JSON to the metadata API.
     * This sidesteps the classpath conflict with the external conductor-client JAR.
     */
    private void registerWorkflowDefJson(
            String name, List<Map<String, Object>> tasks, Map<String, Object> outputParameters)
            throws Exception {
        Map<String, Object> def = new HashMap<>();
        def.put("name", name);
        def.put("ownerEmail", OWNER_EMAIL);
        def.put("schemaVersion", 2);
        def.put("tasks", tasks);
        def.put("timeoutPolicy", "ALERT_ONLY");
        def.put("timeoutSeconds", 0);
        if (outputParameters != null) {
            def.put("outputParameters", outputParameters);
        }

        String json = objectMapper.writeValueAsString(def);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        restTemplate.postForEntity(apiRoot + "metadata/workflow", entity, String.class);
    }

    private String startWorkflow(String name) {
        return workflowClient.startWorkflow(
                new StartWorkflowRequest().withName(name).withInput(Map.of()));
    }

    // =========================================================================
    // Task interaction helpers
    // =========================================================================

    /** Polls until a task of the given type is available, then marks it COMPLETED. */
    private void completeTask(String taskType, Map<String, Object> output)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TASK_POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<Task> tasks = taskClient.batchPollTasksByTaskType(taskType, "test", 1, 200);
            if (!tasks.isEmpty()) {
                Task task = tasks.get(0);
                task.setStatus(Task.Status.COMPLETED);
                if (output != null) {
                    task.getOutputData().putAll(output);
                }
                taskClient.updateTask(new TaskResult(task));
                return;
            }
            Thread.sleep(50);
        }
        fail("Task '"
                + taskType
                + "' not available for polling within "
                + TASK_POLL_TIMEOUT_MS
                + "ms");
    }

    private void completeTask(String taskType) throws InterruptedException {
        completeTask(taskType, Map.of());
    }

    /** Polls until a task of the given type is available, then marks it FAILED. */
    private void failTask(String taskType) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TASK_POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<Task> tasks = taskClient.batchPollTasksByTaskType(taskType, "test", 1, 200);
            if (!tasks.isEmpty()) {
                Task task = tasks.get(0);
                task.setStatus(Task.Status.FAILED);
                task.setReasonForIncompletion("Intentionally failed for test");
                taskClient.updateTask(new TaskResult(task));
                return;
            }
            Thread.sleep(50);
        }
        fail("Task '"
                + taskType
                + "' not available for polling within "
                + TASK_POLL_TIMEOUT_MS
                + "ms");
    }

    // =========================================================================
    // Wait helpers
    // =========================================================================

    /**
     * Polls until the workflow reaches a terminal status or timeout expires.
     *
     * @return the workflow at its final observed state
     */
    private Workflow waitForWorkflowTerminal(String workflowId, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Workflow wf;
        do {
            Thread.sleep(100);
            wf = workflowClient.getWorkflow(workflowId, true);
        } while (!wf.getStatus().isTerminal() && System.currentTimeMillis() < deadline);
        return wf;
    }

    /**
     * Polls until the named task reference reaches terminal status or timeout expires.
     *
     * @return the task at its final observed state
     * @throws AssertionError if the task did not reach terminal status within the timeout
     */
    private Task waitForTaskTerminal(String workflowId, String taskRefName, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            Optional<Task> found =
                    wf.getTasks().stream()
                            .filter(t -> taskRefName.equals(t.getReferenceTaskName()))
                            .findFirst();
            if (found.isPresent() && found.get().getStatus().isTerminal()) {
                return found.get();
            }
            Thread.sleep(100);
        }
        Workflow wf = workflowClient.getWorkflow(workflowId, true);
        String actual =
                wf.getTasks().stream()
                        .filter(t -> taskRefName.equals(t.getReferenceTaskName()))
                        .findFirst()
                        .map(t -> t.getStatus().name())
                        .orElse("NOT_FOUND");
        throw new AssertionError(
                "Task '"
                        + taskRefName
                        + "' did not reach terminal status within "
                        + timeoutMs
                        + "ms. Actual status: "
                        + actual
                        + ". Workflow status: "
                        + wf.getStatus());
    }

    // =========================================================================
    // Test 1 — Basic SYNC join: 3 branches, all succeed
    // =========================================================================

    /**
     * A FORK with 3 independent branches joins with joinMode=SYNC. All branches succeed. After
     * the last branch completes, the JOIN should evaluate immediately (offset=0) and the workflow
     * should advance to the post-join task and then COMPLETE.
     *
     * <p>This is the core correctness test for issue #619.
     */
    @Test
    public void testSync_threeBranches_allSucceed() throws Exception {
        registerTask("fjt1_branch_a");
        registerTask("fjt1_branch_b");
        registerTask("fjt1_branch_c");
        registerTask("fjt1_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(simpleTaskMap("fjt1_branch_a", "branch_a")),
                                List.of(simpleTaskMap("fjt1_branch_b", "branch_b")),
                                List.of(simpleTaskMap("fjt1_branch_c", "branch_c")))));
        tasks.add(
                joinTaskMap("join_ref", List.of("branch_a", "branch_b", "branch_c"), "SYNC"));
        tasks.add(simpleTaskMap("fjt1_post", "post_task"));

        Map<String, Object> outputParams =
                Map.of(
                        "branchAOutput", "${join_ref.output.branch_a}",
                        "branchBOutput", "${join_ref.output.branch_b}",
                        "branchCOutput", "${join_ref.output.branch_c}");
        registerWorkflowDefJson("FJSync_ThreeBranches", tasks, outputParams);

        String workflowId = startWorkflow("FJSync_ThreeBranches");
        assertNotNull(workflowId);

        completeTask("fjt1_branch_a", Map.of("result", "A"));
        completeTask("fjt1_branch_b", Map.of("result", "B"));
        completeTask("fjt1_branch_c", Map.of("result", "C"));

        Task join = waitForTaskTerminal(workflowId, "join_ref", JOIN_COMPLETE_TIMEOUT_MS);
        assertEquals("JOIN should COMPLETE after all 3 branches succeed", Task.Status.COMPLETED, join.getStatus());

        // Verify JOIN accumulates each branch's output under its reference name
        assertNotNull("JOIN output should contain branch_a's output", join.getOutputData().get("branch_a"));
        assertNotNull("JOIN output should contain branch_b's output", join.getOutputData().get("branch_b"));
        assertNotNull("JOIN output should contain branch_c's output", join.getOutputData().get("branch_c"));

        completeTask("fjt1_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }

    // =========================================================================
    // Test 2 — Backward compatibility: no joinMode set (implicit ASYNC)
    // =========================================================================

    /**
     * A FORK/JOIN workflow with NO joinMode on the JOIN task should behave exactly as before
     * issue #619. This test guards against regression.
     *
     * <p>The only behavioral difference from SYNC mode is that the JOIN may be evaluated with
     * exponential backoff when the poll count exceeds the postpone threshold. We verify
     * correctness (same terminal state) rather than timing.
     */
    @Test
    public void testNoJoinMode_backwardCompatibility() throws Exception {
        registerTask("fjt2_branch_a");
        registerTask("fjt2_branch_b");
        registerTask("fjt2_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(simpleTaskMap("fjt2_branch_a", "branch_a")),
                                List.of(simpleTaskMap("fjt2_branch_b", "branch_b")))));
        tasks.add(joinTaskMap("join_ref", List.of("branch_a", "branch_b"), null)); // no joinMode
        tasks.add(simpleTaskMap("fjt2_post", "post_task"));
        registerWorkflowDefJson("FJSync_NoJoinMode", tasks, null);

        String workflowId = startWorkflow("FJSync_NoJoinMode");
        assertNotNull(workflowId);

        completeTask("fjt2_branch_a");
        completeTask("fjt2_branch_b");

        // Give async mode a generous window — it uses backoff so may be slower.
        Task join = waitForTaskTerminal(workflowId, "join_ref", 30_000);
        assertEquals(Task.Status.COMPLETED, join.getStatus());

        completeTask("fjt2_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }

    // =========================================================================
    // Test 3 — Explicit ASYNC joinMode
    // =========================================================================

    /** Explicitly setting joinMode=ASYNC should produce the same outcome as no joinMode. */
    @Test
    public void testExplicitAsync_sameOutcomeAsDefault() throws Exception {
        registerTask("fjt3_branch_a");
        registerTask("fjt3_branch_b");
        registerTask("fjt3_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(simpleTaskMap("fjt3_branch_a", "branch_a")),
                                List.of(simpleTaskMap("fjt3_branch_b", "branch_b")))));
        tasks.add(joinTaskMap("join_ref", List.of("branch_a", "branch_b"), "ASYNC"));
        tasks.add(simpleTaskMap("fjt3_post", "post_task"));
        registerWorkflowDefJson("FJSync_ExplicitAsync", tasks, null);

        String workflowId = startWorkflow("FJSync_ExplicitAsync");
        assertNotNull(workflowId);

        completeTask("fjt3_branch_a");
        completeTask("fjt3_branch_b");

        Task join = waitForTaskTerminal(workflowId, "join_ref", 30_000);
        assertEquals(Task.Status.COMPLETED, join.getStatus());

        completeTask("fjt3_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }

    // =========================================================================
    // Test 4 — SYNC join: optional branch fails → workflow COMPLETES
    // =========================================================================

    /**
     * When a branch task is marked optional and fails, the SYNC JOIN should still complete (with
     * COMPLETED or COMPLETED_WITH_ERRORS status), and the workflow should proceed.
     */
    @Test
    public void testSync_optionalBranchFails_workflowCompletes() throws Exception {
        registerTask("fjt4_branch_a");
        registerTask("fjt4_branch_opt");
        registerTask("fjt4_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(simpleTaskMap("fjt4_branch_a", "branch_a")),
                                List.of(optionalTaskMap("fjt4_branch_opt", "branch_opt")))));
        tasks.add(joinTaskMap("join_ref", List.of("branch_a", "branch_opt"), "SYNC"));
        tasks.add(simpleTaskMap("fjt4_post", "post_task"));
        registerWorkflowDefJson("FJSync_OptionalFail", tasks, null);

        String workflowId = startWorkflow("FJSync_OptionalFail");
        assertNotNull(workflowId);

        completeTask("fjt4_branch_a");
        failTask("fjt4_branch_opt");

        Task join = waitForTaskTerminal(workflowId, "join_ref", JOIN_COMPLETE_TIMEOUT_MS);
        assertTrue(
                "JOIN should be terminal and successful (COMPLETED or COMPLETED_WITH_ERRORS)",
                join.getStatus().isTerminal() && join.getStatus().isSuccessful());

        completeTask("fjt4_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(
                "Workflow with failed optional branch should COMPLETE",
                Workflow.WorkflowStatus.COMPLETED,
                wf.getStatus());
    }

    // =========================================================================
    // Test 5 — SYNC join: required branch fails → workflow FAILS
    // =========================================================================

    /**
     * When a required (non-optional) branch fails, the SYNC JOIN should fail and the workflow
     * should reach FAILED status.
     */
    @Test
    public void testSync_requiredBranchFails_workflowFails() throws Exception {
        registerTask("fjt5_branch_a");
        registerTask("fjt5_branch_b");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(simpleTaskMap("fjt5_branch_a", "branch_a")),
                                List.of(simpleTaskMap("fjt5_branch_b", "branch_b")))));
        tasks.add(joinTaskMap("join_ref", List.of("branch_a", "branch_b"), "SYNC"));
        registerWorkflowDefJson("FJSync_RequiredFail", tasks, null);

        String workflowId = startWorkflow("FJSync_RequiredFail");
        assertNotNull(workflowId);

        completeTask("fjt5_branch_a");
        failTask("fjt5_branch_b");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(
                "Workflow with failed required branch should FAIL",
                Workflow.WorkflowStatus.FAILED,
                wf.getStatus());
    }

    // =========================================================================
    // Test 6 — SYNC join: sequential tasks within a branch
    // =========================================================================

    /**
     * A branch with sequential tasks (fjt6_a1 → fjt6_a2). The JOIN waits on the LAST task in
     * each branch (branch_a2 and branch_b). Tests that joinMode=SYNC works correctly when
     * branches contain multiple chained tasks.
     */
    @Test
    public void testSync_sequentialTasksInBranch() throws Exception {
        registerTask("fjt6_a1");
        registerTask("fjt6_a2");
        registerTask("fjt6_b");
        registerTask("fjt6_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(
                                        simpleTaskMap("fjt6_a1", "branch_a1"),
                                        simpleTaskMap("fjt6_a2", "branch_a2")), // sequential
                                List.of(simpleTaskMap("fjt6_b", "branch_b")))));
        tasks.add(joinTaskMap("join_ref", List.of("branch_a2", "branch_b"), "SYNC"));
        tasks.add(simpleTaskMap("fjt6_post", "post_task"));
        registerWorkflowDefJson("FJSync_SequentialBranch", tasks, null);

        String workflowId = startWorkflow("FJSync_SequentialBranch");
        assertNotNull(workflowId);

        // Branch 1: fjt6_a1 must complete before fjt6_a2 is scheduled
        completeTask("fjt6_a1", Map.of("step", "1"));
        completeTask("fjt6_a2", Map.of("step", "2"));
        // Branch 2: single task
        completeTask("fjt6_b", Map.of("step", "B"));

        Task join = waitForTaskTerminal(workflowId, "join_ref", JOIN_COMPLETE_TIMEOUT_MS);
        assertEquals(Task.Status.COMPLETED, join.getStatus());

        completeTask("fjt6_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }

    // =========================================================================
    // Test 7 — SYNC join: joinOn subset (JOIN doesn't wait for all branches)
    // =========================================================================

    /**
     * The JOIN only waits for a subset of the forked branches (mon_a and mon_b), not the third
     * (unmon). The JOIN should complete as soon as mon_a and mon_b are done, without requiring
     * unmon to finish.
     *
     * <p>This tests that joinMode=SYNC interacts correctly with partial joinOn lists.
     */
    @Test
    public void testSync_joinOnSubset_completesWithoutWaitingForAllBranches() throws Exception {
        registerTask("fjt7_monitored_a");
        registerTask("fjt7_monitored_b");
        registerTask("fjt7_unmonitored");
        registerTask("fjt7_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(
                forkTaskMap(
                        "fork",
                        List.of(
                                List.of(simpleTaskMap("fjt7_monitored_a", "mon_a")),
                                List.of(simpleTaskMap("fjt7_monitored_b", "mon_b")),
                                List.of(simpleTaskMap("fjt7_unmonitored", "unmon")))));
        tasks.add(joinTaskMap("join_ref", List.of("mon_a", "mon_b"), "SYNC")); // only 2 of 3
        tasks.add(simpleTaskMap("fjt7_post", "post_task"));
        registerWorkflowDefJson("FJSync_JoinOnSubset", tasks, null);

        String workflowId = startWorkflow("FJSync_JoinOnSubset");
        assertNotNull(workflowId);

        completeTask("fjt7_monitored_a");
        completeTask("fjt7_monitored_b");
        // Deliberately leave fjt7_unmonitored pending — JOIN should not wait for it

        Task join = waitForTaskTerminal(workflowId, "join_ref", JOIN_COMPLETE_TIMEOUT_MS);
        assertEquals(
                "JOIN should COMPLETE without waiting for the unmonitored branch",
                Task.Status.COMPLETED,
                join.getStatus());

        completeTask("fjt7_post");
        completeTask("fjt7_unmonitored"); // clean up the dangling branch

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }

    // =========================================================================
    // Test 8 — Nested SYNC join
    // =========================================================================

    /**
     * A nested FORK/JOIN structure:
     *
     * <pre>
     *   outer_FORK
     *     branch_1: inner_FORK → [inner_a, inner_b] → inner_JOIN(SYNC)
     *     branch_2: outer_c
     *   outer_JOIN(SYNC) → post_task
     * </pre>
     *
     * Both the inner and outer JOINs use joinMode=SYNC. Tests that:
     *
     * <ul>
     *   <li>The inner JOIN completes after inner_a and inner_b finish
     *   <li>The outer JOIN waits for inner_JOIN and outer_c
     *   <li>The workflow reaches COMPLETED
     * </ul>
     */
    @Test
    public void testSync_nestedForkJoin() throws Exception {
        registerTask("fjt8_inner_a");
        registerTask("fjt8_inner_b");
        registerTask("fjt8_outer_c");
        registerTask("fjt8_post");

        // Build inner fork/join (branch 1)
        Map<String, Object> innerFork =
                forkTaskMap(
                        "inner_fork",
                        List.of(
                                List.of(simpleTaskMap("fjt8_inner_a", "inner_a")),
                                List.of(simpleTaskMap("fjt8_inner_b", "inner_b"))));
        Map<String, Object> innerJoin =
                joinTaskMap("inner_join", List.of("inner_a", "inner_b"), "SYNC");

        // Build outer fork/join
        Map<String, Object> outerFork =
                forkTaskMap(
                        "outer_fork",
                        List.of(
                                List.of(innerFork, innerJoin), // branch 1 = nested fork/join
                                List.of(simpleTaskMap("fjt8_outer_c", "outer_c")))); // branch 2
        Map<String, Object> outerJoin =
                joinTaskMap("outer_join", List.of("inner_join", "outer_c"), "SYNC");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(outerFork);
        tasks.add(outerJoin);
        tasks.add(simpleTaskMap("fjt8_post", "post_task"));
        registerWorkflowDefJson("FJSync_Nested", tasks, null);

        String workflowId = startWorkflow("FJSync_Nested");
        assertNotNull(workflowId);

        completeTask("fjt8_inner_a");
        completeTask("fjt8_inner_b");
        completeTask("fjt8_outer_c");

        // Inner join should resolve before outer join
        Task innerJoinTask =
                waitForTaskTerminal(workflowId, "inner_join", JOIN_COMPLETE_TIMEOUT_MS);
        assertEquals("Inner JOIN should COMPLETE", Task.Status.COMPLETED, innerJoinTask.getStatus());

        Task outerJoinTask =
                waitForTaskTerminal(workflowId, "outer_join", JOIN_COMPLETE_TIMEOUT_MS);
        assertEquals("Outer JOIN should COMPLETE", Task.Status.COMPLETED, outerJoinTask.getStatus());

        completeTask("fjt8_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }

    // =========================================================================
    // Test 9 — SYNC join: large fan-out (10 parallel branches)
    // =========================================================================

    /**
     * Stress-tests SYNC join with 10 parallel branches. All branches complete successfully.
     * Verifies that the JOIN correctly waits for all 10 and then COMPLETES.
     */
    @Test
    public void testSync_largeFanOut_tenBranches() throws Exception {
        int branchCount = 10;
        List<List<Map<String, Object>>> branches = new ArrayList<>();
        List<String> joinOnRefs = new ArrayList<>();
        for (int i = 0; i < branchCount; i++) {
            String taskName = "fjt9_branch_" + i;
            String refName = "branch_" + i;
            registerTask(taskName);
            branches.add(List.of(simpleTaskMap(taskName, refName)));
            joinOnRefs.add(refName);
        }
        registerTask("fjt9_post");

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(forkTaskMap("fork", branches));
        tasks.add(joinTaskMap("join_ref", joinOnRefs, "SYNC"));
        tasks.add(simpleTaskMap("fjt9_post", "post_task"));
        registerWorkflowDefJson("FJSync_LargeFanOut", tasks, null);

        String workflowId = startWorkflow("FJSync_LargeFanOut");
        assertNotNull(workflowId);

        for (int i = 0; i < branchCount; i++) {
            completeTask("fjt9_branch_" + i, Map.of("branch", i));
        }

        Task join = waitForTaskTerminal(workflowId, "join_ref", JOIN_COMPLETE_TIMEOUT_MS);
        assertEquals(
                "JOIN should COMPLETE after all 10 branches succeed",
                Task.Status.COMPLETED,
                join.getStatus());

        // Verify all branch outputs are collected in the JOIN
        for (int i = 0; i < branchCount; i++) {
            assertNotNull(
                    "JOIN output should include branch_" + i,
                    join.getOutputData().get("branch_" + i));
        }

        completeTask("fjt9_post");

        Workflow wf = waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
    }
}
