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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MCP Tasks for {@code CALL_MCP_TOOL} through the <b>real engine</b> (change {@code
 * add-mcp-tasks-polling}, task group 6): the genuine decider, Redis-backed persistence, and the
 * production asynchronous system-task path ({@link AsyncSystemTaskExecutor}, which reloads each
 * task from the DAO on every run), against an embedded MCP server.
 *
 * <p>The embedded server speaks the MCP {@code 2026-07-28} form: {@code tools/call} with the Tasks
 * extension declared returns a {@code CreateTaskResult}, {@code tasks/get} reports {@code working}
 * for a few checks and then the scripted outcome, and {@code tasks/cancel} is recorded. A {@code
 * tools/call} without {@code _meta} (the legacy request) gets an immediate result.
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
class McpTasksDurableEngineEndToEndTest {

    private static final String CALL_MCP_TOOL = "CALL_MCP_TOOL";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("conductor.redis.availability-zone", () -> "us-east-1c");
        registry.add("conductor.redis.data-center-region", () -> "us-east-1");
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "mcp-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "mcp-e2e");
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

    private FakeMcpServer mcp;

    @BeforeEach
    void startServer() throws IOException {
        mcp = FakeMcpServer.start();
    }

    @AfterEach
    void stopServer() {
        if (mcp != null) {
            mcp.stop();
        }
    }

    // ---- 6.1 Durable polling

