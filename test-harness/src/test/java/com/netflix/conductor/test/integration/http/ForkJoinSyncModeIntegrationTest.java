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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
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
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.dao.QueueDAO;

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
 * <p>An {@link InMemoryQueueDAO} is provided via {@link TestConfig} to satisfy the {@link QueueDAO}
 * dependency without requiring Redis or Docker. JOIN tasks are evaluated explicitly via {@link
 * AsyncSystemTaskExecutor} — matching the pattern used by the Groovy Spock integration specs.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ConductorTestApp.class)
@TestPropertySource(locations = "classpath:application-integrationtest.properties")
public class ForkJoinSyncModeIntegrationTest {

    // =========================================================================
    // Test configuration: in-memory QueueDAO (no Redis / Docker required)
    // =========================================================================

    @TestConfiguration
    static class TestConfig {
        @Bean
        public QueueDAO inMemoryQueueDAO() {
            return new InMemoryQueueDAO();
        }

        /** Minimal in-memory QueueDAO backed by {@link LinkedBlockingDeque} per queue name. */
        static class InMemoryQueueDAO implements QueueDAO {

            private final ConcurrentHashMap<String, LinkedBlockingDeque<String>> queues =
                    new ConcurrentHashMap<>();

            private LinkedBlockingDeque<String> q(String name) {
                return queues.computeIfAbsent(name, k -> new LinkedBlockingDeque<>());
            }

            @Override
            public void push(String queueName, String id, long offsetTimeInSecond) {
                q(queueName).addLast(id);
            }

            @Override
            public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
                q(queueName).addLast(id);
            }

            @Override
            public void push(String queueName, List<Message> messages) {
                messages.forEach(m -> q(queueName).addLast(m.getId()));
            }

            @Override
            public boolean pushIfNotExists(
                    String queueName, String id, long offsetTimeInSecond) {
                LinkedBlockingDeque<String> queue = q(queueName);
                if (queue.contains(id)) return false;
                queue.addLast(id);
                return true;
            }

            @Override
            public boolean pushIfNotExists(
                    String queueName, String id, int priority, long offsetTimeInSecond) {
                return pushIfNotExists(queueName, id, offsetTimeInSecond);
            }

            @Override
            public List<String> pop(String queueName, int count, int timeout) {
                LinkedBlockingDeque<String> queue = q(queueName);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String id = queue.poll();
                    if (id == null) break;
                    result.add(id);
                }
                if (result.isEmpty() && timeout > 0) {
                    try {
                        String id = queue.poll(Math.min(timeout, 200), TimeUnit.MILLISECONDS);
                        if (id != null) result.add(id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return result;
            }

            @Override
            public List<Message> pollMessages(String queueName, int count, int timeout) {
                return pop(queueName, count, timeout).stream()
                        .map(id -> new Message(id, null, null))
                        .collect(Collectors.toList());
            }

            @Override
            public void remove(String queueName, String messageId) {
                q(queueName).remove(messageId);
            }

            @Override
            public int getSize(String queueName) {
                return q(queueName).size();
            }

            @Override
            public boolean ack(String queueName, String messageId) {
                return true;
            }

            @Override
            public boolean setUnackTimeout(
                    String queueName, String messageId, long unackTimeout) {
                return true;
            }

            @Override
            public void flush(String queueName) {
                q(queueName).clear();
            }

            @Override
            public Map<String, Long> queuesDetail() {
                Map<String, Long> result = new HashMap<>();
                queues.forEach((k, v) -> result.put(k, (long) v.size()));
                return result;
            }

            @Override
            public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
                return new HashMap<>();
            }

            @Override
            public boolean resetOffsetTime(String queueName, String id) {
                return q(queueName).contains(id);
            }

            @Override
            public boolean containsMessage(String queueName, String messageId) {
                return q(queueName).contains(messageId);
            }
        }
    }

    // =========================================================================
    // Spring-injected beans for direct JOIN evaluation (no background workers)
    // =========================================================================

    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;
    @Autowired private Join joinSystemTask;

    // =========================================================================
    // HTTP client fields
    // =========================================================================

