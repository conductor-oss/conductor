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
package com.netflix.conductor.test.integration.a2a;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The durability money-shot, through the <b>real engine</b>: a {@code AGENT} task is driven by the
 * genuine decider + {@link AsyncSystemTaskExecutor} + Redis-backed persistence (not a mocked
 * engine), against a slow A2A agent, and proves crash/restart resume.
 *
 * <p>"Durable A2A" claim under test: while the remote agent is still {@code working}, all of the
 * in-flight call state (the remote {@code taskId}, the deadline anchor) lives in the persisted task
 * output — <b>not</b> in a worker thread. So a process that lost all memory can reload the task
 * from the DAO and keep polling to completion. We model the crash by capturing only the workflow id
 * and resuming purely from {@code ExecutionService.getExecutionStatus} + {@code
 * AsyncSystemTaskExecutor.execute} (which reloads each {@code TaskModel} from the DAO every cycle).
 * The embedded agent stands in for the external agent, which does <i>not</i> crash when Conductor
 * does — it keeps serving and eventually flips to {@code completed}.
 *
 * <p>System-task workers are disabled in test mode, so we drain the {@code AGENT} queue and execute
 * via {@link AsyncSystemTaskExecutor} — the same path {@code SystemTaskWorkerCoordinator} takes in
 * production.
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
            "conductor.integrations.ai.enabled=true",
            // The embedded agent runs on loopback; let the engine's A2A client reach it.
            "conductor.a2a.client.allow-private-network=true"
        })
class A2ADurableEngineEndToEndTest {

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
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "a2a-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "a2a-e2e");
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
    private java.util.Set<WorkflowSystemTask> asyncSystemTasks;

    private SlowA2AAgent agent;

    @BeforeEach
    void startAgent() throws IOException {
        agent = SlowA2AAgent.start(2); // completes on the 2nd tasks/get poll
    }

    @AfterEach
    void stopAgent() {
        if (agent != null) {
            agent.stop();
        }
    }

