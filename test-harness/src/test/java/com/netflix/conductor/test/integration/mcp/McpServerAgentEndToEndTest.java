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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deployed agents as MCP tools, against the real embedded AgentSpan ({@link AgentService}, its
 * compiler and the Redis-backed stores). The agent's LLM step is completed by the test (system task
 * workers are off in the harness), so no model provider is needed.
 *
 * <p>No workflow opt-in is on, so every agent reaches MCP through the agent source alone. One agent
 * name is also in {@code exposedWorkflows}: the workflow source must skip it, and it must still
 * appear once, as an agent.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.db.type=redis_standalone",
            "conductor.queue.type=redis_standalone",
            "conductor.app.sweeperThreadCount=1",
            "conductor.app.sweeper.sweepBatchSize=1",
            "conductor.app.sweeper.queuePopTimeout=750",
            "conductor.integrations.ai.enabled=true",
            "agentspan.embedded=true",
            "conductor.mcp.server.enabled=true",
            "conductor.mcp.server.exposedWorkflows=" + McpServerAgentEndToEndTest.OPTED_IN_AGENT,
            // A call returns a task at once, so the test drives the agent between polls.
            "conductor.mcp.server.syncWaitSeconds=0"
        })
class McpServerAgentEndToEndTest {

    private static final String VERSION = "2026-07-28";
    private static final String TASKS = "io.modelcontextprotocol/tasks";
    private static final String LLM = "LLM_CHAT_COMPLETE";
    static final String OPTED_IN_AGENT = "mcp_agent_also_opted_in_as_a_workflow";

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
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "mcp-agent-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "mcp-agent-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
    }

    @LocalServerPort private int port;

    @Autowired private AgentService agentService;
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

    @Test
    void deployedAgentIsListedWithAPromptSchema_andNoOptIn() throws Exception {
        String agent = deployAgent("Answers questions about orders");

        List<JsonNode> entries = listed(agent);
        assertEquals(1, entries.size(), entries.toString());
        JsonNode tool = entries.get(0);
        assertEquals("Answers questions about orders", tool.get("description").asString());
        JsonNode schema = tool.get("inputSchema");
        assertEquals("string", schema.get("properties").get("prompt").get("type").asString());
        assertEquals("prompt", schema.get("required").get(0).asString());
        Set<String> fields = new TreeSet<>();
        tool.propertyNames().forEach(fields::add);
        assertEquals(Set.of("description", "inputSchema", "name"), fields, "no kind is shown");
    }

    @Test
    void agentAlsoOptedInAsAWorkflowIsListedOnce_asTheAgent() throws Exception {
        deployAgent(OPTED_IN_AGENT, "Opted in twice");

        List<JsonNode> entries = listed(OPTED_IN_AGENT);
        assertEquals(1, entries.size(), entries.toString());
        assertEquals("Opted in twice", entries.get(0).get("description").asString());
        assertEquals("prompt", entries.get(0).get("inputSchema").get("required").get(0).asString());
    }

    @Test
    void agentWithoutADescriptionGetsTheNeutralOne_notTheCompilers() throws Exception {
        String agent = deployAgent(null);
        String tools = rpc("tools/list", mapper.createObjectNode(), null, false).toString();
        assertTrue(tools.contains("\"Conductor tool '" + agent + "'\""), tools);
        assertFalse(tools.contains("Agent workflow for " + agent), tools);
    }

    @Test
    void agentCallIsATaskThatIsPolledToTheAgentsAnswer() throws Exception {
        String agent = deployAgent(null);

        JsonNode created = callAgent(agent, "Where is order 42?");
        assertEquals("task", created.get("resultType").asString(), created.toString());
        String taskId = created.get("taskId").asString();
        assertEquals("working", created.get("status").asString());

        Workflow execution = executionService.getExecutionStatus(taskId, false);
        assertEquals(agent, execution.getWorkflowName());
        assertEquals("Where is order 42?", execution.getInput().get("prompt"));
        assertEquals("working", getTask(taskId).get("status").asString());

        completeLlm(awaitScheduledLlm(taskId), "Order 42 ships tomorrow.");

        JsonNode done = awaitTaskStatus(taskId, "completed");
        JsonNode result = done.get("result");
        assertFalse(result.get("isError").asBoolean(), done.toString());
        assertEquals(
                "Order 42 ships tomorrow.",
                result.get("structuredContent").get("result").asString());
    }

    @Test
    void agentSetsItsOwnSyncWait_inItsMetadata() throws Exception {
        String agent =
                deployAgent(
                        "mcp_agent_" + UUID.randomUUID().toString().replace('-', '_'),
                        null,
                        Map.of("mcp.syncWaitSeconds", 2));

        long started = System.nanoTime();
        JsonNode created = callAgent(agent, "Wait for me");
        long waitedMs = (System.nanoTime() - started) / 1_000_000;

        // The server default here is 0; the agent's own 2 s holds the call until it becomes a task.
        assertEquals("task", created.get("resultType").asString(), created.toString());
        assertTrue(waitedMs >= 1_500, "waited " + waitedMs + " ms");
    }

    @Test
    void agentCallCanBeCancelled() throws Exception {
        String agent = deployAgent(null);
        String taskId = callAgent(agent, "Take your time").get("taskId").asString();

        JsonNode ack = rpc("tasks/cancel", taskParams(taskId), taskId, true);
        assertEquals("complete", ack.get("result").get("resultType").asString(), ack.toString());

        assertEquals("cancelled", awaitTaskStatus(taskId, "cancelled").get("status").asString());
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                executionService.getExecutionStatus(taskId, false).getStatus());
    }

    @Test
    void agentCallWithoutAPromptIsInvalidParams_andStartsNothing() throws Exception {
        String agent = deployAgent(null);

        for (String arguments : List.of("{}", "{\"prompt\":\"  \"}", "{\"prompt\":7}")) {
            ObjectNode params = mapper.createObjectNode();
            params.put("name", agent);
            params.set("arguments", mapper.readTree(arguments));
            JsonNode response = rpc("tools/call", params, agent, true);
            assertEquals(-32602, response.get("error").get("code").asInt(), response.toString());
            assertTrue(
                    response.get("error").get("message").asString().contains("prompt"),
                    response.toString());
        }
        assertTrue(
                executionService.getRunningWorkflows(agent, 1).isEmpty(),
                "a rejected call starts no execution");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private String deployAgent(String description) {
        return deployAgent(
                "mcp_agent_" + UUID.randomUUID().toString().replace('-', '_'), description);
    }

    private String deployAgent(String name, String description) {
        return deployAgent(name, description, null);
    }

    private String deployAgent(String name, String description, Map<String, Object> metadata) {
        agentService.deploy(
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name(name)
                                        .description(description)
                                        .model("openai/gpt-4o-mini")
                                        .instructions("Answer briefly.")
                                        .metadata(metadata)
                                        .build())
                        .build());
        return name;
    }

    private List<JsonNode> listed(String name) throws Exception {
        List<JsonNode> entries = new ArrayList<>();
        rpc("tools/list", mapper.createObjectNode(), null, false)
                .get("result")
                .get("tools")
                .forEach(
                        tool -> {
                            if (name.equals(tool.get("name").asString())) {
                                entries.add(tool);
                            }
                        });
        return entries;
    }

    private JsonNode callAgent(String agent, String prompt) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", agent);
        params.putObject("arguments").put("prompt", prompt);
        JsonNode response = rpc("tools/call", params, agent, true);
        assertNotNull(response.get("result"), response.toString());
        return response.get("result");
    }

    private JsonNode getTask(String taskId) throws Exception {
        JsonNode response = rpc("tasks/get", taskParams(taskId), taskId, true);
        assertNotNull(response.get("result"), response.toString());
        return response.get("result");
    }

    private ObjectNode taskParams(String taskId) {
        ObjectNode params = mapper.createObjectNode();
        params.put("taskId", taskId);
        return params;
    }

    private JsonNode awaitTaskStatus(String taskId, String status) {
        AtomicReference<JsonNode> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTasksExceptLlm();
                            workflowExecutor.decide(taskId);
                            JsonNode task = getTask(taskId);
                            latest.set(task);
                            return status.equals(task.get("status").asString());
                        });
        return latest.get();
    }

    private JsonNode rpc(String method, ObjectNode params, String name, boolean declareTasks)
            throws Exception {
        ObjectNode meta = params.putObject("_meta");
        meta.put("io.modelcontextprotocol/protocolVersion", VERSION);
        ObjectNode capabilities = meta.putObject("io.modelcontextprotocol/clientCapabilities");
        if (declareTasks) {
            capabilities.putObject("extensions").putObject(TASKS);
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        body.set("params", params);

        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/mcp"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("MCP-Protocol-Version", VERSION)
                        .header("Mcp-Method", method)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (name != null) {
            request.header("Mcp-Name", name);
        }
        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    /** Runs the agent's non-LLM system tasks until its LLM step is scheduled. */
    private Task awaitScheduledLlm(String executionId) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            drainAsyncSystemTasksExceptLlm();
                            workflowExecutor.decide(executionId);
                            Workflow workflow =
                                    executionService.getExecutionStatus(executionId, true);
                            workflow.getTasks().stream()
                                    .filter(task -> LLM.equals(task.getTaskType()))
                                    .filter(task -> task.getStatus() == Task.Status.SCHEDULED)
                                    .reduce((first, second) -> second)
                                    .ifPresent(latest::set);
                            return latest.get() != null;
                        });
        return latest.get();
    }

    /** Completes the LLM step as a model that answers with {@code answer} and no tool calls. */
    private void completeLlm(Task llm, String answer) {
        TaskResult result = new TaskResult();
        result.setTaskId(llm.getTaskId());
        result.setWorkflowInstanceId(llm.getWorkflowInstanceId());
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(Map.of("finishReason", "STOP", "result", answer));
        workflowExecutor.updateTask(result);
    }

    private void drainAsyncSystemTasksExceptLlm() {
        for (WorkflowSystemTask task : asyncSystemTasks) {
            if (LLM.equals(task.getTaskType())) {
                continue;
            }
            for (String taskId : queueDAO.pop(task.getTaskType(), 5, 0)) {
                asyncSystemTaskExecutor.execute(task, taskId);
            }
        }
    }
}
