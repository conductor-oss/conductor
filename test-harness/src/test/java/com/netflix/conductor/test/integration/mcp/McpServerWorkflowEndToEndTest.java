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
import org.conductoross.conductor.service.SchemaService;
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
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
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
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opted-in workflows as MCP tools ({@code POST /api/mcp}), against a real engine on Redis: a long
 * workflow becomes an MCP task that is polled to completion or cancelled, and {@code CALL_MCP_TOOL}
 * (opted in to {@code 2026-07-28}) talks to this same server as an MCP client.
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
            "conductor.ai.outbound.allow-private-networks=true",
            "conductor.mcp.server.enabled=true",
            // Short, so the blocking-cap test does not wait 25 s.
            "conductor.mcp.server.blockingCapSeconds=2"
        })
class McpServerWorkflowEndToEndTest {

    private static final String VERSION = "2026-07-28";
    private static final String TASKS = "io.modelcontextprotocol/tasks";
    private static final String CALL_MCP_TOOL = "CALL_MCP_TOOL";
    private static final String OWNER = "mcp-server-e2e@conductor.test";

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
        registry.add("conductor.redis.workflow-namespace-prefix", () -> "mcp-server-e2e");
        registry.add("conductor.redis.queue-namespace-prefix", () -> "mcp-server-e2e");
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + REDIS.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> "redis://localhost:" + REDIS.getFirstMappedPort());
    }

    @LocalServerPort private int port;

    @Autowired private MetadataService metadataService;
    @Autowired private SchemaService schemaService;
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

    // ---- 6.1: the server against the engine -------------------------------------------------

    @Test
    void longWorkflowBecomesATaskThatIsPolledToCompletion() throws Exception {
        String tool = registerWaitingTool(1);

        JsonNode list = rpc("tools/list", mapper.createObjectNode(), null, true);
        assertTrue(
                list.get("result").get("tools").toString().contains("\"" + tool + "\""),
                "the opted-in workflow is listed");

        JsonNode created = callTool(tool, true);
        assertEquals("task", created.get("resultType").asString(), created.toString());
        String taskId = created.get("taskId").asString();
        assertEquals("working", created.get("status").asString());
        assertTrue(created.get("ttlMs").isIntegralNumber());
        assertNotNull(created.get("createdAt"));

        assertEquals("working", getTask(taskId).get("status").asString());

        completeWait(taskId, Map.of("answer", 42));

        JsonNode done = awaitTaskStatus(taskId, "completed");
        JsonNode result = done.get("result");
        assertFalse(result.get("isError").asBoolean(), done.toString());
        assertEquals(42, result.get("structuredContent").get("answer").asInt());
        assertEquals("complete", result.get("resultType").asString());
    }

    @Test
    void cancelTerminatesTheExecution() throws Exception {
        String tool = registerWaitingTool(0);
        String taskId = callTool(tool, true).get("taskId").asString();

        JsonNode ack = rpc("tasks/cancel", taskParams(taskId), taskId, true);
        assertEquals("complete", ack.get("result").get("resultType").asString(), ack.toString());
        assertEquals(1, ack.get("result").size());

        assertEquals("cancelled", awaitTaskStatus(taskId, "cancelled").get("status").asString());
        assertEquals(
                Workflow.WorkflowStatus.TERMINATED,
                executionService.getExecutionStatus(taskId, false).getStatus());

        JsonNode again = rpc("tasks/cancel", taskParams(taskId), taskId, true);
        assertEquals("complete", again.get("result").get("resultType").asString());
    }

    @Test
    void tasksUpdateAcksARunningTask_andChangesNothing() throws Exception {
        String tool = registerWaitingTool(0);
        String taskId = callTool(tool, true).get("taskId").asString();

        ObjectNode params = taskParams(taskId);
        params.putObject("inputResponses").putObject("unknown_key").put("action", "accept");
        JsonNode ack = rpc("tasks/update", params, taskId, true);
        assertEquals("complete", ack.get("result").get("resultType").asString(), ack.toString());
        assertEquals(1, ack.get("result").size());
        assertEquals("working", getTask(taskId).get("status").asString());

        String unknown = "no-such-execution-" + UUID.randomUUID();
        JsonNode error = rpc("tasks/update", taskParams(unknown), unknown, true).get("error");
        assertEquals(-32602, error.get("code").asInt(), String.valueOf(error));

        rpc("tasks/cancel", taskParams(taskId), taskId, true);
    }

    @Test
    void quickWorkflowAnswersInOneRoundTrip() throws Exception {
        String tool = registerQuickTool();
        JsonNode result = callTool(tool, true);
        assertEquals("complete", result.get("resultType").asString(), result.toString());
        assertFalse(result.get("isError").asBoolean());
        assertEquals("Hello, World!", result.get("structuredContent").get("greeting").asString());
        assertFalse(result.has("taskId"));
    }

    // ---- every method and outcome over HTTP ------------------------------------------------

    @Test
    void discoveryAdvertisesTheVersionToolsAndTasks() throws Exception {
        JsonNode result =
                rpc("server/discover", mapper.createObjectNode(), null, false).get("result");
        assertEquals(VERSION, result.get("supportedVersions").get(0).asString());
        assertTrue(result.get("capabilities").has("tools"));
        assertTrue(result.get("capabilities").get("extensions").has(TASKS));
        assertEquals(
                "conductor",
                result.get("_meta")
                        .get("io.modelcontextprotocol/serverInfo")
                        .get("name")
                        .asString());
    }

    @Test
    void toolsListHidesWorkflowsThatAreNotOptedIn() throws Exception {
        String hidden = registerQuickTool(false);
        String shown = registerQuickTool(true);
        String tools = rpc("tools/list", mapper.createObjectNode(), null, false).toString();
        assertTrue(tools.contains("\"" + shown + "\""));
        assertFalse(tools.contains("\"" + hidden + "\""));
    }

    // tool input schemas, from real metadata and schema stores

    private JsonNode listedSchema(String tool) throws Exception {
        for (JsonNode listed :
                rpc("tools/list", mapper.createObjectNode(), null, false)
                        .get("result")
                        .get("tools")) {
            if (tool.equals(listed.get("name").asString())) {
                return listed.get("inputSchema");
            }
        }
        throw new AssertionError("tool not listed: " + tool);
    }

    private String registerSchemaTool(
            SchemaDef inputSchema, List<String> inputParameters, boolean enforce) {
        WorkflowTask set = new WorkflowTask();
        set.setName("set");
        set.setTaskReferenceName("set");
        set.setType("SET_VARIABLE");
        set.setInputParameters(Map.of("seen", "${workflow.input.orderId}"));

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_server_schema_" + UUID.randomUUID().toString().replace("-", ""));
        def.setVersion(1);
        def.setOwnerEmail(OWNER);
        def.setTasks(List.of(set));
        def.setInputParameters(inputParameters);
        def.setInputSchema(inputSchema);
        def.setEnforceSchema(enforce);
        def.setOutputParameters(Map.of("orderId", "${workflow.input.orderId}"));
        def.setMetadata(Map.of("mcp.enabled", true));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    private SchemaDef saveSchema(SchemaDef.Type type, Map<String, Object> data) {
        SchemaDef schema = new SchemaDef();
        schema.setName("McpServerE2e_" + UUID.randomUUID().toString().replace("-", ""));
        schema.setVersion(1);
        schema.setType(type);
        schema.setData(data);
        SchemaDef stored = schemaService.saveSchema(schema, false);
        SchemaDef ref = new SchemaDef();
        ref.setName(stored.getName());
        ref.setVersion(stored.getVersion());
        return ref;
    }

    @Test
    void registeredJsonSchemaIsTheToolInputSchema_andAConformingCallRuns() throws Exception {
        SchemaDef ref =
                saveSchema(
                        SchemaDef.Type.JSON,
                        Map.of(
                                "type",
                                "object",
                                "properties",
                                Map.of("orderId", Map.of("type", "string")),
                                "required",
                                List.of("orderId")));
        String tool = registerSchemaTool(ref, List.of(), true);

        JsonNode schema = listedSchema(tool);
        assertEquals("object", schema.get("type").asString());
        assertEquals("orderId", schema.get("required").get(0).asString());
        assertEquals("string", schema.get("properties").get("orderId").get("type").asString());

        ObjectNode params = mapper.createObjectNode();
        params.put("name", tool);
        params.putObject("arguments").put("orderId", "o-1");
        JsonNode result = rpc("tools/call", params, tool, true).get("result");
        assertFalse(result.get("isError").asBoolean(), result.toString());
        assertEquals("o-1", result.get("structuredContent").get("orderId").asString());
    }

    @Test
    void nonJsonSchemaFallsBackToInputParameters() throws Exception {
        SchemaDef ref = saveSchema(SchemaDef.Type.AVRO, Map.of("type", "record"));
        JsonNode schema = listedSchema(registerSchemaTool(ref, List.of("orderId"), false));
        assertEquals("object", schema.get("type").asString());
        assertTrue(schema.get("properties").has("orderId"), schema.toString());
    }

    @Test
    void missingSchemaFallsBackToInputParameters() throws Exception {
        SchemaDef ref = new SchemaDef();
        ref.setName("McpServerE2eMissing_" + UUID.randomUUID().toString().replace("-", ""));
        ref.setVersion(1);
        JsonNode schema = listedSchema(registerSchemaTool(ref, List.of("orderId"), false));
        assertTrue(schema.get("properties").has("orderId"), schema.toString());
    }

    @Test
    void inputParametersOnlyAndNoSchemaAtAll() throws Exception {
        JsonNode withParams =
                listedSchema(registerSchemaTool(null, List.of("orderId", "region"), false));
        assertTrue(withParams.get("properties").has("orderId"));
        assertTrue(withParams.get("properties").has("region"));
        assertFalse(withParams.has("required"));

        JsonNode bare = listedSchema(registerSchemaTool(null, List.of(), false));
        assertEquals(1, bare.size(), bare.toString());
        assertEquals("object", bare.get("type").asString());
    }

    @Test
    void definitionDescriptionIsTheToolDescription_elseANeutralDefault() throws Exception {
        String plain = registerQuickTool(true);
        WorkflowDef described = metadataService.getWorkflowDef(plain, 1);
        described.setDescription("Greets a person");
        metadataService.updateWorkflowDef(List.of(described));
        String other = registerQuickTool(true);

        JsonNode tools =
                rpc("tools/list", mapper.createObjectNode(), null, false)
                        .get("result")
                        .get("tools");
        for (JsonNode listed : tools) {
            String name = listed.get("name").asString();
            if (name.equals(plain)) {
                assertEquals("Greets a person", listed.get("description").asString());
            } else if (name.equals(other)) {
                assertEquals(
                        "Conductor tool '" + other + "'", listed.get("description").asString());
            }
        }
    }

    @Test
    void quickFailureIsAnErrorResult() throws Exception {
        String tool = registerFailingTool();
        JsonNode result = callTool(tool, true);
        assertEquals("complete", result.get("resultType").asString(), result.toString());
        assertTrue(result.get("isError").asBoolean());
        String text = result.get("content").get(0).get("text").asString();
        assertTrue(text.contains("FAILED") && text.contains("card declined"), text);
        assertFalse(text.toLowerCase().contains("workflow"), text);
    }

    @Test
    void engineTerminateTextDoesNotReachTheClient() throws Exception {
        // A TERMINATE task with no terminationReason: the engine writes
        // "Workflow is FAILED by TERMINATE task: <id>" (WorkflowExecutorOps).
        String failing = registerTerminateTool("FAILED");
        JsonNode result = callTool(failing, true);
        String text = result.get("content").get(0).get("text").asString();
        assertTrue(result.get("isError").asBoolean(), result.toString());
        assertTrue(text.contains("Stopped by a TERMINATE step with status FAILED"), text);
        assertFalse(text.toLowerCase().contains("workflow"), text);

        String terminating = registerTerminateTool("TERMINATED");
        String taskId = callTool(registerWaitingTool(0), true).get("taskId").asString();
        workflowExecutor.terminateWorkflow(taskId, "Error when restarting the workflow");
        assertEquals(
                "Error when restarting the call",
                awaitTaskStatus(taskId, "cancelled").get("statusMessage").asString());

        JsonNode cancelled = callTool(terminating, true);
        assertFalse(cancelled.toString().toLowerCase().contains("workflow"), cancelled.toString());
    }

    @Test
    void engineTimeoutTextDoesNotReachTheClient() throws Exception {
        String tool = registerWaitingTool(0, 1);
        String taskId = callTool(tool, true).get("taskId").asString();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(taskId);
                            return executionService.getExecutionStatus(taskId, false).getStatus()
                                    == Workflow.WorkflowStatus.TIMED_OUT;
                        });
        String engineReason =
                executionService.getExecutionStatus(taskId, false).getReasonForIncompletion();
        assertTrue(engineReason.contains("Workflow"), "precondition: " + engineReason);

        JsonNode done = awaitTaskStatus(taskId, "completed");
        String text = done.get("result").get("content").get(0).get("text").asString();
        assertTrue(done.get("result").get("isError").asBoolean());
        assertTrue(text.startsWith("The call timed out"), text);
        assertFalse(text.toLowerCase().contains("workflow"), text);
    }

    @Test
    void longFailureIsACompletedTaskWithAnErrorResult() throws Exception {
        String tool = registerWaitingTool(0);
        String taskId = callTool(tool, true).get("taskId").asString();
        finishWait(taskId, TaskResult.Status.FAILED, Map.of());

        JsonNode done = awaitTaskStatus(taskId, "completed");
        assertTrue(done.get("result").get("isError").asBoolean(), done.toString());
        assertFalse(done.has("error"), "a tool error is not a protocol error");
    }

    @Test
    void executionTerminatedOutsideMcpIsCancelled() throws Exception {
        String tool = registerWaitingTool(0);
        String taskId = callTool(tool, true).get("taskId").asString();
        workflowExecutor.terminateWorkflow(taskId, "stopped by operator");

        JsonNode task = awaitTaskStatus(taskId, "cancelled");
        assertEquals("stopped by operator", task.get("statusMessage").asString());
    }

    @Test
    void cancelOfAFinishedTaskChangesNothing() throws Exception {
        String tool = registerWaitingTool(0);
        String taskId = callTool(tool, true).get("taskId").asString();
        completeWait(taskId, Map.of("answer", 1));
        awaitTaskStatus(taskId, "completed");

        JsonNode ack = rpc("tasks/cancel", taskParams(taskId), taskId, true);
        assertEquals("complete", ack.get("result").get("resultType").asString());
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                executionService.getExecutionStatus(taskId, false).getStatus());
    }

    @Test
    void longCallWithoutTasksBlocksToTheCap_andTheExecutionKeepsRunning() throws Exception {
        String tool = registerWaitingTool(0);
        long before = System.currentTimeMillis();
        JsonNode result = callTool(tool, false);
        assertTrue(System.currentTimeMillis() - before >= 1500, "waits up to the 2 s cap");
        assertEquals("complete", result.get("resultType").asString(), result.toString());
        assertTrue(result.get("isError").asBoolean());
        String text = result.get("content").get(0).get("text").asString();
        assertTrue(text.startsWith("The call is still running. Execution id: "), text);
        String executionId = text.substring(text.lastIndexOf(' ') + 1);
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                executionService.getExecutionStatus(executionId, false).getStatus());
    }

    @Test
    void unknownOrHiddenToolIsInvalidParams() throws Exception {
        String hidden = registerQuickTool(false);
        for (String name : new String[] {"no_such_tool_" + UUID.randomUUID(), hidden}) {
            ObjectNode params = mapper.createObjectNode();
            params.put("name", name);
            JsonNode error = rpc("tools/call", params, name, true).get("error");
            assertEquals(-32602, error.get("code").asInt(), name);
        }
    }

    /**
     * This server runs without embedded AgentSpan, so there is no agent source. An agent definition
     * in the store (from a standalone AgentSpan, say) is not a workflow tool even when opted in:
     * its compiler-written description would name the kind, and it is started with a prompt.
     */
    @Test
    void agentDefinitionIsNotAWorkflowTool_evenWhenOptedIn() throws Exception {
        String agent = registerQuickTool(true);
        WorkflowDef def = metadataService.getWorkflowDef(agent, 1);
        def.setDescription("Agent workflow for " + agent);
        def.setMetadata(Map.of("mcp.enabled", true, "agent_sdk", "conductor"));
        metadataService.updateWorkflowDef(List.of(def));

        String tools = rpc("tools/list", mapper.createObjectNode(), null, false).toString();
        assertFalse(tools.contains(agent), tools);

        ObjectNode params = mapper.createObjectNode();
        params.put("name", agent);
        params.putObject("arguments").put("name", "World");
        JsonNode error = rpc("tools/call", params, agent, true).get("error");
        assertEquals(-32602, error.get("code").asInt(), String.valueOf(error));
        assertTrue(executionService.getRunningWorkflows(agent, 1).isEmpty());
    }

    @Test
    void unknownTaskIsInvalidParams() throws Exception {
        String taskId = "nonexistent-" + UUID.randomUUID();
        for (String method : new String[] {"tasks/get", "tasks/cancel"}) {
            JsonNode error = rpc(method, taskParams(taskId), taskId, true).get("error");
            assertEquals(-32602, error.get("code").asInt(), method);
        }
    }

    @Test
    void taskMethodsWithoutTheExtensionAreRejected() throws Exception {
        String tool = registerWaitingTool(0);
        String taskId = callTool(tool, true).get("taskId").asString();
        for (String method : new String[] {"tasks/get", "tasks/cancel"}) {
            Raw response =
                    post(method, request(method, taskParams(taskId), false), taskId, VERSION);
            assertEquals(400, response.status(), method);
            JsonNode error = response.body().get("error");
            assertEquals(-32021, error.get("code").asInt(), method);
            assertTrue(error.get("data").get("requiredCapabilities").get("extensions").has(TASKS));
        }
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                executionService.getExecutionStatus(taskId, false).getStatus(),
                "a rejected cancel does not terminate");
    }

    @Test
    void protocolErrorsUseTheirHttpStatus() throws Exception {
        // Missing _meta: 400, -32602.
        ObjectNode noMeta = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1);
        noMeta.put("method", "tools/list");
        noMeta.putObject("params");
        Raw missingMeta = post("tools/list", noMeta, null, VERSION);
        assertEquals(400, missingMeta.status());
        assertEquals(-32602, missingMeta.body().get("error").get("code").asInt());

        // Unsupported version: 400, -32022 with supported and requested.
        ObjectNode old = request("tools/list", mapper.createObjectNode(), false);
        ((ObjectNode) old.get("params").get("_meta"))
                .put("io.modelcontextprotocol/protocolVersion", "v999.0.0");
        Raw unsupported = post("tools/list", old, null, "v999.0.0");
        assertEquals(400, unsupported.status());
        JsonNode data = unsupported.body().get("error").get("data");
        assertEquals(-32022, unsupported.body().get("error").get("code").asInt());
        assertEquals(VERSION, data.get("supported").get(0).asString());
        assertEquals("v999.0.0", data.get("requested").asString());

        // Missing protocol version header: 400, -32020.
        Raw noHeader =
                post(
                        "tools/list",
                        request("tools/list", mapper.createObjectNode(), false),
                        null,
                        null);
        assertEquals(400, noHeader.status());
        assertEquals(-32020, noHeader.body().get("error").get("code").asInt());

        // Removed and unknown methods: 404, -32601.
        for (String method : new String[] {"initialize", "ping", "resources/list"}) {
            Raw removed =
                    post(method, request(method, mapper.createObjectNode(), true), null, VERSION);
            assertEquals(404, removed.status(), method);
            assertEquals(-32601, removed.body().get("error").get("code").asInt(), method);
        }

        // Notification: 202, no body.
        ObjectNode notification = mapper.createObjectNode().put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        Raw accepted = post("notifications/initialized", notification, null, VERSION);
        assertEquals(202, accepted.status());

        // GET: 405.
        HttpResponse<String> get =
                http.send(
                        HttpRequest.newBuilder(URI.create(url())).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode());
    }

    @Test
    void nameHeaderMismatchStartsNothing() throws Exception {
        String tool = registerQuickTool(true);
        ObjectNode params = mapper.createObjectNode();
        params.put("name", tool);
        Raw response =
                post("tools/call", request("tools/call", params, true), "some_other_tool", VERSION);
        assertEquals(400, response.status());
        assertEquals(-32020, response.body().get("error").get("code").asInt());
    }

    // ---- 6.2: CALL_MCP_TOOL as the client of this server -----------------------------------

    @Test
    void callMcpToolGetsAQuickResultFromThisServer() {
        String tool = registerQuickTool();
        String caller = registerCaller(tool, Map.of("name", "World"));
        Workflow done = awaitTerminal(startWorkflow(caller));

        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus(), describe(done));
        Task call = mcpTask(done);
        assertEquals(Task.Status.COMPLETED, call.getStatus());
        assertTrue(call.getOutputData().toString().contains("Hello, World!"), describe(done));
        assertFalse(call.getOutputData().containsKey("mcpTask"), "no remote task was needed");
    }

    @Test
    void callMcpToolPollsALongCallOfThisServerToCompletion() {
        String tool = registerWaitingTool(0);
        String caller = registerCaller(tool, Map.of());
        String callerId = startWorkflow(caller);

        Task polling = awaitPolling(callerId);
        String remoteTaskId = remoteTaskId(polling);
        assertNotNull(remoteTaskId, "the server created a task, and the client keeps its id");

        completeWait(remoteTaskId, Map.of("answer", 7));

        Workflow done = awaitTerminal(callerId);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus(), describe(done));
        Task call = mcpTask(done);
        assertEquals(Task.Status.COMPLETED, call.getStatus());
        assertTrue(call.getOutputData().toString().contains("7"), describe(done));
    }

    @Test
    void callMcpToolSeesAToolErrorAsAnErrorResult() {
        String tool = registerFailingTool();
        Workflow done = awaitTerminal(startWorkflow(registerCaller(tool, Map.of())));
        Task call = mcpTask(done);
        assertEquals(Task.Status.COMPLETED, call.getStatus(), describe(done));
        assertEquals(Boolean.TRUE, call.getOutputData().get("isError"), describe(done));
    }

    @Test
    void terminatingTheCallerCancelsTheServerExecution() {
        String tool = registerWaitingTool(0);
        String callerId = startWorkflow(registerCaller(tool, Map.of()));
        String remoteTaskId = remoteTaskId(awaitPolling(callerId));

        workflowExecutor.terminateWorkflow(callerId, "caller stopped");

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () ->
                                executionService.getExecutionStatus(remoteTaskId, false).getStatus()
                                        == Workflow.WorkflowStatus.TERMINATED);
    }

    // ---- existing (legacy) clients ----------------------------------------------------------

    private Raw legacy(String method, ObjectNode params, String versionHeader) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        body.set("params", params);
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(url()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (versionHeader != null) {
            request.header("MCP-Protocol-Version", versionHeader);
        }
        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertFalse(response.headers().firstValue("Mcp-Session-Id").isPresent(), "no session");
        return new Raw(response.statusCode(), mapper.readTree(response.body()));
    }

    @Test
    void legacyClientInitializesListsAndCalls() throws Exception {
        String tool = registerQuickTool(true);
        ObjectNode init = mapper.createObjectNode().put("protocolVersion", "2025-06-18");
        init.putObject("capabilities");
        init.putObject("clientInfo").put("name", "legacy-client").put("version", "1");
        JsonNode initialized = legacy("initialize", init, null).body().get("result");
        assertEquals("2025-06-18", initialized.get("protocolVersion").asString());

        String tools =
                legacy("tools/list", mapper.createObjectNode(), "2025-06-18").body().toString();
        assertTrue(tools.contains("\"" + tool + "\""));

        ObjectNode call = mapper.createObjectNode().put("name", tool);
        call.putObject("arguments").put("name", "World");
        JsonNode result = legacy("tools/call", call, "2025-06-18").body().get("result");
        assertFalse(result.has("resultType"), result.toString());
        assertFalse(result.get("isError").asBoolean());
        assertEquals("Hello, World!", result.get("structuredContent").get("greeting").asString());
    }

    @Test
    void legacyLongCallBlocksToTheCap_withoutATask() throws Exception {
        String tool = registerWaitingTool(0);
        long before = System.currentTimeMillis();
        JsonNode result =
                legacy("tools/call", mapper.createObjectNode().put("name", tool), null)
                        .body()
                        .get("result");
        assertTrue(System.currentTimeMillis() - before >= 1500);
        assertTrue(result.get("isError").asBoolean(), result.toString());
        assertFalse(result.has("taskId"));
        assertFalse(result.has("resultType"));
    }

    @Test
    void legacyUnsupportedVersionHeaderIs400() throws Exception {
        assertEquals(400, legacy("tools/list", mapper.createObjectNode(), "2024-01-01").status());
    }

    private Raw legacyRaw(String body, String versionHeader) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(url()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (versionHeader != null) {
            request.header("MCP-Protocol-Version", versionHeader);
        }
        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Raw(
                response.statusCode(),
                response.body() == null || response.body().isEmpty()
                        ? null
                        : mapper.readTree(response.body()));
    }

    private ObjectNode legacyCall(String tool, String argumentsJson) {
        ObjectNode call = mapper.createObjectNode().put("name", tool);
        if (argumentsJson != null) {
            call.set("arguments", mapper.readTree(argumentsJson));
        }
        return call;
    }

    // happy paths

    @Test
    void legacyHappyPathForEverySupportedVersion() throws Exception {
        String tool = registerQuickTool(true);
        for (String version : new String[] {"2025-03-26", "2025-06-18", "2025-11-25"}) {
            ObjectNode init = mapper.createObjectNode().put("protocolVersion", version);
            init.putObject("capabilities");
            init.putObject("clientInfo").put("name", "legacy-" + version);
            Raw initialized = legacy("initialize", init, null);
            assertEquals(200, initialized.status(), version);
            assertEquals(
                    version,
                    initialized.body().get("result").get("protocolVersion").asString(),
                    version);

            Raw list = legacy("tools/list", mapper.createObjectNode(), version);
            assertEquals(200, list.status(), version);
            assertTrue(list.body().toString().contains("\"" + tool + "\""), version);

            Raw call = legacy("tools/call", legacyCall(tool, "{\"name\":\"World\"}"), version);
            assertEquals(200, call.status(), version);
            JsonNode result = call.body().get("result");
            assertFalse(result.get("isError").asBoolean(), version);
            assertEquals(
                    "Hello, World!",
                    result.get("structuredContent").get("greeting").asString(),
                    version);
            assertFalse(result.has("resultType"), version);
        }
    }

    @Test
    void legacyCallWithoutHeaderOrInitialize() throws Exception {
        String tool = registerQuickTool(true);
        JsonNode result =
                legacy("tools/call", legacyCall(tool, "{\"name\":\"World\"}"), null)
                        .body()
                        .get("result");
        assertFalse(result.get("isError").asBoolean(), result.toString());
    }

    @Test
    void legacyInitializeWithAnUnsupportedVersionGetsTheNewest() throws Exception {
        for (String requested : new String[] {"2024-11-05", "2099-01-01"}) {
            ObjectNode init = mapper.createObjectNode().put("protocolVersion", requested);
            init.putObject("capabilities");
            JsonNode result = legacy("initialize", init, null).body().get("result");
            assertEquals("2025-11-25", result.get("protocolVersion").asString(), requested);
            assertTrue(result.get("capabilities").has("tools"));
            assertEquals("conductor", result.get("serverInfo").get("name").asString());
        }
    }

    @Test
    void legacyPingAndNotification() throws Exception {
        Raw ping = legacy("ping", mapper.createObjectNode(), "2025-06-18");
        assertEquals(200, ping.status());
        assertEquals(0, ping.body().get("result").size());

        Raw notification =
                legacyRaw(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                        "2025-06-18");
        assertEquals(202, notification.status());
    }

    // bad cases

    @Test
    void legacyToolFailureIsAnErrorResult() throws Exception {
        String tool = registerFailingTool();
        JsonNode result = legacy("tools/call", legacyCall(tool, "{}"), null).body().get("result");
        assertTrue(result.get("isError").asBoolean(), result.toString());
        assertTrue(result.get("content").get(0).get("text").asString().contains("card declined"));
        assertFalse(result.has("resultType"));
    }

    @Test
    void legacyUnknownOrHiddenToolIsInvalidParams() throws Exception {
        String hidden = registerQuickTool(false);
        for (String name : new String[] {"no_such_tool_" + UUID.randomUUID(), hidden}) {
            Raw response = legacy("tools/call", legacyCall(name, "{}"), "2025-06-18");
            assertEquals(200, response.status(), name);
            assertEquals(-32602, response.body().get("error").get("code").asInt(), name);
        }
    }

    @Test
    void legacyNonObjectArgumentsIsInvalidParams() throws Exception {
        String tool = registerQuickTool(true);
        Raw response = legacy("tools/call", legacyCall(tool, "[1, 2]"), "2025-06-18");
        assertEquals(200, response.status());
        assertEquals(-32602, response.body().get("error").get("code").asInt());
    }

    @Test
    void legacyTaskAndDiscoverMethodsAreNotFound() throws Exception {
        for (String method : new String[] {"tasks/get", "tasks/cancel", "server/discover"}) {
            ObjectNode params = mapper.createObjectNode().put("taskId", "x");
            Raw response = legacy(method, params, "2025-11-25");
            assertEquals(200, response.status(), method);
            assertEquals(-32601, response.body().get("error").get("code").asInt(), method);
        }
    }

    @Test
    void legacyMalformedJsonIsAParseError() throws Exception {
        Raw response = legacyRaw("{not json", "2025-06-18");
        assertEquals(400, response.status());
        assertEquals(-32700, response.body().get("error").get("code").asInt());
    }

    @Test
    void callMcpToolWithoutMcpOptionsSeesAToolErrorAsAnErrorResult() {
        String tool = registerFailingTool();
        Workflow done = awaitTerminal(startWorkflow(registerCaller(tool, Map.of(), false)));
        Task call = mcpTask(done);
        assertEquals(Task.Status.COMPLETED, call.getStatus(), describe(done));
        assertEquals(Boolean.TRUE, call.getOutputData().get("isError"), describe(done));
    }

    @Test
    void callMcpToolWithoutMcpOptionsGetsStillRunningAtTheCap_withoutATask() {
        String tool = registerWaitingTool(0);
        Workflow done = awaitTerminal(startWorkflow(registerCaller(tool, Map.of(), false)));
        Task call = mcpTask(done);
        assertEquals(Task.Status.COMPLETED, call.getStatus(), describe(done));
        assertEquals(Boolean.TRUE, call.getOutputData().get("isError"), describe(done));
        assertTrue(call.getOutputData().toString().contains("still running"), describe(done));
        assertFalse(
                call.getOutputData().containsKey("mcpTask"), "a legacy client never gets a task");
    }

    @Test
    void callMcpToolWithoutMcpOptionsWorksAgainstThisServer() {
        String tool = registerQuickTool(true);
        String caller = registerCaller(tool, Map.of("name", "World"), false);
        Workflow done = awaitTerminal(startWorkflow(caller));
        assertEquals(Workflow.WorkflowStatus.COMPLETED, done.getStatus(), describe(done));
        Task call = mcpTask(done);
        assertTrue(call.getOutputData().toString().contains("Hello, World!"), describe(done));
    }

    // ---- MCP over HTTP ------------------------------------------------------------------------

    private JsonNode callTool(String tool, boolean declareTasks) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", tool);
        params.putObject("arguments").put("name", "World");
        JsonNode response = rpc("tools/call", params, tool, declareTasks);
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
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(taskId);
                            JsonNode task = getTask(taskId);
                            latest.set(task);
                            return status.equals(task.get("status").asString());
                        });
        return latest.get();
    }

    private JsonNode rpc(String method, ObjectNode params, String name, boolean declareTasks)
            throws Exception {
        return post(method, request(method, params, declareTasks), name, VERSION).body();
    }

    /** A {@code 2026-07-28} request with {@code _meta}. */
    private ObjectNode request(String method, ObjectNode params, boolean declareTasks) {
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
        return body;
    }

    private record Raw(int status, JsonNode body) {}

    /**
     * POSTs a body with {@code Mcp-Method} = {@code method}, an optional {@code Mcp-Name}, and an
     * optional {@code MCP-Protocol-Version}. A response with a body must be JSON, never SSE.
     */
    private Raw post(String method, JsonNode body, String name, String versionHeader)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(url()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Mcp-Method", method)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (versionHeader != null) {
            request.header("MCP-Protocol-Version", versionHeader);
        }
        if (name != null) {
            request.header("Mcp-Name", name);
        }
        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.body() == null || response.body().isEmpty()) {
            return new Raw(response.statusCode(), null);
        }
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .startsWith("application/json"),
                "JSON only, no SSE");
        return new Raw(response.statusCode(), mapper.readTree(response.body()));
    }

    private String url() {
        return "http://localhost:" + port + "/api/mcp";
    }

    // ---- engine helpers -----------------------------------------------------------------------

    private String registerWaitingTool(int syncWaitSeconds) {
        return registerWaitingTool(syncWaitSeconds, 0);
    }

    /**
     * A workflow that waits on a WAIT task (no duration) until the test completes it; with {@code
     * timeoutSeconds} above 0 it times out instead (TIME_OUT_WF).
     */
    private String registerWaitingTool(int syncWaitSeconds, long timeoutSeconds) {
        WorkflowTask wait = new WorkflowTask();
        wait.setName("wait");
        wait.setTaskReferenceName("wait");
        wait.setType("WAIT");

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_server_wait_" + UUID.randomUUID().toString().replace("-", ""));
        def.setVersion(1);
        def.setOwnerEmail(OWNER);
        def.setTasks(List.of(wait));
        def.setOutputParameters(Map.of("answer", "${wait.output.answer}"));
        def.setMetadata(Map.of("mcp.enabled", true, "mcp.syncWaitSeconds", syncWaitSeconds));
        if (timeoutSeconds > 0) {
            def.setTimeoutSeconds(timeoutSeconds);
            def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        }
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    private String registerQuickTool() {
        return registerQuickTool(true);
    }

    /** A TERMINATE task with the given status and no terminationReason. */
    private String registerTerminateTool(String status) {
        WorkflowTask terminate = new WorkflowTask();
        terminate.setName("terminate");
        terminate.setTaskReferenceName("terminate");
        terminate.setType("TERMINATE");
        terminate.setInputParameters(Map.of("terminationStatus", status));

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_server_terminate_" + UUID.randomUUID().toString().replace("-", ""));
        def.setVersion(1);
        def.setOwnerEmail(OWNER);
        def.setTasks(List.of(terminate));
        def.setMetadata(Map.of("mcp.enabled", true));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    /** A workflow that ends as FAILED in its first decide (TERMINATE with FAILED). */
    private String registerFailingTool() {
        WorkflowTask terminate = new WorkflowTask();
        terminate.setName("terminate");
        terminate.setTaskReferenceName("terminate");
        terminate.setType("TERMINATE");
        terminate.setInputParameters(
                Map.of("terminationStatus", "FAILED", "terminationReason", "card declined"));

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_server_failing_" + UUID.randomUUID().toString().replace("-", ""));
        def.setVersion(1);
        def.setOwnerEmail(OWNER);
        def.setTasks(List.of(terminate));
        def.setMetadata(Map.of("mcp.enabled", true));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    /** A workflow that ends in its first decide (SET_VARIABLE only). */
    private String registerQuickTool(boolean optedIn) {
        WorkflowTask set = new WorkflowTask();
        set.setName("set");
        set.setTaskReferenceName("set");
        set.setType("SET_VARIABLE");
        set.setInputParameters(Map.of("seen", "${workflow.input.name}"));

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_server_quick_" + UUID.randomUUID().toString().replace("-", ""));
        def.setVersion(1);
        def.setOwnerEmail(OWNER);
        def.setInputParameters(List.of("name"));
        def.setTasks(List.of(set));
        def.setOutputParameters(Map.of("greeting", "Hello, ${workflow.input.name}!"));
        def.setMetadata(Map.of("mcp.enabled", optedIn));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    /** A workflow whose CALL_MCP_TOOL step calls {@code tool} on this server. */
    private String registerCaller(String tool, Map<String, Object> arguments) {
        return registerCaller(tool, arguments, true);
    }

    private String registerCaller(String tool, Map<String, Object> arguments, boolean optIn) {
        String taskName = "mcp_server_call_" + UUID.randomUUID();
        TaskDef taskDef = new TaskDef();
        taskDef.setName(taskName);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(120);
        taskDef.setResponseTimeoutSeconds(60);
        taskDef.setOwnerEmail(OWNER);
        metadataService.registerTaskDef(List.of(taskDef));

        WorkflowTask call = new WorkflowTask();
        call.setName(taskName);
        call.setTaskReferenceName("call");
        call.setType(CALL_MCP_TOOL);
        Map<String, Object> input = new HashMap<>();
        input.put("mcpServer", "http://localhost:" + port + "/api/mcp");
        input.put("method", tool);
        input.put("arguments", arguments);
        if (optIn) {
            input.put("mcpOptions", Map.of("protocolVersion", VERSION));
        }
        call.setInputParameters(input);

        WorkflowDef def = new WorkflowDef();
        def.setName("mcp_server_caller_" + UUID.randomUUID().toString().replace("-", ""));
        def.setVersion(1);
        def.setOwnerEmail(OWNER);
        def.setTasks(List.of(call));
        metadataService.updateWorkflowDef(List.of(def));
        return def.getName();
    }

    private String startWorkflow(String name) {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setName(name);
        input.setVersion(1);
        input.setWorkflowInput(new HashMap<>());
        return workflowExecutor.startWorkflow(input);
    }

    private void completeWait(String workflowId, Map<String, Object> output) {
        finishWait(workflowId, TaskResult.Status.COMPLETED, output);
    }

    private void finishWait(
            String workflowId, TaskResult.Status status, Map<String, Object> output) {
        AtomicReference<Task> wait = new AtomicReference<>();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Workflow wf = executionService.getExecutionStatus(workflowId, true);
                            wf.getTasks().stream()
                                    .filter(t -> "WAIT".equals(t.getTaskType()))
                                    .filter(t -> t.getStatus() == Task.Status.IN_PROGRESS)
                                    .findFirst()
                                    .ifPresent(wait::set);
                            return wait.get() != null;
                        });
        TaskResult result = new TaskResult(wait.get());
        result.setStatus(status);
        result.setOutputData(new HashMap<>(output));
        workflowExecutor.updateTask(result);
        workflowExecutor.decide(workflowId);
    }

    private Task awaitPolling(String workflowId) {
        AtomicReference<Task> latest = new AtomicReference<>();
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            workflowExecutor.decide(workflowId);
                            drainQueue();
                            Task task =
                                    mcpTask(executionService.getExecutionStatus(workflowId, true));
                            latest.set(task);
                            return task != null
                                    && task.getStatus() == Task.Status.IN_PROGRESS
                                    && remoteTaskId(task) != null;
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
                        .orElseThrow();
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

    private static String describe(Workflow workflow) {
        Task task = mcpTask(workflow);
        return "workflow "
                + workflow.getStatus()
                + " ("
                + workflow.getReasonForIncompletion()
                + "), call task "
                + (task == null
                        ? "none"
                        : task.getStatus()
                                + " "
                                + task.getReasonForIncompletion()
                                + " "
                                + task.getOutputData());
    }
}