    @Test
    void callAgentSurvivesCrashAndResumesFromPersistence() {
        String wfName = "a2a_durable_e2e_" + UUID.randomUUID();
        registerCallAgentWorkflow(wfName, agent.url());
        String workflowId = startWorkflow(wfName);

        // ── Phase 1: drive until message/send is done and we're polling the working agent. ──
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAgentTasks();
                            Task t = callAgentTask(workflowId);
                            return t != null
                                    && t.getStatus() == Task.Status.IN_PROGRESS
                                    && t.getOutputData().get("taskId") != null;
                        });

        // ── Crash boundary: keep ONLY the workflow id; everything else is "lost". ──
        // Read in-flight state fresh from the DAO — this is all a restarted process would have.
        Task inflight = callAgentTask(workflowId);
        String remoteTaskId = (String) inflight.getOutputData().get("taskId");
        assertNotNull(remoteTaskId, "remote A2A taskId must be persisted to resume after a crash");
        assertNotNull(
                inflight.getOutputData().get("a2aStartedAt"),
                "deadline anchor must be persisted so the resumed poll loop keeps its bound");

        // ── Phase 2 ("after restart"): resume purely from persistence and finish. ──
        Workflow completed = awaitTerminal(workflowId);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, completed.getStatus());
        Task done = callAgentTask(completed);
        assertEquals("completed", done.getOutputData().get("state"));
        assertEquals(
                remoteTaskId,
                done.getOutputData().get("taskId"),
                "the SAME remote task was resumed — no new message/send, no duplicate work");
        assertTrue(
                String.valueOf(done.getOutputData().get("text")).contains("durable"),
                "the agent's artifact text should reach AGENT output: "
                        + done.getOutputData().get("text"));
        // Exactly one message/send — the crash/resume did not re-send (idempotent at-least-once).
        assertEquals(
                1, agent.sendCount(), "message/send must happen exactly once across the resume");
    }

    // ── engine helpers ─────────────────────────────────────────────────────

    private Workflow awaitTerminal(String workflowId) {
        AtomicReference<Workflow> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAgentTasks();
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            latest.set(wf);
                            return wf != null
                                    && wf.getStatus() != null
                                    && wf.getStatus().isTerminal();
                        });
        return latest.get();
    }

    private void drainAgentTasks() {
        WorkflowSystemTask callAgent =
                asyncSystemTasks.stream()
                        .filter(t -> "AGENT".equals(t.getTaskType()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "AGENT WorkflowSystemTask was not registered —"
                                                        + " conductor.integrations.ai.enabled must be"
                                                        + " true and the ai module on the classpath."));
        for (String taskId : queueDAO.pop("AGENT", 5, 100)) {
            asyncSystemTaskExecutor.execute(callAgent, taskId);
        }
    }

    private Task callAgentTask(String workflowId) {
        return callAgentTask(executionService.getExecutionStatus(workflowId, true));
    }

    private Task callAgentTask(Workflow workflow) {
        if (workflow == null || workflow.getTasks() == null) {
            return null;
        }
        return workflow.getTasks().stream()
                .filter(t -> "AGENT".equals(t.getTaskType()))
                .findFirst()
                .orElse(null);
    }

    private String startWorkflow(String name) {
        StartWorkflowInput swi = new StartWorkflowInput();
        swi.setName(name);
        swi.setVersion(1);
        swi.setWorkflowInput(new java.util.HashMap<>());
        return workflowExecutor.startWorkflow(swi);
    }

    private void registerCallAgentWorkflow(String name, String agentUrl) {
        TaskDef td = new TaskDef();
        td.setName("AGENT");
        td.setRetryCount(0);
        td.setTimeoutSeconds(120);
        try {
            metadataService.registerTaskDef(List.of(td));
        } catch (Exception ignored) {
            // already registered by a prior test
        }

        WorkflowTask task = new WorkflowTask();
        task.setName("AGENT");
        task.setTaskReferenceName("callAgent");
        task.setType("AGENT");
        Map<String, Object> taskInput = new java.util.HashMap<>();
        taskInput.put("agentUrl", agentUrl);
        taskInput.put("text", "process this durably");
        taskInput.put("pollIntervalSeconds", 1);
        task.setInputParameters(taskInput);

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("a2a-e2e@conductor.test");
        def.setTasks(List.of(task));
        metadataService.updateWorkflowDef(List.of(def));
    }

    /**
     * A self-contained, dependency-free A2A agent (JDK {@link HttpServer}) that is deliberately
     * slow: {@code message/send} returns a {@code working} task, and {@code tasks/get} stays {@code
     * working} until the configured number of polls, then returns {@code completed} with an
     * artifact. Stands in for an external agent that keeps running across a Conductor crash.
     */
    private static final class SlowA2AAgent {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final HttpServer server;
        private final int port;
        private final int completeAfterPolls;
        private final Map<String, Integer> pollCounts = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicInteger sends =
                new java.util.concurrent.atomic.AtomicInteger();

        private SlowA2AAgent(HttpServer server, int port, int completeAfterPolls) {
            this.server = server;
            this.port = port;
            this.completeAfterPolls = completeAfterPolls;
        }

        static SlowA2AAgent start(int completeAfterPolls) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SlowA2AAgent agent =
                    new SlowA2AAgent(server, server.getAddress().getPort(), completeAfterPolls);
            server.createContext("/", agent::handle);
            server.start();
            return agent;
        }

        String url() {
            return "http://localhost:" + port + "/";
        }

        int sendCount() {
            return sends.get();
        }

        void stop() {
            server.stop(0);
        }

        private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("GET".equals(exchange.getRequestMethod()) && path.contains("agent")) {
                    write(exchange, MAPPER.writeValueAsBytes(agentCard()));
                    return;
                }
                Map<?, ?> request =
                        MAPPER.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
                Object id = request.get("id");
                String method = String.valueOf(request.get("method"));
                Map<?, ?> params = (Map<?, ?>) request.get("params");

                Object result;
                if ("message/send".equals(method)) {
                    sends.incrementAndGet();
                    String taskId = "agent-task-" + sends.get();
                    pollCounts.put(taskId, 0);
                    result = task(taskId, "working", null);
                } else if ("tasks/get".equals(method)) {
                    String taskId = String.valueOf(params.get("id"));
                    int polls = pollCounts.merge(taskId, 1, Integer::sum);
                    result =
                            polls >= completeAfterPolls
                                    ? task(
                                            taskId,
                                            "completed",
                                            "durable echo: process this durably")
                                    : task(taskId, "working", null);
                } else {
                    write(exchange, error(id, "method not found: " + method));
                    return;
                }
                write(exchange, MAPPER.writeValueAsBytes(rpcResult(id, result)));
            } catch (Exception e) {
                byte[] body =
                        ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        }

        private static Map<String, Object> rpcResult(Object id, Object result) {
            return Map.of("jsonrpc", "2.0", "id", id == null ? 1 : id, "result", result);
        }

        private byte[] error(Object id, String message) throws IOException {
            return MAPPER.writeValueAsBytes(
                    Map.of(
                            "jsonrpc",
                            "2.0",
                            "id",
                            id == null ? 1 : id,
                            "error",
                            Map.of("code", -32601, "message", message)));
        }

        private static Map<String, Object> task(String taskId, String state, String artifactText) {
            java.util.Map<String, Object> task = new java.util.HashMap<>();
            task.put("kind", "task");
            task.put("id", taskId);
            task.put("contextId", "ctx-" + taskId);
            task.put("status", Map.of("state", state));
            if (artifactText != null) {
                task.put(
                        "artifacts",
                        List.of(
                                Map.of(
                                        "artifactId",
                                        "a1",
                                        "parts",
                                        List.of(Map.of("kind", "text", "text", artifactText)))));
            }
            return task;
        }

        private static Map<String, Object> agentCard() {
            return Map.of(
                    "name", "Slow Durable Agent",
                    "description", "Emits a working task that completes after a few polls",
                    "url", "http://localhost/",
                    "version", "1.0.0",
                    "capabilities", Map.of("streaming", false),
                    "skills", List.of(Map.of("id", "echo", "name", "Echo")));
        }

        private void write(com.sun.net.httpserver.HttpExchange exchange, byte[] body)
                throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