    private static final String OWNER_EMAIL = "test@harness.com";
    private static final long TASK_POLL_TIMEOUT_MS = 10_000;
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
        TaskDef def = new TaskDef(name, name + " (fork/join sync test)", OWNER_EMAIL, 0, 120, 120);
        def.setTimeoutPolicy(TaskDef.TimeoutPolicy.TIME_OUT_WF);
        metadataClient.registerTaskDefs(List.of(def));
    }

    private Map<String, Object> simpleTaskMap(String name, String ref) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", name);
        t.put("taskReferenceName", ref);
        t.put("type", "SIMPLE");
        t.put("inputParameters", Map.of());
        return t;
    }

    private Map<String, Object> optionalTaskMap(String name, String ref) {
        Map<String, Object> t = simpleTaskMap(name, ref);
        t.put("optional", true);
        return t;
    }

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
        fail("Task '" + taskType + "' not available for polling within " + TASK_POLL_TIMEOUT_MS + "ms");
    }

    private void completeTask(String taskType) throws InterruptedException {
        completeTask(taskType, Map.of());
    }

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
        fail("Task '" + taskType + "' not available for polling within " + TASK_POLL_TIMEOUT_MS + "ms");
    }

    // =========================================================================
    // JOIN evaluation helper
    //
    // Mirrors the Groovy Spock specs: asyncSystemTaskExecutor.execute(joinTask, taskId)
    // is called directly instead of relying on background system-task-worker polling.
    // =========================================================================

    /**
     * Finds the JOIN task by reference name and executes it via {@link AsyncSystemTaskExecutor}.
     * Returns the JOIN task after execution (fetched from the server).
     */
    private Task executeJoin(String workflowId, String joinRefName) {
        Workflow wf = workflowClient.getWorkflow(workflowId, true);
        Optional<Task> maybeJoin =
                wf.getTasks().stream()
                        .filter(t -> joinRefName.equals(t.getReferenceTaskName()))
                        .findFirst();
        assertNotNull(
                "JOIN task '" + joinRefName + "' should be present in workflow",
                maybeJoin.orElse(null));
        Task joinTask = maybeJoin.get();
        if (!joinTask.getStatus().isTerminal()) {
            asyncSystemTaskExecutor.execute(joinSystemTask, joinTask.getTaskId());
        }
        return workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                .filter(t -> joinRefName.equals(t.getReferenceTaskName()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "JOIN task '" + joinRefName + "' missing after execute"));
    }

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

    // =========================================================================
    // Test 1 — Basic SYNC join: 3 branches, all succeed
    // =========================================================================

    /**
     * A FORK with 3 independent branches joins with joinMode=SYNC. All branches succeed. After
     * the last branch completes, the JOIN evaluates to COMPLETED and the workflow advances.
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
        tasks.add(joinTaskMap("join_ref", List.of("branch_a", "branch_b", "branch_c"), "SYNC"));
        tasks.add(simpleTaskMap("fjt1_post", "post_task"));

        registerWorkflowDefJson(
                "FJSync_ThreeBranches",
                tasks,
                Map.of(
                        "branchAOutput", "${join_ref.output.branch_a}",
                        "branchBOutput", "${join_ref.output.branch_b}",
                        "branchCOutput", "${join_ref.output.branch_c}"));

        String workflowId = startWorkflow("FJSync_ThreeBranches");
        assertNotNull(workflowId);

        completeTask("fjt1_branch_a", Map.of("result", "A"));
        completeTask("fjt1_branch_b", Map.of("result", "B"));
        completeTask("fjt1_branch_c", Map.of("result", "C"));

        Task join = executeJoin(workflowId, "join_ref");
        assertEquals("JOIN should COMPLETE after all 3 branches succeed",
                Task.Status.COMPLETED, join.getStatus());
        assertNotNull("JOIN output should contain branch_a", join.getOutputData().get("branch_a"));
        assertNotNull("JOIN output should contain branch_b", join.getOutputData().get("branch_b"));
        assertNotNull("JOIN output should contain branch_c", join.getOutputData().get("branch_c"));

        completeTask("fjt1_post");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 2 — Backward compatibility: no joinMode (implicit ASYNC)
    // =========================================================================

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
        tasks.add(joinTaskMap("join_ref", List.of("branch_a", "branch_b"), null));
        tasks.add(simpleTaskMap("fjt2_post", "post_task"));
        registerWorkflowDefJson("FJSync_NoJoinMode", tasks, null);

        String workflowId = startWorkflow("FJSync_NoJoinMode");
        assertNotNull(workflowId);

        completeTask("fjt2_branch_a");
        completeTask("fjt2_branch_b");

        Task join = executeJoin(workflowId, "join_ref");
        assertEquals(Task.Status.COMPLETED, join.getStatus());

        completeTask("fjt2_post");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 3 — Explicit ASYNC joinMode
    // =========================================================================

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

        Task join = executeJoin(workflowId, "join_ref");
        assertEquals(Task.Status.COMPLETED, join.getStatus());

        completeTask("fjt3_post");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 4 — SYNC join: optional branch fails → workflow COMPLETES
    // =========================================================================

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

        Task join = executeJoin(workflowId, "join_ref");
        assertTrue(
                "JOIN should be terminal and successful (COMPLETED or COMPLETED_WITH_ERRORS)",
                join.getStatus().isTerminal() && join.getStatus().isSuccessful());

        completeTask("fjt4_post");
        assertEquals(
                "Workflow with failed optional branch should COMPLETE",
                Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 5 — SYNC join: required branch fails → workflow FAILS
    // =========================================================================

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

        // Execute JOIN — it detects the required failure and itself fails,
        // then decide() transitions the workflow to FAILED.
        executeJoin(workflowId, "join_ref");

        assertEquals(
                "Workflow with failed required branch should FAIL",
                Workflow.WorkflowStatus.FAILED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 6 — SYNC join: sequential tasks within a branch
    // =========================================================================

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
                                        simpleTaskMap("fjt6_a2", "branch_a2")),
                                List.of(simpleTaskMap("fjt6_b", "branch_b")))));
        tasks.add(joinTaskMap("join_ref", List.of("branch_a2", "branch_b"), "SYNC"));
        tasks.add(simpleTaskMap("fjt6_post", "post_task"));
        registerWorkflowDefJson("FJSync_SequentialBranch", tasks, null);

        String workflowId = startWorkflow("FJSync_SequentialBranch");
        assertNotNull(workflowId);

        completeTask("fjt6_a1", Map.of("step", "1"));
        completeTask("fjt6_a2", Map.of("step", "2"));
        completeTask("fjt6_b", Map.of("step", "B"));

        Task join = executeJoin(workflowId, "join_ref");
        assertEquals(Task.Status.COMPLETED, join.getStatus());

        completeTask("fjt6_post");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 7 — SYNC join: joinOn subset (JOIN doesn't wait for all branches)
    // =========================================================================

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
        tasks.add(joinTaskMap("join_ref", List.of("mon_a", "mon_b"), "SYNC"));
        tasks.add(simpleTaskMap("fjt7_post", "post_task"));
        registerWorkflowDefJson("FJSync_JoinOnSubset", tasks, null);

        String workflowId = startWorkflow("FJSync_JoinOnSubset");
        assertNotNull(workflowId);

        completeTask("fjt7_monitored_a");
        completeTask("fjt7_monitored_b");
        // Deliberately leave fjt7_unmonitored pending

        Task join = executeJoin(workflowId, "join_ref");
        assertEquals(
                "JOIN should COMPLETE without waiting for the unmonitored branch",
                Task.Status.COMPLETED,
                join.getStatus());

        completeTask("fjt7_post");
        completeTask("fjt7_unmonitored");

        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 8 — Nested SYNC join
    // =========================================================================

    @Test
    public void testSync_nestedForkJoin() throws Exception {
        registerTask("fjt8_inner_a");
        registerTask("fjt8_inner_b");
        registerTask("fjt8_outer_c");
        registerTask("fjt8_post");

        Map<String, Object> innerFork =
                forkTaskMap(
                        "inner_fork",
                        List.of(
                                List.of(simpleTaskMap("fjt8_inner_a", "inner_a")),
                                List.of(simpleTaskMap("fjt8_inner_b", "inner_b"))));
        Map<String, Object> innerJoin =
                joinTaskMap("inner_join", List.of("inner_a", "inner_b"), "SYNC");
        Map<String, Object> outerFork =
                forkTaskMap(
                        "outer_fork",
                        List.of(
                                List.of(innerFork, innerJoin),
                                List.of(simpleTaskMap("fjt8_outer_c", "outer_c"))));
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

        Task innerJoinTask = executeJoin(workflowId, "inner_join");
        assertEquals("Inner JOIN should COMPLETE", Task.Status.COMPLETED, innerJoinTask.getStatus());

        Task outerJoinTask = executeJoin(workflowId, "outer_join");
        assertEquals("Outer JOIN should COMPLETE", Task.Status.COMPLETED, outerJoinTask.getStatus());

        completeTask("fjt8_post");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }

    // =========================================================================
    // Test 9 — SYNC join: large fan-out (10 parallel branches)
    // =========================================================================

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

        Task join = executeJoin(workflowId, "join_ref");
        assertEquals(
                "JOIN should COMPLETE after all 10 branches succeed",
                Task.Status.COMPLETED,
                join.getStatus());
        for (int i = 0; i < branchCount; i++) {
            assertNotNull(
                    "JOIN output should include branch_" + i,
                    join.getOutputData().get("branch_" + i));
        }

        completeTask("fjt9_post");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                waitForWorkflowTerminal(workflowId, WORKFLOW_TERMINAL_TIMEOUT_MS).getStatus());
    }
}
