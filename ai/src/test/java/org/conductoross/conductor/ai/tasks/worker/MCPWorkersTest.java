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
package org.conductoross.conductor.ai.tasks.worker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.mcp.MCPService;
import org.conductoross.conductor.ai.model.MCPListToolsRequest;
import org.conductoross.conductor.ai.model.MCPToolCallRequest;
import org.conductoross.conductor.core.execution.tasks.annotated.AnnotatedWorkflowSystemTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression baseline for the synchronous {@code CALL_MCP_TOOL} and {@code LIST_MCP_TOOLS} behavior
 * before MCP Tasks support (change {@code add-mcp-tasks-polling}, task group 1).
 *
 * <p>Each test drives the worker through {@link AnnotatedWorkflowSystemTask} with the real result
 * mapper and asserts on the resulting {@link TaskModel}, against an actual HTTP listener. Output is
 * compared after JSON conversion, which is the form that is persisted.
 */
class MCPWorkersTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private MockWebServer server;
    private MCPWorkers workers;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        url = server.url("/mcp").toString();
        workers = new MCPWorkers(new MCPService(new OkHttpClient()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ---- 1.1 CALL_MCP_TOOL request ------------------------------------------------------------

    @Test
    void callToolSendsTodaysRequestBodyAndHeaders() throws Exception {
        enqueueJson(
                result("{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"isError\":false}"));

        runCallTool(
                Map.of(
                        "mcpServer",
                        url,
                        "method",
                        "echo",
                        "arguments",
                        Map.of("q", "x"),
                        "headers",
                        Map.of("Authorization", "Bearer t", "X-Custom", "v")));

        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/mcp", request.getPath());
        assertEquals(
                json(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"id\":1,"
                                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"q\":\"x\"}}}"),
                json(request.getBody().readUtf8()));
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
        assertEquals("application/json, text/event-stream", request.getHeader("Accept"));
        assertEquals("Bearer t", request.getHeader("Authorization"));
        assertEquals("v", request.getHeader("X-Custom"));
        assertNoMcpProtocolHeaders(request);
    }

    // ---- 1.2 CALL_MCP_TOOL output ---------------------------------------------------------------

    @Test
    void textAndJsonTextAndImageContentMapToTodaysOutput() throws Exception {
        enqueueJson(
                result(
                        "{\"content\":["
                                + "{\"type\":\"text\",\"text\":\"hello\"},"
                                + "{\"type\":\"text\",\"text\":\"{\\\"a\\\":1}\"},"
                                + "{\"type\":\"image\",\"data\":\"AAAA\",\"mimeType\":\"image/png\"}"
                                + "],\"isError\":false}"));

        TaskModel task = runCallTool(defaultInput());

        assertCompleted(task);
        assertEquals(
                json(
                        "{\"content\":["
                                + "{\"type\":\"text\",\"text\":\"hello\",\"parsed\":\"hello\"},"
                                + "{\"type\":\"text\",\"text\":\"{\\\"a\\\":1}\",\"parsed\":{\"a\":1}},"
                                + "{\"type\":\"image\",\"data\":\"AAAA\",\"mimeType\":\"image/png\"}"
                                + "],\"isError\":false}"),
                output(task));
    }

    @Test
    void toolErrorResultCompletesWithIsErrorTrue() throws Exception {
        enqueueJson(
                result(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"bad input\"}],\"isError\":true}"));

        TaskModel task = runCallTool(defaultInput());

        assertCompleted(task);
        assertEquals(
                json(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"bad input\",\"parsed\":\"bad input\"}],"
                                + "\"isError\":true}"),
                output(task));
    }

    @Test
    void absentIsErrorIsOmittedFromOutput() throws Exception {
        enqueueJson(result("{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}"));

        TaskModel task = runCallTool(defaultInput());

        assertCompleted(task);
        assertEquals(
                json("{\"content\":[{\"type\":\"text\",\"text\":\"hello\",\"parsed\":\"hello\"}]}"),
                output(task));
    }

    @Test
    void singleEventSseResponseMapsLikeJson() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                                "event: message\ndata: "
                                        + result(
                                                "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],"
                                                        + "\"isError\":false}")
                                        + "\n\n"));

        TaskModel task = runCallTool(defaultInput());

        assertCompleted(task);
        assertEquals(
                json(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"hello\",\"parsed\":\"hello\"}],"
                                + "\"isError\":false}"),
                output(task));
    }

    // ---- 1.3 CALL_MCP_TOOL failure outcomes -----------------------------------------------------

    @Test
    void http500FailsWithTodaysReason() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        TaskModel task = runCallTool(defaultInput());

        String inner = "HTTP 500 error from MCP server: boom";
        assertFailed(
                task,
                "Task execution failed: Failed to call MCP tool 'echo' on "
                        + url
                        + ": "
                        + inner
                        + " — caused by: "
                        + inner);
    }

    @Test
    void jsonRpcErrorFailsWithTodaysReason() {
        enqueueJson(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"message\":\"bad\"}}");

        TaskModel task = runCallTool(defaultInput());

        String inner = "{\"code\":-32602,\"message\":\"bad\"}";
        assertFailed(
                task,
                "Task execution failed: Failed to call MCP tool 'echo' on "
                        + url
                        + ": "
                        + inner
                        + " — caused by: "
                        + inner);
    }

    @Test
    void connectionFailureFailsWithTodaysReasonPrefix() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        TaskModel task = runCallTool(defaultInput());

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        assertTrue(
                task.getReasonForIncompletion()
                        .startsWith(
                                "Task execution failed: Failed to call MCP tool 'echo' on "
                                        + url
                                        + ": "),
                task.getReasonForIncompletion());
        assertTrue(task.getOutputData().isEmpty());
    }

    /**
     * Records an existing defect, not a desired behavior: a result without {@code content} fails
     * with a {@code NullPointerException} in {@code ToolCallResult.from(Map)}. The opted-out path
     * must keep this behavior until it is fixed in a separate change.
     */
    @Test
    void resultWithoutContentFailsToday() {
        enqueueJson(result("{\"isError\":false}"));

        TaskModel task = runCallTool(defaultInput());

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        assertNotNull(task.getReasonForIncompletion());
        assertTrue(
                task.getReasonForIncompletion().startsWith("Task execution failed: "),
                task.getReasonForIncompletion());
        assertTrue(task.getOutputData().isEmpty());
    }

    // ---- 1.4 Input isolation --------------------------------------------------------------------

    @Test
    void unknownTopLevelInputIsNotSentAndArgumentsAreForwardedVerbatim() throws Exception {
        enqueueJson(
                result("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"isError\":false}"));

        Map<String, Object> input = new HashMap<>();
        input.put("mcpServer", url);
        input.put("method", "echo");
        input.put(
                "arguments",
                Map.of("q", "x", "mcpOptions", Map.of("protocolVersion", "2026-07-28")));
        input.put("extraTopLevel", "should-not-be-sent");
        // A top-level "mcpOptions" was ignored before task 2.1; since 2.1 it selects the protocol
        // (an intended change), and the protocol-selection tests below cover it. Inside
        // "arguments" it stays an ordinary tool argument.

        TaskModel task = runCallTool(input);

        assertCompleted(task);
        RecordedRequest request = takeRequest();
        assertEquals(
                json(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"id\":1,"
                                + "\"params\":{\"name\":\"echo\","
                                + "\"arguments\":{\"q\":\"x\",\"mcpOptions\":{\"protocolVersion\":\"2026-07-28\"}}}}"),
                json(request.getBody().readUtf8()));
        assertNoMcpProtocolHeaders(request);
    }

    // ---- 1.5 LIST_MCP_TOOLS ---------------------------------------------------------------------

    @Test
    void listToolsSendsTodaysRequestAndMapsTools() throws Exception {
        enqueueJson(
                result(
                        "{\"tools\":[{\"name\":\"echo\",\"description\":\"Echo\","
                                + "\"inputSchema\":{\"type\":\"object\","
                                + "\"properties\":{\"q\":{\"type\":\"string\"}},"
                                + "\"required\":[\"q\"]}}]}"));

        TaskModel task =
                run(
                        "LIST_MCP_TOOLS",
                        MCPWorkers.class.getMethod("listTools", MCPListToolsRequest.class),
                        Map.of("mcpServer", url, "headers", Map.of("Authorization", "Bearer t")));

        RecordedRequest request = takeRequest();
        assertEquals(
                json("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1}"),
                json(request.getBody().readUtf8()));
        assertEquals("application/json, text/event-stream", request.getHeader("Accept"));
        assertEquals("Bearer t", request.getHeader("Authorization"));
        assertNoMcpProtocolHeaders(request);

        assertCompleted(task);
        assertEquals(
                json(
                        "{\"tools\":[{\"name\":\"echo\",\"description\":\"Echo\","
                                + "\"inputSchema\":{\"type\":\"object\","
                                + "\"properties\":{\"q\":{\"type\":\"string\"}},"
                                + "\"required\":[\"q\"]}}]}"),
                output(task));
    }

    // ---- 2.1 / 2.5 / 2.6 mcpOptions input -------------------------------------------------------

    @Test
    void emptyMcpOptionsProducesTheSameRequestAndOutputAsMcpOptionsAbsent() throws Exception {
        String body =
                result("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"isError\":false}");
        enqueueJson(body);
        enqueueJson(body);

        TaskModel absent = runCallTool(defaultInput());
        RecordedRequest absentRequest = takeRequest();

        Map<String, Object> disabledInput = new HashMap<>(defaultInput());
        disabledInput.put("mcpOptions", Map.of());
        TaskModel disabled = runCallTool(disabledInput);
        RecordedRequest disabledRequest = takeRequest();

        assertEquals(
                json(absentRequest.getBody().readUtf8()),
                json(disabledRequest.getBody().readUtf8()));
        assertEquals(absentRequest.getHeaders(), disabledRequest.getHeaders());
        assertCompleted(disabled);
        assertEquals(output(absent), output(disabled));
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void optedInCallFailsWhenNoPageListsTheToolAndSendsNoToolsCall() throws Exception {
        enqueueJson(result("{\"tools\":[{\"name\":\"other\"}],\"nextCursor\":\"page-2\"}"));
        enqueueJson(result("{\"tools\":[{\"name\":\"another\"}]}"));

        TaskModel task = runCallTool(optedInInput());

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        assertTrue(
                task.getReasonForIncompletion().contains("MCP tool 'echo' is not listed"),
                task.getReasonForIncompletion());
        assertEquals(
                "tools/list", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(
                "tools/list", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void optedInCallFailsOnInvalidAnnotationAndSendsNoToolsCall() throws Exception {
        enqueueJson(
                result(
                        "{\"tools\":[{\"name\":\"echo\",\"inputSchema\":{\"type\":\"object\","
                                + "\"properties\":{\"count\":{\"type\":\"number\","
                                + "\"x-mcp-header\":\"Count\"}}}}]}"));

        TaskModel task = runCallTool(optedInInput());

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        String reason = task.getReasonForIncompletion();
        assertTrue(reason.contains("MCP tool 'echo'"), reason);
        assertTrue(reason.contains("x-mcp-header annotation 'Count'"), reason);
        assertEquals(1, server.getRequestCount());
    }

    // ---- 2.1 mcpOptions validation, before any request -----------------------------------------

    @Test
    void unsupportedProtocolVersionsAreRejectedBeforeAnyRequest() {
        for (String version : List.of("2025-11-25", "auto")) {
            TaskModel task = runCallTool(inputWithMcpOptions(Map.of("protocolVersion", version)));

            assertTerminalFailureContaining(
                    task,
                    "Unsupported mcpOptions.protocolVersion '" + version + "'",
                    "supported versions: [2026-07-28]",
                    "Remove protocolVersion to use the legacy MCP request");
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void unknownOptionIsRejectedBeforeAnyRequest() {
        TaskModel withVersion =
                runCallTool(
                        inputWithMcpOptions(
                                Map.of("protocolVersion", "2026-07-28", "tasks", true)));
        TaskModel withoutVersion = runCallTool(inputWithMcpOptions(Map.of("tasks", true)));

        assertTerminalFailureContaining(
                withVersion, "unknown option(s) [tasks]", "supported options: [protocolVersion]");
        assertTerminalFailureContaining(withoutVersion, "unknown option(s) [tasks]");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void mcpOptionsThatIsNotAnObjectIsRejectedBeforeAnyRequest() {
        TaskModel task = runCallTool(inputWithMcpOptions("2026-07-28"));

        assertTerminalFailureContaining(
                task, "Invalid mcpOptions: expected a JSON object, got a string");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void protocolVersionThatIsNotAStringIsRejectedBeforeAnyRequest() {
        TaskModel task = runCallTool(inputWithMcpOptions(Map.of("protocolVersion", 20260728)));

        assertTerminalFailureContaining(
                task,
                "Invalid mcpOptions.protocolVersion: expected a string, got a number",
                "supported versions: [2026-07-28]");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void nullProtocolVersionIsALegacyCall() throws Exception {
        enqueueJson(result("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));
        Map<String, Object> options = new HashMap<>();
        options.put("protocolVersion", null);

        TaskModel task = runCallTool(inputWithMcpOptions(options));

        assertCompleted(task);
        RecordedRequest request = takeRequest();
        assertFalse(json(request.getBody().readUtf8()).at("/params").has("_meta"));
        assertNoMcpProtocolHeaders(request);
        assertEquals(1, server.getRequestCount());
    }

    // ---- 3.5 Immediate results with mcpOptions.protocolVersion ----------------------------------
    // ----------------------------------------------------

    @Test
    void optedInCompleteResultHasTheOptOutOutputAndNoPollingState() throws Exception {
        String content =
                "\"content\":[{\"type\":\"text\",\"text\":\"plain\"},"
                        + "{\"type\":\"text\",\"text\":\"{\\\"a\\\":1}\"}],\"isError\":false";

        assertOptedInMatchesOptOut(
                result("{" + content + "}"),
                result("{\"resultType\":\"complete\"," + content + "}"));
    }

    @Test
    void optedInResultWithoutResultTypeHasTheOptOutOutput() throws Exception {
        String callToolResult = result("{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}");

        assertOptedInMatchesOptOut(callToolResult, callToolResult);
    }

    @Test
    void optedInToolErrorHasTheOptOutStatusAndOutput() throws Exception {
        String callToolResult =
                result(
                        "{\"resultType\":\"complete\",\"isError\":true,"
                                + "\"content\":[{\"type\":\"text\",\"text\":\"no such row\"}]}");

        assertOptedInMatchesOptOut(callToolResult, callToolResult);
    }

    @Test
    void optedInImmediateResultSendsSchemaLookupThenOneToolsCall() throws Exception {
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));

        assertCompleted(runCallTool(optedInInput()));

        assertEquals(List.of("tools/list", "tools/call"), methodsOfRequests(2));
    }

    // ---- 3.2 resultType routing through the real mapper ---------------------------------------

    @Test
    void unknownResultTypeFailsNamingTheValue() {
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"something_else\",\"content\":[]}"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "'something_else'");
    }

    @Test
    void inputRequiredResultTypeFailsAsUnsupported() {
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"input_required\",\"inputRequests\":{}}"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "server input requests are not supported");
    }

    // ---- 3.3 HeaderMismatch refresh and retry ---------------------------------------------------

    @Test
    void headerMismatchRefreshesTheToolAndRetriesOnce() throws Exception {
        enqueueToolList();
        enqueueRpcError(400, -32020, "Header mismatch: Mcp-Param-Region missing");
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));

        TaskModel task = runCallTool(optedInInput());

        assertCompleted(task);
        assertEquals(
                List.of("tools/list", "tools/call", "tools/list", "tools/call"),
                methodsOfRequests(4));
    }

    @Test
    void repeatedHeaderMismatchFailsWithTheServerMessage() throws Exception {
        enqueueToolList();
        enqueueRpcError(400, -32020, "Header mismatch: Mcp-Param-Region missing");
        enqueueToolList();
        enqueueRpcError(400, -32020, "Header mismatch: Mcp-Param-Region missing");

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "Header mismatch: Mcp-Param-Region missing");
        assertEquals(
                List.of("tools/list", "tools/call", "tools/list", "tools/call"),
                methodsOfRequests(4));
        assertEquals(4, server.getRequestCount());
    }

    // ---- 3.6 Protocol version, capability and older-server errors -------------------------------

    @Test
    void unsupportedProtocolVersionFailsListingTheSupportedVersions() {
        enqueueToolList();
        server.enqueue(
                rpcErrorResponse(
                        400,
                        "{\"code\":-32022,\"message\":\"Unsupported protocol version\","
                                + "\"data\":{\"supported\":[\"2025-11-25\"],"
                                + "\"requested\":\"2026-07-28\"}}"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "2025-11-25");
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void missingClientCapabilityFailsNamingTheCapability() {
        enqueueToolList();
        server.enqueue(
                rpcErrorResponse(
                        400,
                        "{\"code\":-32021,\"message\":\"Missing required client capability\","
                                + "\"data\":{\"requiredCapabilities\":{\"elicitation\":{}}}}"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "elicitation");
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void olderServerRejectionFailsWithoutRetryingInThePreChangeForm() {
        List<MockResponse> rejections =
                List.of(
                        new MockResponse().setResponseCode(400),
                        new MockResponse()
                                .setResponseCode(404)
                                .setHeader("Content-Type", "text/html")
                                .setBody("<html>Not Found</html>"),
                        new MockResponse().setResponseCode(405).setBody("Method Not Allowed"));
        int expectedRequests = 0;
        for (MockResponse rejection : rejections) {
            enqueueToolList();
            server.enqueue(rejection);

            TaskModel task = runCallTool(optedInInput());

            assertFailedContaining(
                    task,
                    "rejected the MCP 2026-07-28 request form",
                    "remove mcpOptions.protocolVersion");
            expectedRequests += 2;
            assertEquals(expectedRequests, server.getRequestCount());
        }
    }

    /**
     * The realistic older-server flow: the schema-lookup {@code tools/list} is the first request in
     * the 2026-07-28 form, so the server rejects it before any {@code tools/call} is sent.
     */
    @Test
    void olderServerRejectingTheSchemaLookupFailsWithTheActionableReason() throws Exception {
        List<MockResponse> rejections =
                List.of(
                        new MockResponse().setResponseCode(400),
                        new MockResponse()
                                .setResponseCode(404)
                                .setHeader("Content-Type", "text/html")
                                .setBody("<html>Not Found</html>"),
                        new MockResponse().setResponseCode(405).setBody("Method Not Allowed"));
        int expectedRequests = 0;
        for (MockResponse rejection : rejections) {
            server.enqueue(rejection);

            TaskModel task = runCallTool(optedInInput());

            assertFailedContaining(
                    task,
                    "rejected the MCP 2026-07-28 request form",
                    "remove mcpOptions.protocolVersion");
            expectedRequests += 1;
            assertEquals(expectedRequests, server.getRequestCount());
            assertEquals(
                    "tools/list",
                    json(takeRequest().getBody().readUtf8()).get("method").asString());
        }
    }

    @Test
    void olderServerThatProcessesTheRequestCompletesWithTheSynchronousMapping() throws Exception {
        String callToolResult = result("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");

        assertOptedInMatchesOptOut(callToolResult, callToolResult);
    }

    /** A 401 on the first request is about credentials, not the request form: no protocol hint. */
    @Test
    void credentialErrorOnTheSchemaLookupIsNotReportedAsAnOlderServer() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("token expired"));

        TaskModel task = runCallTool(optedInInput());

        assertFailed(
                task,
                "Task execution failed: Failed to call MCP tool 'echo' on "
                        + url
                        + ": HTTP 401 error from MCP server: token expired — caused by: HTTP 401"
                        + " error from MCP server: token expired");
        assertFalse(task.getReasonForIncompletion().contains("protocolVersion"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void credentialErrorOnToolsCallFailsWithTheOptOutReason() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));
        TaskModel optOut = runCallTool(defaultInput());

        enqueueToolList();
        server.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));
        TaskModel optedIn = runCallTool(optedInInput());

        assertFailed(optedIn, optOut.getReasonForIncompletion());
    }

    @Test
    void olderServerReasonAlsoNamesANonMcpUrl() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("<html>Not Found</html>"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(
                task, "or the URL may not be an MCP endpoint", "remove mcpOptions.protocolVersion");
    }

    // ---- 4.x / 5.1-5.3 Polling lifecycle --------------------------------------------------------

    @Test
    void createdTaskIsInProgressWithOnlyThePollingState() {
        TaskModel task = startCreatedTask(5000L);

        assertInProgress(task, 5);
        assertEquals(
                json(
                        "{\"mcpTask\":{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0,\"pollIntervalMs\":5000}}"),
                output(task));
    }

    @Test
    void pollIntervalIsRoundedUpOrDefaults() {
        assertInProgress(startCreatedTask(1500L), 2);
        assertInProgress(startCreatedTask(null), McpTasksCall.DEFAULT_POLL_SECONDS);
    }

    @Test
    void pollUsesThePersistedTaskIdAfterAJsonRoundTrip() throws Exception {
        TaskModel created = startCreatedTask(5000L);
        takeRequests(2);
        TaskModel restored = MAPPER.readValue(MAPPER.writeValueAsString(created), TaskModel.class);
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":3000");

        poll(new MCPWorkers(new MCPService(new OkHttpClient())), restored);

        assertInProgress(restored, 3);
        RecordedRequest request = takeRequest();
        assertEquals("tasks/get", json(request.getBody().readUtf8()).get("method").asString());
        assertEquals("t-1", request.getHeader("Mcp-Name"));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void workingKeepsPollingWithTheLatestInterval() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":3000");

        poll(task);

        assertInProgress(task, 3);
        assertEquals(0, output(task).at("/mcpTask/pollFailures").asInt());
    }

    @Test
    void completedTaskHasTheSynchronousOutputPlusThePollingState() throws Exception {
        String callToolResult =
                "{\"isError\":false,\"content\":[{\"type\":\"text\",\"text\":\"{\\\"a\\\":1}\"}]}";
        enqueueJson(result(callToolResult));
        TaskModel legacy = runCallTool(defaultInput());

        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"completed\",\"result\":" + callToolResult);
        poll(task);

        assertCompleted(task);
        JsonNode expected = output(legacy).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) expected)
                .set("mcpTask", json("{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0}"));
        assertEquals(expected, output(task));
    }

    @Test
    void completedTaskWithToolErrorMatchesTheLegacyOutcome() {
        String callToolResult =
                "{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"no row\"}]}";
        enqueueJson(result(callToolResult));
        TaskModel legacy = runCallTool(defaultInput());

        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"completed\",\"result\":" + callToolResult);
        poll(task);

        assertEquals(legacy.getStatus(), task.getStatus());
        JsonNode expected = output(legacy).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) expected)
                .set("mcpTask", json("{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0}"));
        assertEquals(expected, output(task));
    }

    @Test
    void failedTaskFailsWithTheJsonRpcError() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"failed\",\"error\":{\"code\":-32603,\"message\":\"boom\"}");

        poll(task);

        assertPollFailed(task, "-32603", "boom");
    }

    @Test
    void taskCancelledByTheServerFails() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"cancelled\"");

        poll(task);

        assertPollFailed(task, "MCP server cancelled task 't-1'");
    }

    @Test
    void inputRequiredCancelsTheTaskAndFails() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"input_required\",\"inputRequests\":{}");
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertPollFailed(task, "task input requests are not supported");
        takeRequests(3);
        RecordedRequest cancel = takeRequest();
        assertEquals("tasks/cancel", json(cancel.getBody().readUtf8()).get("method").asString());
        assertEquals("t-1", cancel.getHeader("Mcp-Name"));
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void unsupportedStatusCancelsAndFailsNamingIt() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"paused\"");
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertPollFailed(task, "unsupported status 'paused'", "Conductor sent tasks/cancel");
        takeRequests(3);
        JsonNode cancel = json(takeRequest().getBody().readUtf8());
        assertEquals("tasks/cancel", cancel.get("method").asString());
        assertEquals("t-1", cancel.at("/params/taskId").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void noPollingHistoryAfterTenChecksWithThreeTransientFailures() {
        TaskModel task = startCreatedTask(5000L);
        for (int i = 0; i < 6; i++) {
            enqueueTaskStatus("\"status\":\"working\"");
            poll(task);
        }
        for (int i = 1; i <= 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(503));
            poll(task);
            assertEquals(i, output(task).at("/mcpTask/pollFailures").asInt());
        }
        enqueueTaskStatus("\"status\":\"working\",\"statusMessage\":\"still going\"");
        poll(task);

        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertEquals(
                json(
                        "{\"mcpTask\":{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0,\"pollIntervalMs\":5000}}"),
                output(task));
    }

    @Test
    void transientErrorWithAnUnusualJsonRpcErrorIsCountedAsAPollFailure() {
        TaskModel task = startCreatedTask(5000L);

        server.enqueue(
                new MockResponse()
                        .setResponseCode(503)
                        .setBody(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,"
                                        + "\"message\":{\"detail\":\"busy\"}}}"));
        poll(task);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(1, output(task).at("/mcpTask/pollFailures").asInt());

        server.enqueue(
                new MockResponse()
                        .setResponseCode(503)
                        .setBody(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{"
                                        + "\"code\":99999999999,\"message\":\"busy\"}}"));
        poll(task);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(2, output(task).at("/mcpTask/pollFailures").asInt());
    }

    @Test
    void pollIntervalThatIsNotAWholeNumberFittingALongIsIgnored() {
        // 1e30: a float, out of the long range.
        enqueueToolList();
        enqueueJson(
                result(
                        "{\"resultType\":\"task\",\"taskId\":\"t-1\",\"status\":\"working\","
                                + "\"pollIntervalMs\":1e30}"));
        TaskModel task = runCallTool(optedInInput());

        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertEquals("t-1", output(task).at("/mcpTask/taskId").asString());

        // An integer out of the long range.
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":99999999999999999999");
        poll(task);
        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);

        // A fraction is ignored, not truncated to 1500.
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":1500.5");
        poll(task);
        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);

        // A whole number written as a float is used.
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":2000.0");
        poll(task);
        assertInProgress(task, 2);
    }

    /** A result that the synchronous mapping cannot read: text is an object, not a string. */
    private static final String UNREADABLE_RESULT =
            "\"result\":{\"content\":[{\"type\":\"text\",\"text\":{\"a\":1}}]}";

    @Test
    void resultOnACreatedTaskIsIgnored() {
        enqueueToolList();
        enqueueJson(
                result(
                        "{\"resultType\":\"task\",\"taskId\":\"t-1\",\"status\":\"working\","
                                + UNREADABLE_RESULT
                                + "}"));
        TaskModel task = runCallTool(optedInInput());

        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertEquals(
                json("{\"mcpTask\":{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0}}"),
                output(task));
    }

    @Test
    void resultOnACreatedTaskThatIsAlreadyCompletedIsIgnored() {
        enqueueToolList();
        enqueueJson(
                result(
                        "{\"resultType\":\"task\",\"taskId\":\"t-1\",\"status\":\"completed\","
                                + UNREADABLE_RESULT
                                + "}"));
        TaskModel task = runCallTool(optedInInput());

        // CreateTaskResult has no result for any status; the result comes from tasks/get.
        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
        enqueueTaskStatus(
                "\"status\":\"completed\",\"result\":{\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"done\"}],\"isError\":false}");
        poll(task);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("done", output(task).at("/content/0/text").asString());
    }

    @Test
    void tasksGetResponseOverTheDepthLimitCancelsAndFails() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        String deep = "{\"a\":".repeat(40) + "1" + "}".repeat(40);
        enqueueTaskStatus("\"status\":\"working\",\"_meta\":" + deep);
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertPollFailed(task, "maximum nesting depth");
        takeRequests(3);
        JsonNode cancel = json(takeRequest().getBody().readUtf8());
        assertEquals("tasks/cancel", cancel.get("method").asString());
        assertEquals("t-1", cancel.at("/params/taskId").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void completedResultThatCannotBeReadFailsWithoutACancel() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"completed\"," + UNREADABLE_RESULT);

        poll(task);

        // The remote task has already finished: a clear reason, and no tasks/cancel.
        assertPollFailed(task, "completed with a result that cannot be mapped");
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void nonTransientTasksGetErrorCancelsAndFails() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        enqueueJson(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32600,\"message\":\"boom\"}}");
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertPollFailed(task, "boom");
        takeRequests(3);
        JsonNode cancel = json(takeRequest().getBody().readUtf8());
        assertEquals("tasks/cancel", cancel.get("method").asString());
        assertEquals("t-1", cancel.at("/params/taskId").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void resultOnAWorkingTaskIsIgnored() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"working\"," + UNREADABLE_RESULT);

        poll(task);

        assertInProgress(task, 5);
        assertEquals(
                json(
                        "{\"mcpTask\":{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0,\"pollIntervalMs\":5000}}"),
                output(task));
        assertEquals(3, server.getRequestCount(), "no tasks/cancel");
    }

    @Test
    void deadlineExceededWhileWorkingCancelsAndFailsWithoutARetry() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        task.setStartTime(System.currentTimeMillis() - 25L * 60 * 60 * 1000);
        enqueueTaskStatus("\"status\":\"working\"");
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("maximum duration"));
        assertTrue(task.getReasonForIncompletion().contains("24 h"));
        takeRequests(3);
        assertEquals(
                "tasks/cancel", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(
                json("{\"mcpTask\":{\"taskId\":\"t-1\",\"pollFailures\":0,\"retryCount\":0}}"),
                output(task));
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void transientFailureAfterTheDeadlineCancelsAndFailsWithoutARetry() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        task.setStartTime(System.currentTimeMillis() - 25L * 60 * 60 * 1000);
        server.enqueue(new MockResponse().setResponseCode(503));
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("maximum duration"));
        takeRequests(3);
        assertEquals(
                "tasks/cancel", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void taskThatFinishedInTheLastIntervalCompletesAfterTheDeadline() {
        TaskModel task = startCreatedTask(5000L);
        task.setStartTime(System.currentTimeMillis() - 25L * 60 * 60 * 1000);
        enqueueTaskStatus(
                "\"status\":\"completed\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");

        poll(task);

        assertCompleted(task);
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void lastCallbackIsLimitedSoTheDeadlineCheckRuns() {
        TaskModel task = startCreatedTask(5000L);
        task.setStartTime(
                System.currentTimeMillis() - (McpTasksCall.MAX_DURATION_SECONDS - 3) * 1000L);
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":600000");

        poll(task);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertTrue(task.getCallbackAfterSeconds() >= 1 && task.getCallbackAfterSeconds() <= 4);
    }

    @Test
    void expiredOrUnknownTaskFails() {
        TaskModel task = startCreatedTask(5000L);
        server.enqueue(rpcErrorResponse(400, "{\"code\":-32602,\"message\":\"Task not found\"}"));

        poll(task);

        assertPollFailed(task, "expired or is unknown");
        assertEquals(3, server.getRequestCount(), "the server does not know the task: no cancel");
    }

    @Test
    void authorizationFailureWhilePollingFails() {
        TaskModel task = startCreatedTask(5000L);
        server.enqueue(new MockResponse().setResponseCode(401));

        poll(task);

        assertPollFailed(task, "HTTP 401");
        assertEquals(3, server.getRequestCount(), "a cancel would be rejected the same way");
    }

    @Test
    void networkFailureWhilePollingIsTransient() {
        TaskModel task = startCreatedTask(5000L);
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return new MockResponse()
                                .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
                    }
                });

        poll(task);

        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertEquals(1, output(task).at("/mcpTask/pollFailures").asInt());
    }

    @Test
    void consecutiveFailureLimitCancelsAndFails() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        task.getOutputData()
                .put(
                        "mcpTask",
                        Map.of(
                                "taskId",
                                "t-1",
                                "pollFailures",
                                McpTasksCall.MAX_POLL_FAILURES - 1));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("busy"));
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertPollFailed(task, "limit of " + McpTasksCall.MAX_POLL_FAILURES, "HTTP 503");
        takeRequests(3);
        assertEquals(
                "tasks/cancel", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void failureCountResetsAfterASuccessfulCheck() {
        TaskModel task = startCreatedTask(5000L);
        server.enqueue(new MockResponse().setResponseCode(503));
        poll(task);
        enqueueTaskStatus("\"status\":\"working\"");
        poll(task);
        server.enqueue(new MockResponse().setResponseCode(503));
        poll(task);

        assertEquals(1, output(task).at("/mcpTask/pollFailures").asInt());
    }

    @Test
    void scheduledTaskWithStalePollingStateStartsANewToolCall() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        takeRequests(2);
        // The engine resets a CANCELED task to SCHEDULED in place (same task ID, output kept),
        // for example when a sub-workflow next to it is retried.
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setStartTime(0);
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));

        startAgain(task);

        assertCompleted(task);
        assertEquals(
                "tools/list", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(
                "tools/call", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void failedCheckWaitsTheServersIntervalThenBacksOff() {
        TaskModel task = startCreatedTask(60000L);
        assertInProgress(task, 60);
        assertEquals(60000, output(task).at("/mcpTask/pollIntervalMs").asLong());

        long[] expected = {60, 120, 240, 300, 300};
        for (long seconds : expected) {
            server.enqueue(new MockResponse().setResponseCode(503));
            poll(task);
            assertInProgress(task, seconds);
            assertEquals(60000, output(task).at("/mcpTask/pollIntervalMs").asLong());
        }
        assertEquals(expected.length, output(task).at("/mcpTask/pollFailures").asInt());

        enqueueTaskStatus("\"status\":\"working\"");
        poll(task);
        assertInProgress(task, 60);
        assertEquals(0, output(task).at("/mcpTask/pollFailures").asInt());
    }

    @Test
    void workingWithoutAnIntervalKeepsTheLastOneTheServerGave() {
        TaskModel task = startCreatedTask(60000L);
        enqueueTaskStatus("\"status\":\"working\"");

        poll(task);

        assertInProgress(task, 60);
        assertEquals(60000, output(task).at("/mcpTask/pollIntervalMs").asLong());
    }

    @Test
    void zeroIntervalKeepsTheLastOneTheServerGave() {
        TaskModel task = startCreatedTask(30000L);
        enqueueTaskStatus("\"status\":\"working\",\"pollIntervalMs\":0");

        poll(task);

        assertInProgress(task, 30);
        assertEquals(30000, output(task).at("/mcpTask/pollIntervalMs").asLong());
    }

    @Test
    void noIntervalFromTheServerIsLeftOutOfTheState() {
        TaskModel task = startCreatedTask(null);

        assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertTrue(output(task).at("/mcpTask/pollIntervalMs").isMissingNode());
    }

    @Test
    void backoffDoublesFromTheIntervalUpToTheCap() {
        assertEquals(5000, McpTasksCall.backoffMs(null, 1));
        assertEquals(10000, McpTasksCall.backoffMs(null, 2));
        assertEquals(160000, McpTasksCall.backoffMs(null, 6));
        assertEquals(300000, McpTasksCall.backoffMs(null, 7));
        assertEquals(300000, McpTasksCall.backoffMs(null, McpTasksCall.MAX_POLL_FAILURES));
        // A server interval longer than the cap is never shortened.
        assertEquals(600000, McpTasksCall.backoffMs(600000L, 1));
        assertEquals(600000, McpTasksCall.backoffMs(600000L, 5));
    }

    @Test
    void internalErrorWhilePollingIsTransientWhateverTheHttpStatus() {
        for (int status : new int[] {200, 500}) {
            TaskModel task = startCreatedTask(5000L);
            int before = server.getRequestCount();
            server.enqueue(
                    rpcErrorResponse(status, "{\"code\":-32603,\"message\":\"store hiccup\"}"));

            poll(task);

            assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
            assertEquals(1, output(task).at("/mcpTask/pollFailures").asInt());
            assertEquals(before + 1, server.getRequestCount(), "no tasks/cancel");
        }
    }

    @Test
    void theServersErrorCodeDecidesBeforeTheHttpStatus() throws Exception {
        // HTTP 503 alone is transient, but the server's own answer says the request is rejected.
        TaskModel task = startCreatedTask(5000L);
        server.enqueue(rpcErrorResponse(503, "{\"code\":-32600,\"message\":\"bad request\"}"));
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        poll(task);

        assertPollFailed(task, "bad request");
        takeRequests(3);
        assertEquals(
                "tasks/cancel", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void implementationDefinedCodeLetsTheHttpStatusDecide() throws Exception {
        // The MCP TypeScript SDK sends -32001 "Session not found" with 404: a rejected request.
        TaskModel notFound = startCreatedTask(5000L);
        int before = server.getRequestCount();
        server.enqueue(
                rpcErrorResponse(404, "{\"code\":-32001,\"message\":\"Session not found\"}"));
        enqueueJson(result("{\"resultType\":\"complete\"}"));
        poll(notFound);
        assertPollFailed(notFound, "Session not found");
        assertEquals(before + 2, server.getRequestCount(), "tasks/get, then tasks/cancel");

        // -32000 with 403 (for example, an Origin check): fails without a cancel.
        TaskModel forbidden = startCreatedTask(5000L);
        before = server.getRequestCount();
        server.enqueue(rpcErrorResponse(403, "{\"code\":-32000,\"message\":\"Forbidden\"}"));
        poll(forbidden);
        assertPollFailed(forbidden, "HTTP 403");
        assertEquals(before + 1, server.getRequestCount(), "no tasks/cancel");

        // -32000 with 500: a server failure, so transient.
        TaskModel serverError = startCreatedTask(5000L);
        before = server.getRequestCount();
        server.enqueue(rpcErrorResponse(500, "{\"code\":-32000,\"message\":\"replay failed\"}"));
        poll(serverError);
        assertInProgress(serverError, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertEquals(1, output(serverError).at("/mcpTask/pollFailures").asInt());
        assertEquals(before + 1, server.getRequestCount(), "no tasks/cancel");
    }

    @Test
    void completedResultAtTheDepthLimitCompletesWhenPolled() {
        // The tool result is checked from its own root, as a result returned at once is, so a
        // result that passes there also passes when it arrives through tasks/get.
        TaskModel task = startCreatedTask(5000L);
        // structuredContent and the maps inside it reach depth 32 from the tool result's root:
        // the limit. Counted from the tasks/get root, they would be one level too deep.
        String deep = "{\"a\":".repeat(32) + "1" + "}".repeat(32);
        enqueueTaskStatus(
                "\"status\":\"completed\",\"result\":{\"content\":[],\"structuredContent\":"
                        + deep
                        + "}");

        poll(task);

        assertCompleted(task);
    }

    @Test
    void applicationDefinedCodeLetsTheHttpStatusDecide() throws Exception {
        // -1 is left to the application by JSON-RPC: no defined meaning, so 503 is transient.
        TaskModel busy = startCreatedTask(5000L);
        int before = server.getRequestCount();
        server.enqueue(rpcErrorResponse(503, "{\"code\":-1,\"message\":\"overloaded\"}"));
        poll(busy);
        assertInProgress(busy, McpTasksCall.DEFAULT_POLL_SECONDS);
        assertEquals(before + 1, server.getRequestCount(), "no tasks/cancel");

        // The same code with HTTP 200 cancels and fails.
        TaskModel rejected = startCreatedTask(5000L);
        before = server.getRequestCount();
        enqueueJson("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"no\"}}");
        enqueueJson(result("{\"resultType\":\"complete\"}"));
        poll(rejected);
        assertPollFailed(rejected, "no");
        assertEquals(before + 2, server.getRequestCount(), "tasks/get, then tasks/cancel");
    }

    @Test
    void rateLimitWhilePollingIsTransient() {
        for (int status : new int[] {429, 408}) {
            TaskModel task = startCreatedTask(5000L);
            server.enqueue(new MockResponse().setResponseCode(status));

            poll(task);

            assertInProgress(task, McpTasksCall.DEFAULT_POLL_SECONDS);
            assertEquals(1, output(task).at("/mcpTask/pollFailures").asInt());
        }
    }

    @Test
    void invalidPollingStateFailsWithoutARequest() {
        List<Object> states = new ArrayList<>();
        states.add(Map.of("pollFailures", 0));
        states.add(Map.of("taskId", "  ", "pollFailures", 0));
        states.add(Map.of("taskId", 42, "pollFailures", 0));
        states.add("t-1");
        for (Object state : states) {
            TaskModel task = startCreatedTask(5000L);
            int requests = server.getRequestCount();
            task.getOutputData().put("mcpTask", state);

            poll(task);

            assertEquals(TaskModel.Status.FAILED, task.getStatus());
            assertEquals(0, task.getCallbackAfterSeconds());
            assertTrue(
                    task.getReasonForIncompletion().contains("Invalid MCP polling state"),
                    task.getReasonForIncompletion());
            assertEquals(requests, server.getRequestCount());
        }
    }

    @Test
    void failingCancelStillFailsTheTask() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"input_required\",\"inputRequests\":{}");
        server.enqueue(new MockResponse().setResponseCode(500).setBody("cancel broke"));

        poll(task);

        assertPollFailed(task, "task input requests are not supported");
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void completedResultThatCannotBeMappedFailsWithCallbackZero() {
        TaskModel task = startCreatedTask(5000L);
        enqueueTaskStatus("\"status\":\"completed\",\"result\":{\"isError\":false}");

        poll(task);

        assertPollFailed(task, "completed with a result that cannot be mapped");
    }

    // ---- 5.4 Cancellation on workflow termination ----------------------------------------------

    @Test
    void terminationSendsTasksCancelForThePollingTask() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        takeRequests(2);
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        cancelCallTool(task);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        RecordedRequest cancel = takeRequest();
        JsonNode body = json(cancel.getBody().readUtf8());
        assertEquals("tasks/cancel", body.get("method").asString());
        assertEquals("t-1", body.at("/params/taskId").asString());
        assertEquals("tasks/cancel", cancel.getHeader("Mcp-Method"));
        assertEquals("t-1", cancel.getHeader("Mcp-Name"));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void noCancelWithoutARemoteTask() {
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));
        TaskModel immediate = runCallTool(optedInInput());
        immediate.setStatus(TaskModel.Status.IN_PROGRESS);

        cancelCallTool(immediate);

        assertEquals(TaskModel.Status.CANCELED, immediate.getStatus());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void noCancelForALegacyCallOrForListMcpTools() throws Exception {
        TaskModel legacy = pollingTaskModel("CALL_MCP_TOOL", defaultInput());
        TaskModel list = pollingTaskModel("LIST_MCP_TOOLS", optedInInput());

        cancelCallTool(legacy);
        cancel(
                MCPWorkers.class.getMethod("listTools", MCPListToolsRequest.class),
                "LIST_MCP_TOOLS",
                list);

        assertEquals(TaskModel.Status.CANCELED, legacy.getStatus());
        assertEquals(TaskModel.Status.CANCELED, list.getStatus());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void failingCancelIsLoggedAndTerminationContinues() throws Exception {
        TaskModel task = startCreatedTask(5000L);
        server.enqueue(new MockResponse().setResponseCode(503).setBody("busy"));

        cancelCallTool(task);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        takeRequests(2);
        assertEquals(
                "tasks/cancel", json(takeRequest().getBody().readUtf8()).get("method").asString());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void hangingCancelReturnsWithinItsTimeLimit() {
        TaskModel task = startCreatedTask(5000L);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        long started = System.nanoTime();
        cancelCallTool(task);
        long elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000L;

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        assertEquals(3, server.getRequestCount());
        assertTrue(elapsedSeconds < 10, "cancel took " + elapsedSeconds + " s");
    }

    @Test
    void remoteTaskLeftByAnEarlierExecutionIsNotCancelled() {
        TaskModel task = startCreatedTask(5000L);
        // The engine reset the task in place (retryCount + 1, output kept), then terminated the
        // workflow again before the reset task ran.
        task.setRetryCount(task.getRetryCount() + 1);

        cancelCallTool(task);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void cancelCarriesUserHeadersAndProtocolHeaders() throws Exception {
        Map<String, Object> input = new HashMap<>(optedInInput());
        input.put("headers", Map.of("Authorization", "Bearer t"));
        // State written before retryCount existed is still cancelled.
        TaskModel task = pollingTaskModel("CALL_MCP_TOOL", input);
        enqueueJson(result("{\"resultType\":\"complete\"}"));

        cancelCallTool(task);

        RecordedRequest cancel = takeRequest();
        assertEquals("Bearer t", cancel.getHeader("Authorization"));
        assertEquals("2026-07-28", cancel.getHeader("MCP-Protocol-Version"));
        assertEquals("tasks/cancel", cancel.getHeader("Mcp-Method"));
        assertEquals("t-1", cancel.getHeader("Mcp-Name"));
    }

    @Test
    void malformedCancelInputSendsNothingAndDoesNotThrow() {
        Map<String, Object> badOptions = new HashMap<>(defaultInput());
        badOptions.put("mcpOptions", "2026-07-28");
        Map<String, Object> badArguments = new HashMap<>(optedInInput());
        badArguments.put("arguments", "not an object");
        TaskModel blankTaskId = pollingTaskModel("CALL_MCP_TOOL", optedInInput());
        blankTaskId.getOutputData().put("mcpTask", Map.of("taskId", " ", "pollFailures", 0));

        for (TaskModel task :
                List.of(
                        pollingTaskModel("CALL_MCP_TOOL", badOptions),
                        pollingTaskModel("CALL_MCP_TOOL", badArguments),
                        blankTaskId)) {
            cancelCallTool(task);
            assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        }
        assertEquals(0, server.getRequestCount());
    }

    // ---- S6 and nits: worker-level x-mcp-header, retry and input checks ------------------------

    @Test
    void annotatedParameterIsMirroredOnTheToolsCall() throws Exception {
        enqueueJson(result(regionToolList(true)));
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));

        TaskModel task = runCallTool(inputWithArguments(Map.of("region", "us-west1")));

        assertCompleted(task);
        takeRequest();
        assertEquals("us-west1", takeRequest().getHeader("Mcp-Param-Region"));
    }

    @Test
    void headerMismatchRetryUsesTheRefreshedSchema() throws Exception {
        enqueueJson(result(regionToolList(false)));
        enqueueRpcError(400, -32020, "Header mismatch: Mcp-Param-Region missing");
        enqueueJson(result(regionToolList(true)));
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));

        TaskModel task = runCallTool(inputWithArguments(Map.of("region", "us-west1")));

        assertCompleted(task);
        takeRequest();
        assertNull(takeRequest().getHeader("Mcp-Param-Region"));
        takeRequest();
        assertEquals("us-west1", takeRequest().getHeader("Mcp-Param-Region"));
    }

    @Test
    void nonAsciiToolNameReachesTheServer() throws Exception {
        enqueueJson(result("{\"tools\":[{\"name\":\"météo\"}]}"));
        enqueueJson(result("{\"resultType\":\"complete\",\"content\":[]}"));
        Map<String, Object> input = new HashMap<>(optedInInput());
        input.put("method", "météo");

        TaskModel task = runCallTool(input);

        assertCompleted(task);
        takeRequest();
        RecordedRequest call = takeRequest();
        assertEquals("=?base64?bcOpdMOpbw==?=", call.getHeader("Mcp-Name"));
        assertEquals("météo", json(call.getBody().readUtf8()).at("/params/name").asString());
    }

    @Test
    void missingMethodIsRejectedBeforeAnyRequest() {
        Map<String, Object> input = new HashMap<>(optedInInput());
        input.remove("method");

        TaskModel task = runCallTool(input);

        assertFailedContaining(task, "CALL_MCP_TOOL requires the input 'method'");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void unsupportedProtocolVersionWithoutDataSaysSo() {
        enqueueToolList();
        server.enqueue(
                rpcErrorResponse(400, "{\"code\":-32022,\"message\":\"Unsupported protocol\"}"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "supported versions: not given by the server");
    }

    @Test
    void headerMismatchOnTheSchemaLookupNamesTheRejectedRequest() {
        enqueueRpcError(400, -32020, "Header mismatch");
        enqueueRpcError(400, -32020, "Header mismatch");

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "rejected tools/list for tool 'echo'");
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void protocolVersionThatIsAnObjectIsReportedAsAnObject() {
        TaskModel task = runCallTool(inputWithMcpOptions(Map.of("protocolVersion", Map.of())));

        assertTerminalFailureContaining(task, "expected a string, got an object");
        assertEquals(0, server.getRequestCount());
    }

    // ---- S2: reason parity for failures a legacy call can also have ---------------------------

    @Test
    void invalidServerUrlFailsWithTheOptOutReason() {
        Map<String, Object> legacy = new HashMap<>(defaultInput());
        legacy.put("mcpServer", "ftp://example.invalid/mcp");
        Map<String, Object> optedIn = new HashMap<>(optedInInput());
        optedIn.put("mcpServer", "ftp://example.invalid/mcp");

        TaskModel optOut = runCallTool(legacy);
        TaskModel withVersion = runCallTool(optedIn);

        assertEquals(TaskModel.Status.FAILED, optOut.getStatus());
        assertFailed(withVersion, optOut.getReasonForIncompletion());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void oversizedResponseFailsWithTheOptOutReason() {
        okio.Buffer oversized = new okio.Buffer();
        oversized.write(new byte[10 * 1024 * 1024 + 1]);
        server.enqueue(new MockResponse().setBody(oversized.clone()));
        TaskModel optOut = runCallTool(defaultInput());

        enqueueToolList();
        server.enqueue(new MockResponse().setBody(oversized));
        TaskModel optedIn = runCallTool(optedInInput());

        assertTrue(
                optOut.getReasonForIncompletion().contains("10 MiB payload limit"),
                optOut.getReasonForIncompletion());
        assertFailed(optedIn, optOut.getReasonForIncompletion());
    }

    @Test
    void invalidJsonResponseFailsWithTheOptOutPrefix() {
        enqueueToolList();
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("not json"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(
                task, "Task execution failed: Failed to call MCP tool 'echo' on " + url + ": ");
    }

    @Test
    void failureOnlyTheNewFormCanHaveKeepsItsOwnReason() {
        enqueueToolList();
        enqueueJson(result("{\"resultType\":\"something_else\",\"content\":[]}"));

        TaskModel task = runCallTool(optedInInput());

        assertFailedContaining(task, "'something_else'");
        assertFalse(
                task.getReasonForIncompletion().contains("Failed to call MCP tool"),
                task.getReasonForIncompletion());
    }

    @Test
    void toolsCallServerErrorFailsWithTheOptOutReason() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        TaskModel optOut = runCallTool(defaultInput());

        enqueueToolList();
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        TaskModel optedIn = runCallTool(optedInInput());

        assertFailed(optedIn, optOut.getReasonForIncompletion());
    }

    @Test
    void toolsCallJsonRpcErrorFailsWithTheOptOutReason() {
        String error =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32603,\"message\":\"x\"}}";
        enqueueJson(error);
        TaskModel optOut = runCallTool(defaultInput());

        enqueueToolList();
        enqueueJson(error);
        TaskModel optedIn = runCallTool(optedInInput());

        assertFailed(optedIn, optOut.getReasonForIncompletion());
    }

    @Test
    void toolsCallConnectionFailureFailsWithTheOptOutReason() {
        // OkHttp retries a request after a dropped connection, so a queued response would be
        // consumed by the retry. Answer by JSON-RPC method instead: every tools/call disconnects.
        // DISCONNECT_AFTER_REQUEST is applied after dispatch; DISCONNECT_AT_START is not.
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        String method = json(request.getBody().readUtf8()).get("method").asString();
                        if ("tools/list".equals(method)) {
                            return new MockResponse()
                                    .setHeader("Content-Type", "application/json")
                                    .setBody(
                                            result(
                                                    "{\"tools\":[{\"name\":\"echo\","
                                                            + "\"inputSchema\":{\"type\":\"object\"}}]}"));
                        }
                        return new MockResponse()
                                .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
                    }
                });
        TaskModel optOut = runCallTool(defaultInput());

        TaskModel optedIn = runCallTool(optedInInput());

        assertEquals(TaskModel.Status.FAILED, optOut.getStatus());
        String reason = optOut.getReasonForIncompletion();
        assertTrue(reason.contains(" — caused by: "), reason);
        assertFalse(reason.contains("JSON-RPC") || reason.contains("HTTP "), reason);
        assertFailed(optedIn, reason);
    }

    // ---- helpers --------------------------------------------------------------------------------

    /**
     * Runs the same {@code CallToolResult} as a legacy call and with {@code
     * mcpOptions.protocolVersion}, and asserts the same status and output, with no polling-state
     * key.
     */
    private void assertOptedInMatchesOptOut(String optOutResponse, String optedInResponse)
            throws Exception {
        enqueueJson(optOutResponse);
        TaskModel optOut = runCallTool(defaultInput());

        enqueueToolList();
        enqueueJson(optedInResponse);
        TaskModel optedIn = runCallTool(optedInInput());

        assertEquals(optOut.getStatus(), optedIn.getStatus());
        assertCompleted(optedIn);
        assertEquals(output(optOut), output(optedIn));
        assertFalse(optedIn.getOutputData().containsKey("mcpTask"));
    }

    /** Runs the first execution of an opted-in call whose server creates task "t-1". */
    private TaskModel startCreatedTask(Long pollIntervalMs) {
        enqueueToolList();
        enqueueJson(
                result(
                        "{\"resultType\":\"task\",\"taskId\":\"t-1\",\"status\":\"working\""
                                + (pollIntervalMs != null
                                        ? ",\"pollIntervalMs\":" + pollIntervalMs
                                        : "")
                                + "}"));
        return runCallTool(optedInInput());
    }

    private void enqueueTaskStatus(String fields) {
        enqueueJson(result("{\"resultType\":\"complete\",\"taskId\":\"t-1\"," + fields + "}"));
    }

    /** Cancels a CALL_MCP_TOOL task as WorkflowExecutorOps.cancelNonTerminalTasks does. */
    private void cancelCallTool(TaskModel task) {
        // WorkflowExecutorOps.cancelNonTerminalTasks sets CANCELED before it calls the hook.
        task.setStatus(TaskModel.Status.CANCELED);
        try {
            cancel(
                    MCPWorkers.class.getMethod("callTool", MCPToolCallRequest.class),
                    "CALL_MCP_TOOL",
                    task);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cancel(Method method, String taskType, TaskModel task) {
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        taskType, method, workers, method.getAnnotation(WorkerTask.class));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        systemTask.cancel(workflow, task, null);
    }

    /** An IN_PROGRESS task model whose output holds mcpTask "t-1", without running anything. */
    private static TaskModel pollingTaskModel(String taskType, Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-x");
        task.setWorkflowInstanceId("workflow-1");
        task.setTaskType(taskType);
        task.setInputData(new HashMap<>(input));
        Map<String, Object> output = new HashMap<>();
        output.put("mcpTask", Map.of("taskId", "t-1", "pollFailures", 0));
        task.setOutputData(output);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return task;
    }

    /** Runs a first execution (start) on an existing task model, as a re-scheduled task does. */
    private void startAgain(TaskModel task) {
        try {
            Method method = MCPWorkers.class.getMethod("callTool", MCPToolCallRequest.class);
            AnnotatedWorkflowSystemTask systemTask =
                    new AnnotatedWorkflowSystemTask(
                            "CALL_MCP_TOOL",
                            method,
                            workers,
                            method.getAnnotation(WorkerTask.class));
            WorkflowModel workflow = new WorkflowModel();
            workflow.setWorkflowId("workflow-1");
            systemTask.start(workflow, task, null);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs a later execution of the same task, as AsyncSystemTaskExecutor does. */
    private void poll(TaskModel task) {
        poll(workers, task);
    }

    private void poll(MCPWorkers instance, TaskModel task) {
        try {
            Method method = MCPWorkers.class.getMethod("callTool", MCPToolCallRequest.class);
            AnnotatedWorkflowSystemTask systemTask =
                    new AnnotatedWorkflowSystemTask(
                            "CALL_MCP_TOOL",
                            method,
                            instance,
                            method.getAnnotation(WorkerTask.class));
            WorkflowModel workflow = new WorkflowModel();
            workflow.setWorkflowId("workflow-1");
            systemTask.execute(workflow, task, null);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private void takeRequests(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            takeRequest();
        }
    }

    private static void assertInProgress(TaskModel task, long callbackAfterSeconds) {
        assertEquals(
                TaskModel.Status.IN_PROGRESS, task.getStatus(), task.getReasonForIncompletion());
        assertEquals(callbackAfterSeconds, task.getCallbackAfterSeconds());
        assertNull(task.getReasonForIncompletion());
    }

    private static void assertPollFailed(TaskModel task, String... fragments) {
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        String reason = task.getReasonForIncompletion();
        assertNotNull(reason);
        for (String fragment : fragments) {
            assertTrue(reason.contains(fragment), reason);
        }
        assertEquals("t-1", output(task).at("/mcpTask/taskId").asString());
    }

    private Map<String, Object> inputWithArguments(Map<String, Object> arguments) {
        Map<String, Object> input = new HashMap<>(optedInInput());
        input.put("arguments", arguments);
        return input;
    }

    /** A tools/list result for tool "echo", with or without x-mcp-header on "region". */
    private static String regionToolList(boolean annotated) {
        return "{\"tools\":[{\"name\":\"echo\",\"inputSchema\":{\"type\":\"object\","
                + "\"properties\":{\"region\":{\"type\":\"string\""
                + (annotated ? ",\"x-mcp-header\":\"Region\"" : "")
                + "}}}}]}";
    }

    private void enqueueToolList() {
        enqueueJson(
                result(
                        "{\"tools\":[{\"name\":\"echo\",\"inputSchema\":{\"type\":\"object\","
                                + "\"properties\":{\"q\":{\"type\":\"string\"}}}}]}"));
    }

    private void enqueueRpcError(int httpStatus, int code, String message) {
        server.enqueue(
                rpcErrorResponse(
                        httpStatus, "{\"code\":" + code + ",\"message\":\"" + message + "\"}"));
    }

    private static MockResponse rpcErrorResponse(int httpStatus, String errorJson) {
        return new MockResponse()
                .setResponseCode(httpStatus)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":" + errorJson + "}");
    }

    private List<String> methodsOfRequests(int count) throws InterruptedException {
        List<String> methods = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            methods.add(json(takeRequest().getBody().readUtf8()).get("method").asString());
        }
        return methods;
    }

    /** An invalid mcpOptions: a terminal failure, because a retry cannot fix it. */
    private static void assertTerminalFailureContaining(TaskModel task, String... fragments) {
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        String reason = task.getReasonForIncompletion();
        assertNotNull(reason);
        for (String fragment : fragments) {
            assertTrue(reason.contains(fragment), reason);
        }
        assertTrue(task.getOutputData().isEmpty());
    }

    private static void assertFailedContaining(TaskModel task, String... fragments) {
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        String reason = task.getReasonForIncompletion();
        assertNotNull(reason);
        for (String fragment : fragments) {
            assertTrue(reason.contains(fragment), reason);
        }
        assertTrue(task.getOutputData().isEmpty());
    }

    private Map<String, Object> optedInInput() {
        Map<String, Object> input = new HashMap<>(defaultInput());
        input.put("mcpOptions", Map.of("protocolVersion", "2026-07-28"));
        return input;
    }

    private Map<String, Object> inputWithMcpOptions(Object mcpOptions) {
        Map<String, Object> input = new HashMap<>(defaultInput());
        input.put("mcpOptions", mcpOptions);
        return input;
    }

    private Map<String, Object> defaultInput() {
        return Map.of("mcpServer", url, "method", "echo", "arguments", Map.of("q", "x"));
    }

    private TaskModel runCallTool(Map<String, Object> input) {
        try {
            return run(
                    "CALL_MCP_TOOL",
                    MCPWorkers.class.getMethod("callTool", MCPToolCallRequest.class),
                    input);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private TaskModel run(String taskType, Method method, Map<String, Object> input) {
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(
                        taskType, method, workers, method.getAnnotation(WorkerTask.class));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setWorkflowInstanceId("workflow-1");
        task.setReferenceTaskName("mcp_ref");
        task.setTaskType(taskType);
        task.setInputData(new HashMap<>(input));
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);
        systemTask.start(workflow, task, null);
        return task;
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "expected an HTTP request");
        return request;
    }

    private void enqueueJson(String body) {
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }

    private static String result(String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}";
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    /** The task output as it is persisted (JSON text), so number types compare by value. */
    private static JsonNode output(TaskModel task) {
        return MAPPER.readTree(MAPPER.writeValueAsString(task.getOutputData()));
    }

    private static void assertCompleted(TaskModel task) {
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        assertEquals(0, task.getCallbackAfterSeconds());
        assertNull(task.getReasonForIncompletion());
    }

    private static void assertFailed(TaskModel task, String reason) {
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(0, task.getCallbackAfterSeconds());
        assertEquals(reason, task.getReasonForIncompletion());
        assertTrue(task.getOutputData().isEmpty());
    }

    private static void assertNoMcpProtocolHeaders(RecordedRequest request) {
        assertNull(request.getHeader("MCP-Protocol-Version"));
        assertNull(request.getHeader("Mcp-Method"));
        assertNull(request.getHeader("Mcp-Name"));
        for (String name : request.getHeaders().names()) {
            assertTrue(!name.toLowerCase().startsWith("mcp-param-"), "unexpected header " + name);
        }
    }
}
