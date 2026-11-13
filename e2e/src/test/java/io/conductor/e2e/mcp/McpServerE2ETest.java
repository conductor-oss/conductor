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
package io.conductor.e2e.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.Commons;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MCP server ({@code POST /api/mcp}) of the packaged server, over HTTP: every method and
 * outcome for {@code 2026-07-28} clients, and the happy paths and bad cases for {@code 2025-xx}
 * clients. The e2e compose files enable the server with a 3 s blocking cap.
 */
@DisplayName("MCP server")
public class McpServerE2ETest {

    private static final String VERSION = "2026-07-28";
    private static final String TASKS = "io.modelcontextprotocol/tasks";
    private static final String MCP_URL = ApiUtil.SERVER_ROOT_URI + "/mcp";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private record Raw(int status, JsonNode body, HttpResponse<String> response) {}

    // ---- 2026-07-28: discovery and listing --------------------------------------------------

    @Test
    void discoveryAdvertisesTheVersionToolsAndTasks() throws Exception {
        JsonNode result =
                rpc("server/discover", mapper.createObjectNode(), null, false).get("result");
        assertEquals(VERSION, result.get("supportedVersions").get(0).asText());
        assertTrue(result.get("capabilities").has("tools"));
        assertTrue(result.get("capabilities").get("extensions").has(TASKS));
        assertEquals(
                "conductor",
                result.get("_meta").get("io.modelcontextprotocol/serverInfo").get("name").asText());
    }

    @Test
    void toolsListShowsOptedInWorkflowsOnly() throws Exception {
        String shown = registerQuickTool(true);
        String hidden = registerQuickTool(false);
        String tools = rpc("tools/list", mapper.createObjectNode(), null, false).toString();
        assertTrue(tools.contains("\"" + shown + "\""));
        assertFalse(tools.contains("\"" + hidden + "\""));
    }

    // ---- 2026-07-28: tools/call -------------------------------------------------------------

    @Test
    void quickSuccessAnswersInOneRoundTrip() throws Exception {
        JsonNode result = callTool(registerQuickTool(true), "{\"name\":\"World\"}", true);
        assertEquals("complete", result.get("resultType").asText(), result.toString());
        assertFalse(result.get("isError").asBoolean());
        assertEquals("Hello, World!", result.get("structuredContent").get("greeting").asText());
        assertFalse(result.has("taskId"));
    }

    @Test
    void quickFailureIsAnErrorResult() throws Exception {
        JsonNode result = callTool(registerFailingTool(), "{}", true);
        assertTrue(result.get("isError").asBoolean(), result.toString());
        String text = result.get("content").get(0).get("text").asText();
        assertTrue(text.contains("card declined"), text);
        assertFalse(text.toLowerCase().contains("workflow"), text);
    }

    @Test
    void longCallWithTasksBecomesATask_polledToCompletion() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        assertEquals("working", getTask(taskId).get("status").asText());

        finishWait(taskId, TaskResult.Status.COMPLETED, Map.of("answer", 42));

