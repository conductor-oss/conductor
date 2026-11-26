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
package com.netflix.conductor.test.integration.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Tasks for {@code CALL_MCP_TOOL} against a third-party server: mcp-testkit 1.1.0, which
 * implements the Tasks extension (protocol {@code 2026-07-28}) on the official MCP Python SDK. The
 * real engine runs the calls (Redis persistence and the production asynchronous system-task path),
 * as in {@link MCPTasksDurableEngineEndToEndTest}, which uses an embedded fake server instead.
 *
 * <p>The testkit's {@code task_scripted} tool scripts each remote task: {@code working} for {@code
 * working_polls} checks, then the given outcome. Where the remote side matters (cancellation), the
 * test reads the remote task back with {@code tasks/get}.
 */
@SpringBootTest(classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.app.sweeper.queuePopTimeout=750",
            "conductor.integrations.ai.enabled=true"
        })
class MCPTestkitTasksEndToEndTest {

    private static final String CALL_MCP_TOOL = "CALL_MCP_TOOL";
    private static final String VERSION = "2026-07-28";
    private static final String TASKS = "io.modelcontextprotocol/tasks";

    /** The testkit's task lifetime, which it also sends as {@code ttlMs}. */
    private static final long TASK_TTL_MS = 20_000;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    // --host 0.0.0.0 turns off the testkit's Host check, which would refuse the Docker-mapped
    // address. The log wait matters: Docker's port proxy accepts connections before the server
    // inside the container has bound its port.
    @SuppressWarnings("resource")
    private static final GenericContainer<?> TESTKIT =
            new GenericContainer<>(DockerImageName.parse("python:3.12-slim"))
                    .withExposedPorts(3001)
                    .withCommand(
                            "sh",
                            "-c",
                            "pip install --no-cache-dir mcp-testkit==1.1.0"
                                    + " && mcp-testkit --transport http --host 0.0.0.0 --port 3001"
                                    + " --task-ttl-ms "
                                    + TASK_TTL_MS)
                    .withLogConsumer(
                            new Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("mcp-testkit")))
                    .waitingFor(
                            Wait.forLogMessage(".*Application startup complete.*\\n", 1)
                                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        REDIS.start();
        TESTKIT.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "mcp-testkit-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "mcp-testkit-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
    }

    @Autowired private MetadataService metadataService;
    @Autowired private WorkflowExecutor workflowExecutor;
    @Autowired private ExecutionService executionService;
    @Autowired private QueueDAO queueDAO;
    @Autowired private AsyncSystemTaskExecutor asyncSystemTaskExecutor;

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private Set<WorkflowSystemTask> asyncSystemTasks;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static String mcpUrl() {
        return "http://localhost:" + TESTKIT.getMappedPort(3001) + "/mcp";
    }

    @Test
    void scriptedTaskIsPolledToCompletion() {
        String workflowId =
                startWorkflow(
                        registerWorkflow(scripted("completed", "testkit report", 2), true, 0));

        Workflow done = awaitTerminal(workflowId, 60);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus(), reason(done));
        Task task = mcpTask(done);
        assertEquals(Task.Status.COMPLETED, task.getStatus());
        assertTrue(text(task).contains("testkit report"), text(task));
        String remoteTaskId = remoteTaskId(task);
        assertNotNull(remoteTaskId, "the remote task ID is kept in mcpTask");
        assertTrue(remoteTaskId.startsWith("task-"), remoteTaskId);
    }

    @Test
    void toolErrorCompletesTheCallWithIsError() {
        String workflowId =
                startWorkflow(registerWorkflow(scripted("tool_error", "bad region", 1), true, 0));

        Workflow done = awaitTerminal(workflowId, 60);

        // As for a synchronous call: a tool error is the tool's answer, not a failed call.
        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus(), reason(done));
        Task task = mcpTask(done);
        assertEquals(Boolean.TRUE, task.getOutputData().get("isError"));
        assertTrue(text(task).contains("bad region"), text(task));
    }

    @Test
    void failedTaskFailsTheCall() {
        String workflowId =
                startWorkflow(registerWorkflow(scripted("failed", "disk full", 1), true, 1));

        Workflow done = awaitTerminal(workflowId, 60);

        assertEquals(Workflow.WorkflowStatus.FAILED, done.getStatus());
        Task task = mcpTask(done);
        assertEquals(Task.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("failed"), reason(done));
        assertTrue(task.getReasonForIncompletion().contains("disk full"), reason(done));
    }

    @Test
    void taskCancelledByTheServerCancelsTheCall_withoutARetry() {
        String workflowId =
                startWorkflow(
                        registerWorkflow(scripted("cancelled", "stopped by operator", 1), true, 1));

        Workflow done = awaitTerminal(workflowId, 60);

        // A CANCELED task is not retried, and the engine terminates its workflow.
        assertEquals(Workflow.WorkflowStatus.TERMINATED, done.getStatus());
        assertEquals(Task.Status.CANCELED, mcpTask(done).getStatus());
        assertEquals(
                1,
                done.getTasks().stream()
                        .filter(t -> t.getTaskType().equals("CALL_MCP_TOOL"))
                        .count());
        String reason = mcpTask(done).getReasonForIncompletion();
        assertTrue(reason.contains("MCP server cancelled task"), reason);
        assertTrue(reason.contains("stopped by operator"), reason);
    }

    @Test
    void inputRequiredFailsTheCall_andCancelsTheRemoteTask() throws Exception {
        String workflowId =
                startWorkflow(registerWorkflow(scripted("input_required", "Confirm?", 1), true, 1));

        Workflow done = awaitTerminal(workflowId, 60);

        assertEquals(Workflow.WorkflowStatus.FAILED, done.getStatus());
        Task task = mcpTask(done);
        assertTrue(task.getReasonForIncompletion().contains("requires input"), reason(done));
        // The testkit keeps an input_required task waiting until tasks/cancel arrives.
        JsonNode remote = remoteTask(remoteTaskId(task));
        assertEquals("cancelled", remote.get("status").asString(), remote.toString());
    }

    @Test
    void terminationSendsTasksCancelToTheServer() throws Exception {
        String workflowId = startWorkflow(registerWorkflow(scripted("working", "", 0), true, 0));
        String remoteTaskId = remoteTaskId(awaitPolling(workflowId));

        workflowExecutor.terminateWorkflow(workflowId, "test termination");

        Workflow terminated = executionService.getExecutionStatus(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminated.getStatus());
        assertEquals(Task.Status.CANCELED, mcpTask(terminated).getStatus());
        JsonNode remote = remoteTask(remoteTaskId);
        assertEquals("cancelled", remote.get("status").asString(), remote.toString());
        assertEquals("Cancelled by client", remote.get("statusMessage").asString());
    }

    @Test
    void pollingStopsAtTheServersTtl() throws Exception {
        String workflowId = startWorkflow(registerWorkflow(scripted("working", "", 0), true, 0));

        Workflow done = awaitTerminal(workflowId, 90);

        assertEquals(Workflow.WorkflowStatus.FAILED, done.getStatus());
        Task task = mcpTask(done);
        assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(
                task.getReasonForIncompletion()
                        .contains("the server's ttlMs of " + TASK_TTL_MS + " ms"),
                reason(done));
    }

    @Test
    void callWithoutAProtocolVersionStaysSynchronous() {
        String workflowId =
                startWorkflow(
                        registerWorkflow(scripted("completed", "legacy report", 3), false, 0));

        Workflow done = awaitTerminal(workflowId, 60);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus(), reason(done));
        Task task = mcpTask(done);
        assertTrue(text(task).contains("legacy report"), text(task));
        assertEquals(null, task.getOutputData().get("mcpTask"), "no remote task on a legacy call");
    }

    // ---- helpers

    /** Arguments for the testkit's {@code task_scripted} tool, polled every 200 ms. */
    private static Map<String, Object> scripted(String outcome, String message, int workingPolls) {
        return Map.of(
                "outcome",
                outcome,
                "message",
                message,
                "working_polls",
                workingPolls,
                "poll_interval_ms",
                200);
    }

    /** Reads a remote task straight from the testkit with {@code tasks/get}. */
    private JsonNode remoteTask(String taskId) throws Exception {
        String meta =
                "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\""
                        + VERSION
                        + "\",\"io.modelcontextprotocol/clientInfo\":{\"name\":\"e2e\",\"version\":\"1\"},"
                        + "\"io.modelcontextprotocol/clientCapabilities\":{\"extensions\":{\""
                        + TASKS
                        + "\":{}}}}";
        String body =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/get\",\"params\":{\"taskId\":\""
                        + taskId
                        + "\","
                        + meta
                        + "}}";
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(mcpUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("MCP-Protocol-Version", VERSION)
                        .header("Mcp-Method", "tasks/get")
                        .header("Mcp-Name", taskId)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        String response = http.send(request, HttpResponse.BodyHandlers.ofString()).body();
        // The response is JSON, or one server-sent event that carries it.
        String json =
                response.lines()
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring("data:".length()).trim())
                        .findFirst()
                        .orElse(response);
        JsonNode result = mapper.readTree(json).get("result");
        assertNotNull(result, json);
        return result;
    }

    private Task awaitPolling(String workflowId) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue();
                            Task t = mcpTask(executionService.getExecutionStatus(workflowId, true));
                            latest.set(t);
                            return t != null
                                    && t.getStatus() == Task.Status.IN_PROGRESS
                                    && remoteTaskId(t) != null;
                        });
        return latest.get();
    }

    private Workflow awaitTerminal(String workflowId, int seconds) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(seconds, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue();
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            latest.set(wf);
                            return wf != null
                                    && wf.getStatus() != null
                                    && wf.getStatus().isTerminal();
                        });
        return latest.get();
    }

    /** Runs visible CALL_MCP_TOOL queue entries through the production async system-task path. */
    private void drainQueue() {
        WorkflowSystemTask task =
                asyncSystemTasks.stream()
                        .filter(t -> CALL_MCP_TOOL.equals(t.getTaskType()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "CALL_MCP_TOOL is not registered; the ai module"
                                                        + " and conductor.integrations.ai.enabled"
                                                        + " are required"));
        for (String taskId : queueDAO.pop(CALL_MCP_TOOL, 5, 0)) {
            asyncSystemTaskExecutor.execute(task, taskId);
        }
    }

    private static Task mcpTask(Workflow workflow) {
        if (workflow == null || workflow.getTasks() == null) {
            return null;
        }
        List<Task> tasks =
                workflow.getTasks().stream()
                        .filter(t -> CALL_MCP_TOOL.equals(t.getTaskType()))
                        .toList();
        return tasks.isEmpty() ? null : tasks.get(tasks.size() - 1);
    }

    private static String remoteTaskId(Task task) {
        if (task == null
                || !(task.getOutputData().get("mcpTask") instanceof Map<?, ?> state)
                || !(state.get("taskId") instanceof String id)) {
            return null;
        }
        return id;
    }

    /** The text of the first content item of a call's output. */
    private static String text(Task task) {
        if (!(task.getOutputData().get("content") instanceof List<?> content)
                || content.isEmpty()
                || !(content.get(0) instanceof Map<?, ?> first)) {
            return String.valueOf(task.getOutputData());
        }
        return String.valueOf(first.get("text"));
    }

    private static String reason(Workflow workflow) {
        Task task = mcpTask(workflow);
        return "workflow "
                + workflow.getStatus()
                + ": "
                + workflow.getReasonForIncompletion()
                + (task == null ? "" : " / task: " + task.getReasonForIncompletion());
    }

    private String startWorkflow(String name) {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setName(name);
        input.setVersion(1);
        input.setWorkflowInput(new HashMap<>());
        return workflowExecutor.startWorkflow(input);
    }

    /**
     * A workflow with one CALL_MCP_TOOL step that calls {@code task_scripted}. Each workflow has
     * its own task definition, so tests do not depend on each other. {@code AIModelTaskMapper}
     * raises a retry count below 1 to 3; tests that expect a failure pass 1 to keep them short.
     */
    private String registerWorkflow(
            Map<String, Object> arguments, boolean selectProtocolVersion, int retryCount) {
        String taskName = "mcp_testkit_call_" + UUID.randomUUID();
        TaskDef taskDef = new TaskDef();
        taskDef.setName(taskName);
        taskDef.setRetryCount(retryCount);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setTimeoutSeconds(120);
        taskDef.setResponseTimeoutSeconds(60);
        taskDef.setOwnerEmail("mcp-e2e@conductor.test");
        metadataService.registerTaskDef(List.of(taskDef));

        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName("scripted");
        task.setType(CALL_MCP_TOOL);
        Map<String, Object> input = new HashMap<>();
        input.put("mcpServer", mcpUrl());
        input.put("method", "task_scripted");
        input.put("arguments", arguments);
        if (selectProtocolVersion) {
            input.put("mcpOptions", Map.of("protocolVersion", VERSION));
        }
        task.setInputParameters(input);

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_testkit_e2e_" + UUID.randomUUID());
        def.setVersion(1);
        def.setOwnerEmail("mcp-e2e@conductor.test");
        def.setTasks(List.of(task));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }
}