    @Test
    void createdTaskIsPolledFromPersistenceToCompletion() {
        mcp.script("completed", 3);
        String workflowId = startWorkflow(registerWorkflow(true));

        Task inflight = awaitPolling(workflowId);
        String conductorTaskId = inflight.getTaskId();
        String remoteTaskId = remoteTaskId(inflight);
        assertNotNull(remoteTaskId, "mcpTask.taskId must be persisted to resume after a restart");

        // Drive one run at a time and check the persisted task after every tasks/get that
        // answered "working": it stays IN_PROGRESS with the same Conductor and remote task IDs.
        int getsBeforeLoop = mcp.count("tasks/get");
        AtomicInteger checkedGets = new AtomicInteger(getsBeforeLoop);
        AtomicInteger workingCycles = new AtomicInteger();
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue();
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            latest.set(wf);
                            int gets = mcp.count("tasks/get");
                            if (gets > checkedGets.getAndSet(gets)
                                    && !wf.getStatus().isTerminal()) {
                                Task t = mcpTask(wf);
                                assertEquals(Task.Status.IN_PROGRESS, t.getStatus());
                                assertEquals(conductorTaskId, t.getTaskId());
                                assertEquals(remoteTaskId, remoteTaskId(t));
                                workingCycles.incrementAndGet();
                            }
                            return wf.getStatus().isTerminal();
                        });
        Workflow done = latest.get();

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus());
        Task task = mcpTask(done);
        assertEquals(conductorTaskId, task.getTaskId(), "the Conductor task ID never changes");
        assertEquals(remoteTaskId, remoteTaskId(task), "the same remote task was resumed");
        assertEquals(1, mcp.count("tools/call"), "exactly one tools/call across all runs");
        assertEquals(3, mcp.count("tasks/get"), "two working answers, then completed");
        assertEquals(
                2 - getsBeforeLoop,
                workingCycles.get(),
                "every working answer after awaitPolling was checked for IN_PROGRESS");
        assertEquals(
                List.of(remoteTaskId),
                mcp.distinctMcpNames("tasks/get"),
                "every tasks/get used the persisted task ID");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) task.getOutputData().get("content");
        assertEquals("durable report", content.get(0).get("text"));
    }

    // ---- 6.2 Retry reset

    @Test
    void retryPolicyStartsANewToolCallWithFreshState() {
        mcp.script("failed", 1);
        mcp.script("completed", 1);
        String workflowId = startWorkflow(registerWorkflow(true, 1));

        Task scheduledRetry = awaitScheduledAttempt(workflowId, 2);
        assertEquals(Task.Status.SCHEDULED, scheduledRetry.getStatus());
        assertFalse(
                scheduledRetry.getOutputData().containsKey("mcpTask"),
                "the retried task starts with no mcpTask");

        Workflow done = awaitTerminal(workflowId);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus());
        List<Task> attempts = mcpTasks(done);
        assertEquals(2, attempts.size());
        assertEquals(Task.Status.FAILED, attempts.get(0).getStatus());
        assertEquals(Task.Status.COMPLETED, attempts.get(1).getStatus());
        assertNotEquals(attempts.get(0).getTaskId(), attempts.get(1).getTaskId());
        assertEquals(attempts.get(0).getTaskId(), attempts.get(1).getRetriedTaskId());
        assertNotEquals(remoteTaskId(attempts.get(0)), remoteTaskId(attempts.get(1)));
        assertEquals(2, mcp.count("tools/call"), "the retry sent a new tools/call");
    }

    @Test
    void userRetryStartsANewToolCallWithFreshState() {
        // AIModelTaskMapper raises a retryCount below 1 to 3; with retryCount 1, two remote
        // failures exhaust the retries and fail the workflow.
        mcp.script("failed", 1);
        mcp.script("failed", 1);
        mcp.script("completed", 1);
        String workflowId = startWorkflow(registerWorkflow(true, 1));

        Workflow failed = awaitTerminal(workflowId);
        assertEquals(Workflow.WorkflowStatus.FAILED, failed.getStatus());
        Task failedTask = mcpTask(failed);
        String firstRemoteTask = remoteTaskId(failedTask);

        workflowExecutor.retry(workflowId, false);
        Task scheduledRetry = mcpTask(executionService.getExecutionStatus(workflowId, true));
        assertEquals(Task.Status.SCHEDULED, scheduledRetry.getStatus());
        assertNotEquals(failedTask.getTaskId(), scheduledRetry.getTaskId(), "a new task ID");
        assertEquals(failedTask.getTaskId(), scheduledRetry.getRetriedTaskId());
        assertFalse(
                scheduledRetry.getOutputData().containsKey("mcpTask"),
                "the retried task starts with no mcpTask");

        Workflow done = awaitTerminal(workflowId);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus());
        Task retried = mcpTask(done);
        assertEquals(scheduledRetry.getTaskId(), retried.getTaskId());
        assertNotEquals(firstRemoteTask, remoteTaskId(retried));
        assertEquals(3, mcp.count("tools/call"), "the user retry sent a new tools/call");
    }

    // ---- 6.3 Cancellation on termination

    @Test
    void terminationSendsTasksCancelForThePollingTask() {
        mcp.script("completed", 1000);
        String workflowId = startWorkflow(registerWorkflow(true));
        String remoteTaskId = remoteTaskId(awaitPolling(workflowId));

        workflowExecutor.terminateWorkflow(workflowId, "test termination");

        Workflow terminated = executionService.getExecutionStatus(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminated.getStatus());
        assertEquals(Task.Status.CANCELED, mcpTask(terminated).getStatus());
        assertEquals(List.of(remoteTaskId), mcp.distinctMcpNames("tasks/cancel"));
        assertEquals(
                List.of(remoteTaskId),
                mcp.paramTaskIds("tasks/cancel"),
                "params.taskId of tasks/cancel");
    }

    // ---- 6.4 Legacy call through the real engine

    @Test
    void legacyCallIsUnchangedThroughTheEngine() {
        String workflowId = startWorkflow(registerWorkflow(false));

        Workflow done = awaitTerminal(workflowId);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus());
        assertEquals(List.of("tools/call"), mcp.methods(), "one legacy tools/call, nothing else");
        assertFalse(mcp.lastToolsCallHadMeta(), "the legacy request carries no _meta");
        assertNull(mcp.lastProtocolVersionHeader(), "no MCP-Protocol-Version header");
        FakeMcpServer.Call call = mcp.lastCall();
        assertEquals("report", call.toolName());
        assertEquals(Map.of("region", "us-west1"), call.arguments());
        assertEquals(List.of(), call.mcpHeaders(), "no Mcp-* headers on a legacy request");
        Task task = mcpTask(done);
        assertEquals(Set.of("content", "isError"), task.getOutputData().keySet());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) task.getOutputData().get("content");
        assertEquals("legacy result", content.get(0).get("text"));
    }

    // ---- engine helpers

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

    /**
     * Runs the workflow until it has {@code attempts} CALL_MCP_TOOL tasks and returns the newest
     * one before it runs. The queue has no other consumer, so the new task stays SCHEDULED.
     */
    private Task awaitScheduledAttempt(String workflowId, int attempts) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            List<Task> tasks =
                                    mcpTasks(executionService.getExecutionStatus(workflowId, true));
                            if (tasks.size() >= attempts) {
                                latest.set(tasks.get(attempts - 1));
                                return true;
                            }
                            drainQueue();
                            return false;
                        });
        return latest.get();
    }

    private Workflow awaitTerminal(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
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
        List<Task> tasks = mcpTasks(workflow);
        return tasks.isEmpty() ? null : tasks.get(tasks.size() - 1);
    }

    private static List<Task> mcpTasks(Workflow workflow) {
        if (workflow == null || workflow.getTasks() == null) {
            return List.of();
        }
        return workflow.getTasks().stream()
                .filter(t -> CALL_MCP_TOOL.equals(t.getTaskType()))
                .toList();
    }

    private static String remoteTaskId(Task task) {
        if (task == null
                || !(task.getOutputData().get("mcpTask") instanceof Map<?, ?> state)
                || !(state.get("taskId") instanceof String id)) {
            return null;
        }
        return id;
    }

    private String startWorkflow(String name) {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setName(name);
        input.setVersion(1);
        input.setWorkflowInput(new HashMap<>());
        return workflowExecutor.startWorkflow(input);
    }

    /**
     * Each workflow's CALL_MCP_TOOL step has its own task name and task definition, so tests do not
     * depend on the metadata cache or on each other. The task type stays CALL_MCP_TOOL.
     */
    private static TaskDef taskDef(String name, int retryCount) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(name);
        taskDef.setRetryCount(retryCount);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setTimeoutSeconds(120);
        taskDef.setResponseTimeoutSeconds(60);
        taskDef.setOwnerEmail("mcp-e2e@conductor.test");
        return taskDef;
    }

    private String registerWorkflow(boolean selectProtocolVersion) {
        return registerWorkflow(selectProtocolVersion, 0);
    }

    private String registerWorkflow(boolean selectProtocolVersion, int retryCount) {
        String taskName = "mcp_call_" + UUID.randomUUID();
        metadataService.registerTaskDef(List.of(taskDef(taskName, retryCount)));
        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName("report");
        task.setType(CALL_MCP_TOOL);
        Map<String, Object> input = new HashMap<>();
        input.put("mcpServer", mcp.url());
        input.put("method", "report");
        input.put("arguments", Map.of("region", "us-west1"));
        if (selectProtocolVersion) {
            input.put("mcpOptions", Map.of("protocolVersion", "2026-07-28"));
        }
        task.setInputParameters(input);

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_tasks_e2e_" + UUID.randomUUID());
        def.setVersion(1);
        def.setOwnerEmail("mcp-e2e@conductor.test");
        def.setTasks(List.of(task));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    /**
     * A dependency-free MCP server (JDK {@link HttpServer}). Each {@code tools/call} in the
     * 2026-07-28 form creates the next scripted remote task; its {@code tasks/get} answers {@code
     * working} until the scripted number of checks, then the scripted terminal status.
     */
    private static final class FakeMcpServer {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private record Script(String outcome, int checksBeforeOutcome) {}

        private record Call(
                String method,
                String mcpName,
                boolean hadMeta,
                String version,
                String toolName,
                Object arguments,
                String paramTaskId,
                List<String> mcpHeaders) {}

        private final HttpServer server;
        private final List<Script> scripts = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Script> taskScripts =
                Collections.synchronizedMap(new HashMap<>());
        private final Map<String, Integer> checks = Collections.synchronizedMap(new HashMap<>());
        private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        private FakeMcpServer(HttpServer server) {
            this.server = server;
        }

        static FakeMcpServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeMcpServer fake = new FakeMcpServer(server);
            server.createContext("/", fake::handle);
            server.start();
            return fake;
        }

        void script(String outcome, int checksBeforeOutcome) {
            scripts.add(new Script(outcome, checksBeforeOutcome));
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort() + "/mcp";
        }

        void stop() {
            server.stop(0);
        }

        int count(String method) {
            synchronized (calls) {
                return (int) calls.stream().filter(c -> c.method().equals(method)).count();
            }
        }

        List<String> methods() {
            synchronized (calls) {
                return calls.stream().map(Call::method).toList();
            }
        }

        List<String> distinctMcpNames(String method) {
            synchronized (calls) {
                return calls.stream()
                        .filter(c -> c.method().equals(method))
                        .map(Call::mcpName)
                        .distinct()
                        .toList();
            }
        }

        boolean lastToolsCallHadMeta() {
            synchronized (calls) {
                return calls.stream()
                        .filter(c -> c.method().equals("tools/call"))
                        .reduce((a, b) -> b)
                        .map(Call::hadMeta)
                        .orElse(false);
            }
        }

        List<String> paramTaskIds(String method) {
            synchronized (calls) {
                return calls.stream()
                        .filter(c -> c.method().equals(method))
                        .map(Call::paramTaskId)
                        .toList();
            }
        }

        Call lastCall() {
            synchronized (calls) {
                return calls.get(calls.size() - 1);
            }
        }

        String lastProtocolVersionHeader() {
            synchronized (calls) {
                return calls.isEmpty() ? null : calls.get(calls.size() - 1).version();
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                respond(exchange);
            } catch (RuntimeException | IOException e) {
                // Answer at once so the client fails fast instead of waiting for its read timeout.
                byte[] body = String.valueOf(e).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        }

        private void respond(HttpExchange exchange) throws IOException {
            Map<?, ?> request =
                    MAPPER.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
            String method = String.valueOf(request.get("method"));
            Map<?, ?> params = (Map<?, ?>) request.get("params");
            boolean hadMeta = params != null && params.containsKey("_meta");
            List<String> mcpHeaders =
                    exchange.getRequestHeaders().keySet().stream()
                            .filter(h -> h.toLowerCase(Locale.ROOT).startsWith("mcp-"))
                            .sorted()
                            .toList();
            calls.add(
                    new Call(
                            method,
                            exchange.getRequestHeaders().getFirst("Mcp-Name"),
                            hadMeta,
                            exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"),
                            params == null ? null : (String) params.get("name"),
                            params == null ? null : params.get("arguments"),
                            params == null ? null : (String) params.get("taskId"),
                            mcpHeaders));

            Object result =
                    switch (method) {
                        case "tools/list" ->
                                Map.of(
                                        "tools",
                                        List.of(
                                                Map.of(
                                                        "name",
                                                        "report",
                                                        "inputSchema",
                                                        Map.of("type", "object"))));
                        case "tools/call" -> hadMeta ? createTask() : textResult("legacy result");
                        case "tasks/get" -> taskStatus(String.valueOf(params.get("taskId")));
                        case "tasks/cancel" -> Map.of("resultType", "complete");
                        default -> Map.of();
                    };
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", request.get("id"));
            response.put("result", result);
            byte[] body = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private Map<String, Object> createTask() {
            String taskId = "mcp-task-" + (taskScripts.size() + 1);
            Script script = scripts.isEmpty() ? new Script("completed", 1) : scripts.remove(0);
            taskScripts.put(taskId, script);
            checks.put(taskId, 0);
            return Map.of(
                    "resultType",
                    "task",
                    "taskId",
                    taskId,
                    "status",
                    "working",
                    "pollIntervalMs",
                    1000);
        }

        private Map<String, Object> taskStatus(String taskId) {
            Script script = taskScripts.get(taskId);
            int seen = checks.merge(taskId, 1, Integer::sum);
            if (script == null || seen < script.checksBeforeOutcome()) {
                return Map.of(
                        "resultType",
                        "complete",
                        "taskId",
                        taskId,
                        "status",
                        "working",
                        "pollIntervalMs",
                        1000);
            }
            if ("failed".equals(script.outcome())) {
                return Map.of(
                        "resultType",
                        "complete",
                        "taskId",
                        taskId,
                        "status",
                        "failed",
                        "error",
                        Map.of("code", -32603, "message", "remote failure"));
            }
            return Map.of(
                    "resultType",
                    "complete",
                    "taskId",
                    taskId,
                    "status",
                    "completed",
                    "result",
                    textResult("durable report"));
        }

        private static Map<String, Object> textResult(String text) {
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", text)), "isError", false);
        }
    }
}