        JsonNode done = awaitTaskStatus(taskId, "completed");
        assertFalse(done.get("result").get("isError").asBoolean(), done.toString());
        assertEquals(42, done.get("result").get("structuredContent").get("answer").asInt());
    }

    @Test
    void longCallWithoutTasksBlocksToTheCap_andKeepsRunning() throws Exception {
        String tool = registerWaitingTool(0, 0);
        long before = System.currentTimeMillis();
        JsonNode result = callTool(tool, "{}", false);
        assertTrue(System.currentTimeMillis() - before >= 2500, "waits up to the 3 s cap");
        assertTrue(result.get("isError").asBoolean(), result.toString());
        String text = result.get("content").get(0).get("text").asText();
        assertTrue(text.startsWith("The call is still running. Execution id: "), text);
        String executionId = text.substring(text.lastIndexOf(' ') + 1);
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                ApiUtil.WORKFLOW_CLIENT.getWorkflow(executionId, false).getStatus());
        ApiUtil.WORKFLOW_CLIENT.terminateWorkflow(executionId, "e2e cleanup");
    }

    @Test
    void unknownOrHiddenToolIsInvalidParams() throws Exception {
        String hidden = registerQuickTool(false);
        for (String name : new String[] {"no_such_tool_" + UUID.randomUUID(), hidden}) {
            ObjectNode params = mapper.createObjectNode().put("name", name);
            assertEquals(
                    -32602, rpc("tools/call", params, name, true).get("error").get("code").asInt());
        }
    }

    // ---- 2026-07-28: tasks/get and tasks/cancel ----------------------------------------------

    @Test
    void longFailureIsACompletedTaskWithAnErrorResult() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        finishWait(taskId, TaskResult.Status.FAILED, Map.of());
        JsonNode done = awaitTaskStatus(taskId, "completed");
        assertTrue(done.get("result").get("isError").asBoolean(), done.toString());
        assertFalse(done.has("error"));
    }

    @Test
    void timeoutIsReportedInToolTerms() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 1));
        JsonNode done = awaitTaskStatus(taskId, "completed");
        String text = done.get("result").get("content").get(0).get("text").asText();
        assertTrue(text.startsWith("The call timed out"), text);
        assertFalse(text.toLowerCase().contains("workflow"), text);
    }

    @Test
    void cancelOfARunningTaskTerminatesIt() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        JsonNode ack = rpc("tasks/cancel", taskParams(taskId), taskId, true).get("result");
        assertEquals("complete", ack.get("resultType").asText());
        assertEquals(1, ack.size());
        assertEquals("cancelled", awaitTaskStatus(taskId, "cancelled").get("status").asText());
    }

    @Test
    void cancelOfAFinishedTaskChangesNothing() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        finishWait(taskId, TaskResult.Status.COMPLETED, Map.of("answer", 1));
        awaitTaskStatus(taskId, "completed");
        JsonNode ack = rpc("tasks/cancel", taskParams(taskId), taskId, true).get("result");
        assertEquals("complete", ack.get("resultType").asText());
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                ApiUtil.WORKFLOW_CLIENT.getWorkflow(taskId, false).getStatus());
    }

    @Test
    void executionTerminatedByAnOperatorIsCancelled() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        ApiUtil.WORKFLOW_CLIENT.terminateWorkflow(taskId, "stopped by operator");
        JsonNode task = awaitTaskStatus(taskId, "cancelled");
        assertEquals("stopped by operator", task.get("statusMessage").asText());
    }

    @Test
    void tasksUpdateAcksARunningTask_andChangesNothing() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        ObjectNode params = taskParams(taskId);
        params.putObject("inputResponses").putObject("unknown_key").put("action", "accept");
        JsonNode ack = rpc("tasks/update", params, taskId, true);
        assertEquals("complete", ack.get("result").get("resultType").asText(), ack.toString());
        assertEquals(1, ack.get("result").size());
        assertEquals("working", getTask(taskId).get("status").asText());
        ApiUtil.WORKFLOW_CLIENT.terminateWorkflow(taskId, "e2e cleanup");
    }

    @Test
    void unknownTaskIsInvalidParams() throws Exception {
        String taskId = "nonexistent-" + UUID.randomUUID();
        for (String method : new String[] {"tasks/get", "tasks/update", "tasks/cancel"}) {
            assertEquals(
                    -32602,
                    rpc(method, taskParams(taskId), taskId, true).get("error").get("code").asInt(),
                    method);
        }
    }

    @Test
    void taskMethodsWithoutTheExtensionAreRejected() throws Exception {
        String taskId = createTask(registerWaitingTool(0, 0));
        for (String method : new String[] {"tasks/get", "tasks/update", "tasks/cancel"}) {
            Raw response =
                    post(method, request(method, taskParams(taskId), false), taskId, VERSION);
            assertEquals(400, response.status(), method);
            assertEquals(-32021, response.body().get("error").get("code").asInt(), method);
        }
        assertEquals(
                Workflow.WorkflowStatus.RUNNING,
                ApiUtil.WORKFLOW_CLIENT.getWorkflow(taskId, false).getStatus());
        ApiUtil.WORKFLOW_CLIENT.terminateWorkflow(taskId, "e2e cleanup");
    }

    // ---- 2026-07-28: protocol errors --------------------------------------------------------

    @Test
    void protocolErrorsUseTheirHttpStatus() throws Exception {
        ObjectNode noMeta = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1);
        noMeta.put("method", "tools/list");
        noMeta.putObject("params");
        Raw missingMeta = post("tools/list", noMeta, null, VERSION);
        assertEquals(400, missingMeta.status());
        assertEquals(-32602, missingMeta.body().get("error").get("code").asInt());

        ObjectNode old = request("tools/list", mapper.createObjectNode(), false);
        ((ObjectNode) old.get("params").get("_meta"))
                .put("io.modelcontextprotocol/protocolVersion", "v999.0.0");
        Raw unsupported = post("tools/list", old, null, "v999.0.0");
        assertEquals(400, unsupported.status());
        assertEquals(-32022, unsupported.body().get("error").get("code").asInt());
        assertEquals(
                "v999.0.0", unsupported.body().get("error").get("data").get("requested").asText());

        Raw noHeader =
                post(
                        "tools/list",
                        request("tools/list", mapper.createObjectNode(), false),
                        null,
                        null);
        assertEquals(400, noHeader.status());
        assertEquals(-32020, noHeader.body().get("error").get("code").asInt());

        String tool = registerQuickTool(true);
        Raw nameMismatch =
                post(
                        "tools/call",
                        request("tools/call", mapper.createObjectNode().put("name", tool), true),
                        "some_other_tool",
                        VERSION);
        assertEquals(400, nameMismatch.status());
        assertEquals(-32020, nameMismatch.body().get("error").get("code").asInt());

        for (String method : new String[] {"initialize", "ping", "resources/list"}) {
            Raw removed =
                    post(method, request(method, mapper.createObjectNode(), true), null, VERSION);
            assertEquals(404, removed.status(), method);
            assertEquals(-32601, removed.body().get("error").get("code").asInt(), method);
        }

        ObjectNode notification = mapper.createObjectNode().put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        assertEquals(202, post("notifications/initialized", notification, null, VERSION).status());

        HttpResponse<String> get =
                http.send(
                        HttpRequest.newBuilder(URI.create(MCP_URL)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode());
    }

    // ---- 2025-xx clients: happy paths ----------------------------------------------------------

    @Test
    void legacyHappyPathForEverySupportedVersion() throws Exception {
        String tool = registerQuickTool(true);
        for (String version : new String[] {"2025-03-26", "2025-06-18", "2025-11-25"}) {
            ObjectNode init = mapper.createObjectNode().put("protocolVersion", version);
            init.putObject("capabilities");
            init.putObject("clientInfo").put("name", "e2e-" + version);
            Raw initialized = legacy("initialize", init, null);
            assertEquals(version, initialized.body().get("result").get("protocolVersion").asText());
            assertFalse(initialized.response().headers().firstValue("Mcp-Session-Id").isPresent());

            assertTrue(
                    legacy("tools/list", mapper.createObjectNode(), version)
                            .body()
                            .toString()
                            .contains("\"" + tool + "\""));

            ObjectNode call = mapper.createObjectNode().put("name", tool);
            call.putObject("arguments").put("name", "World");
            JsonNode result = legacy("tools/call", call, version).body().get("result");
            assertFalse(result.get("isError").asBoolean(), version);
            assertEquals("Hello, World!", result.get("structuredContent").get("greeting").asText());
            assertFalse(result.has("resultType"), version);
        }
    }

    @Test
    void legacyCallWithoutHeaderOrInitialize_andVersionFallback_andPing() throws Exception {
        String tool = registerQuickTool(true);
        JsonNode result =
                legacy("tools/call", mapper.createObjectNode().put("name", tool), null)
                        .body()
                        .get("result");
        assertFalse(result.get("isError").asBoolean(), result.toString());

        ObjectNode init = mapper.createObjectNode().put("protocolVersion", "2024-11-05");
        init.putObject("capabilities");
        assertEquals(
                "2025-11-25",
                legacy("initialize", init, null)
                        .body()
                        .get("result")
                        .get("protocolVersion")
                        .asText());

        assertEquals(
                0, legacy("ping", mapper.createObjectNode(), null).body().get("result").size());
    }

    // ---- 2025-xx clients: bad cases ------------------------------------------------------------

    @Test
    void legacyBadCases() throws Exception {
        JsonNode failed =
                legacy(
                                "tools/call",
                                mapper.createObjectNode().put("name", registerFailingTool()),
                                null)
                        .body()
                        .get("result");
        assertTrue(failed.get("isError").asBoolean(), failed.toString());

        String hidden = registerQuickTool(false);
        Raw unknown =
                legacy("tools/call", mapper.createObjectNode().put("name", hidden), "2025-06-18");
        assertEquals(200, unknown.status());
        assertEquals(-32602, unknown.body().get("error").get("code").asInt());

        ObjectNode badArgs = mapper.createObjectNode().put("name", registerQuickTool(true));
        badArgs.putArray("arguments").add(1);
        assertEquals(
                -32602,
                legacy("tools/call", badArgs, "2025-06-18")
                        .body()
                        .get("error")
                        .get("code")
                        .asInt());

        for (String method : new String[] {"tasks/get", "tasks/cancel", "server/discover"}) {
            Raw notFound =
                    legacy(method, mapper.createObjectNode().put("taskId", "x"), "2025-11-25");
            assertEquals(200, notFound.status(), method);
            assertEquals(-32601, notFound.body().get("error").get("code").asInt(), method);
        }

        assertEquals(400, legacy("tools/list", mapper.createObjectNode(), "2024-01-01").status());

        Raw malformed = postRaw("{not json", "2025-06-18");
        assertEquals(400, malformed.status());
        assertEquals(-32700, malformed.body().get("error").get("code").asInt());

        long before = System.currentTimeMillis();
        JsonNode capped =
                legacy(
                                "tools/call",
                                mapper.createObjectNode().put("name", registerWaitingTool(0, 0)),
                                null)
                        .body()
                        .get("result");
        assertTrue(System.currentTimeMillis() - before >= 2500);
        assertTrue(capped.get("isError").asBoolean(), capped.toString());
        assertFalse(capped.has("taskId"));
        String text = capped.get("content").get(0).get("text").asText();
        ApiUtil.WORKFLOW_CLIENT.terminateWorkflow(
                text.substring(text.lastIndexOf(' ') + 1), "e2e cleanup");
    }

    // ---- MCP over HTTP ------------------------------------------------------------------------

    private JsonNode callTool(String tool, String argumentsJson, boolean declareTasks)
            throws Exception {
        ObjectNode params = mapper.createObjectNode().put("name", tool);
        params.set("arguments", mapper.readTree(argumentsJson));
        JsonNode response = rpc("tools/call", params, tool, declareTasks);
        assertNotNull(response.get("result"), response.toString());
        return response.get("result");
    }

    private String createTask(String tool) throws Exception {
        JsonNode created = callTool(tool, "{}", true);
        assertEquals("task", created.get("resultType").asText(), created.toString());
        assertTrue(created.get("ttlMs").isIntegralNumber());
        assertTrue(created.has("createdAt") && created.has("lastUpdatedAt"));
        return created.get("taskId").asText();
    }

    private JsonNode getTask(String taskId) throws Exception {
        JsonNode response = rpc("tasks/get", taskParams(taskId), taskId, true);
        assertNotNull(response.get("result"), response.toString());
        return response.get("result");
    }

    private JsonNode awaitTaskStatus(String taskId, String status) {
        AtomicReference<JsonNode> latest = new AtomicReference<>();
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            JsonNode task = getTask(taskId);
                            latest.set(task);
                            return status.equals(task.get("status").asText());
                        });
        return latest.get();
    }

    private ObjectNode taskParams(String taskId) {
        return mapper.createObjectNode().put("taskId", taskId);
    }

    private JsonNode rpc(String method, ObjectNode params, String name, boolean declareTasks)
            throws Exception {
        return post(method, request(method, params, declareTasks), name, VERSION).body();
    }

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

    private Raw post(String method, JsonNode body, String name, String versionHeader)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(MCP_URL))
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
        return send(request);
    }

    private Raw legacy(String method, ObjectNode params, String versionHeader) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        body.set("params", params);
        return postRaw(body.toString(), versionHeader);
    }

    private Raw postRaw(String body, String versionHeader) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(MCP_URL))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (versionHeader != null) {
            request.header("MCP-Protocol-Version", versionHeader);
        }
        return send(request);
    }

    private Raw send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.body() == null || response.body().isEmpty()) {
            return new Raw(response.statusCode(), null, response);
        }
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .startsWith("application/json"),
                "JSON only, no SSE");
        return new Raw(response.statusCode(), mapper.readTree(response.body()), response);
    }

    // ---- workflows ----------------------------------------------------------------------------

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static void register(WorkflowDef def) {
        def.setVersion(1);
        def.setOwnerEmail(Commons.OWNER_EMAIL);
        ApiUtil.METADATA_CLIENT.updateWorkflowDefs(List.of(def));
    }

    private String registerQuickTool(boolean optedIn) {
        WorkflowTask set = new WorkflowTask();
        set.setName("set");
        set.setTaskReferenceName("set");
        set.setType("SET_VARIABLE");
        set.setInputParameters(Map.of("seen", "${workflow.input.name}"));
        WorkflowDef def = new WorkflowDef();
        def.setName(name("mcp_e2e_quick_"));
        def.setInputParameters(List.of("name"));
        def.setTasks(List.of(set));
        def.setOutputParameters(Map.of("greeting", "Hello, ${workflow.input.name}!"));
        def.setMetadata(Map.of("mcp.enabled", optedIn));
        register(def);
        return def.getName();
    }

    private String registerFailingTool() {
        WorkflowTask terminate = new WorkflowTask();
        terminate.setName("terminate");
        terminate.setTaskReferenceName("terminate");
        terminate.setType("TERMINATE");
        terminate.setInputParameters(
                Map.of("terminationStatus", "FAILED", "terminationReason", "card declined"));
        WorkflowDef def = new WorkflowDef();
        def.setName(name("mcp_e2e_failing_"));
        def.setTasks(List.of(terminate));
        def.setMetadata(Map.of("mcp.enabled", true));
        register(def);
        return def.getName();
    }

    /**
     * A WAIT task (no duration) that the test completes, or that times out after timeoutSeconds.
     */
    private String registerWaitingTool(int syncWaitSeconds, long timeoutSeconds) {
        WorkflowTask wait = new WorkflowTask();
        wait.setName("wait");
        wait.setTaskReferenceName("wait");
        wait.setType("WAIT");
        WorkflowDef def = new WorkflowDef();
        def.setName(name("mcp_e2e_wait_"));
        def.setTasks(List.of(wait));
        def.setOutputParameters(Map.of("answer", "${wait.output.answer}"));
        def.setMetadata(Map.of("mcp.enabled", true, "mcp.syncWaitSeconds", syncWaitSeconds));
        if (timeoutSeconds > 0) {
            def.setTimeoutSeconds(timeoutSeconds);
            def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        }
        register(def);
        return def.getName();
    }

    private void finishWait(
            String workflowId, TaskResult.Status status, Map<String, Object> output) {
        AtomicReference<Task> wait = new AtomicReference<>();
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            ApiUtil.WORKFLOW_CLIENT
                                    .getWorkflow(workflowId, true)
                                    .getTasks()
                                    .stream()
                                    .filter(t -> "WAIT".equals(t.getTaskType()))
                                    .filter(t -> t.getStatus() == Task.Status.IN_PROGRESS)
                                    .findFirst()
                                    .ifPresent(wait::set);
                            return wait.get() != null;
                        });
        TaskResult result = new TaskResult(wait.get());
        result.setStatus(status);
        result.setOutputData(new HashMap<>(output));
        ApiUtil.TASK_CLIENT.updateTask(result);
    }
}
