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
package org.conductoross.conductor.ai.mcp.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.ai.mcp.McpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerMethodsTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();

    private McpServerProperties properties;
    private final List<ObjectNode> started = new ArrayList<>();
    private final Map<String, Map<String, Object>> metadata = new HashMap<>();

    /** Statuses the fake store returns, one per read; the last one repeats. */
    private final List<Workflow.WorkflowStatus> statuses = new ArrayList<>();

    private final AtomicInteger reads = new AtomicInteger();
    private final List<String> terminated = new ArrayList<>();

    /** Names exposed for tasks/* but not offered for new calls. */
    private final Set<String> exposedOnly = new HashSet<>();

    /** Makes the exposure check fail, as a metadata store outage would. */
    private boolean storeDown;

    /** Makes the per-tool metadata lookup of tools/call fail. */
    private boolean metadataStoreDown;

    /** Makes the execution read of tasks/* fail with a non-NotFound error. */
    private boolean executionStoreDown;

    /** The execution ends between the read and the terminate: the engine refuses. */
    private boolean terminateConflict;

    private String executionName = "order_lookup";
    private boolean unknownExecution;

    /** The output of a completed execution; {@code null} keeps the default. */
    private Map<String, Object> completedOutput;

    /** An output value that cannot be written as JSON. */
    public static final class Unreadable {
        public String getValue() {
            throw new IllegalStateException("cannot read");
        }
    }

    @BeforeEach
    void setUp() {
        properties = new McpServerProperties();
        statuses.clear();
        started.clear();
        metadata.clear();
        reads.set(0);
        storeDown = false;
        terminated.clear();
        executionName = "order_lookup";
        unknownExecution = false;
        completedOutput = null;
    }

    private JsonNode json(String text) {
        return mapper.readTree(text);
    }

    private McpToolSource source(McpTool... tools) {
        return new McpToolSource() {
            @Override
            public List<McpTool> listTools() {
                return List.of(tools);
            }

            @Override
            public Optional<McpTool> findTool(String name) {
                return listTools().stream().filter(t -> t.name().equals(name)).findFirst();
            }

            @Override
            public boolean exposes(String name) {
                if (storeDown) {
                    throw new IllegalStateException("store unavailable");
                }
                return findTool(name).isPresent() || exposedOnly.contains(name);
            }

            @Override
            public Map<String, Object> metadata(String name) {
                if (metadataStoreDown) {
                    throw new IllegalStateException("metadata store unavailable");
                }
                return metadata.get(name);
            }

            @Override
            public String start(String name, ObjectNode arguments) {
                if ("broken".equals(name)) {
                    throw new IllegalStateException("No such workflow defined. name=broken");
                }
                if ("strict".equals(name)) {
                    throw new org.conductoross.conductor.core.exception.SchemaValidationException(
                            "Input of workflow strict does not match schema: $.qty: string found,"
                                    + " integer expected");
                }
                started.add(arguments);
                return "exec-1";
            }
        };
    }

    private Workflow execution() {
        int read = reads.getAndIncrement();
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("exec-1");
        WorkflowDef def = new WorkflowDef();
        def.setName(executionName);
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(statuses.get(Math.min(read, statuses.size() - 1)));
        workflow.setCreateTime(1_700_000_000_000L);
        workflow.setUpdateTime(1_700_000_005_000L);
        if (workflow.getStatus() == Workflow.WorkflowStatus.COMPLETED) {
            workflow.setOutput(
                    completedOutput != null ? completedOutput : Map.of("orderId", "o-1"));
        }
        if (workflow.getStatus() == Workflow.WorkflowStatus.FAILED) {
            workflow.setReasonForIncompletion("card declined");
        }
        if (workflow.getStatus() == Workflow.WorkflowStatus.TIMED_OUT) {
            // The engine's own text (DeciderService).
            workflow.setReasonForIncompletion(
                    "Workflow timed out after 60 seconds. Timeout configured as 60 seconds.");
        }
        return workflow;
    }

    private McpServerMethods methods(McpTool... tools) {
        McpExecutions executions =
                new McpExecutions() {
                    @Override
                    public Workflow get(String executionId) {
                        if (unknownExecution) {
                            throw new NotFoundException(
                                    "No such workflow found by id: " + executionId);
                        }
                        if (executionStoreDown) {
                            throw new IllegalStateException("execution store unavailable");
                        }
                        return execution();
                    }

                    @Override
                    public void terminate(String executionId, String reason) {
                        if (terminateConflict) {
                            throw new ConflictException(
                                    "Cannot terminate a COMPLETED workflow. Workflow ID: "
                                            + executionId);
                        }
                        terminated.add(executionId + ":" + reason);
                    }
                };
        return new McpServerMethods(
                new McpToolRegistry(List.of(source(tools))),
                mapper,
                properties,
                new McpResults(mapper, properties),
                executions);
    }

    private static McpTool tool(String name) {
        return new McpTool(
                name,
                McpTool.defaultDescription(name),
                new ObjectMapperProvider()
                        .getObjectMapper()
                        .createObjectNode()
                        .put("type", "object"),
                McpTool.Kind.WORKFLOW);
    }

    private McpServerProtocol.McpCall call(String name, String arguments, boolean tasks) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", name);
        if (arguments != null) {
            params.set("arguments", json(arguments));
        }
        return new McpServerProtocol.McpCall(
                mapper.getNodeFactory().numberNode(1),
                McpMethod.TOOLS_CALL,
                params,
                mapper.createObjectNode(),
                tasks,
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
    }

    private static String text(JsonNode result) {
        return result.get("content").get(0).get("text").asString();
    }

    // ---- tools/list ---------------------------------------------------------------------------

    @Test
    void toolsList_returnsNameDescriptionAndSchema_inOneResult() {
        McpTool tool =
                new McpTool(
                        "order_lookup",
                        "Look up an order",
                        json("{\"type\":\"object\"}"),
                        McpTool.Kind.WORKFLOW);
        JsonNode result = methods(tool).toolsList(call("x", null, false)).block();

        assertThat(result.get("tools")).hasSize(1);
        JsonNode listed = result.get("tools").get(0);
        assertThat(listed.get("name").asString()).isEqualTo("order_lookup");
        assertThat(listed.get("description").asString()).isEqualTo("Look up an order");
        assertThat(listed.get("inputSchema").get("type").asString()).isEqualTo("object");
        assertThat(result.has("nextCursor")).isFalse();
        assertThat(result.get("ttlMs").asInt()).isZero();
        assertThat(result.get("cacheScope").asString()).isEqualTo("public");
    }

    @Test
    void sameShapeForBothKinds() {
        McpTool workflow = tool("order_lookup");
        McpTool agent =
                new McpTool(
                        "support_bot",
                        McpTool.defaultDescription("support_bot"),
                        json("{\"type\":\"object\"}"),
                        McpTool.Kind.AGENT);
        JsonNode tools =
                methods(workflow, agent).toolsList(call("x", null, false)).block().get("tools");
        assertThat(tools.get(0).propertyNames())
                .containsExactlyElementsOf(tools.get(1).propertyNames());
        assertThat(tools.toString()).doesNotContain("WORKFLOW").doesNotContain("AGENT");
        assertThat(tools.get(1).get("description").asString())
                .isEqualTo("Conductor tool 'support_bot'");
    }

    @Test
    void handlers_registerToolsListAndCall() {
        assertThat(methods().handlers()).containsKeys(McpMethod.TOOLS_LIST, McpMethod.TOOLS_CALL);
    }

    // ---- tools/call: start and quick results ----------------------------------------------

    @Test
    void quickWorkflow_givesTheOutput_withOrWithoutTasks() {
        statuses.add(Workflow.WorkflowStatus.COMPLETED);
        for (boolean tasks : new boolean[] {true, false}) {
            JsonNode result =
                    methods(tool("order_lookup"))
                            .toolsCall(call("order_lookup", "{\"orderId\":\"o-1\"}", tasks))
                            .block();
            assertThat(result.get("resultType").asString()).isEqualTo("complete");
            assertThat(result.get("isError").asBoolean()).isFalse();
            assertThat(result.get("structuredContent").get("orderId").asString()).isEqualTo("o-1");
            assertThat(json(text(result)).get("orderId").asString()).isEqualTo("o-1");
            assertThat(result.has("taskId")).isFalse();
        }
        assertThat(started.get(0).get("orderId").asString()).isEqualTo("o-1");
    }

    @Test
    void quickFailure_isAnErrorResultWithReasonAndExecutionId() {
        statuses.add(Workflow.WorkflowStatus.FAILED);
        JsonNode result =
                methods(tool("order_lookup")).toolsCall(call("order_lookup", "{}", false)).block();
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(text(result)).contains("FAILED").contains("card declined").contains("exec-1");
        assertThat(text(result).toLowerCase()).doesNotContain("workflow").doesNotContain("agent");
    }

    @Test
    void timeout_isSaidInToolTerms_withoutTheEngineText() {
        statuses.add(Workflow.WorkflowStatus.TIMED_OUT);
        JsonNode result =
                methods(tool("order_lookup")).toolsCall(call("order_lookup", "{}", false)).block();
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(text(result)).isEqualTo("The call timed out. Execution id: exec-1");
    }

    @Test
    void missingArguments_startWithEmptyInput() {
        statuses.add(Workflow.WorkflowStatus.COMPLETED);
        methods(tool("order_lookup")).toolsCall(call("order_lookup", null, false)).block();
        assertThat(started.get(0).isEmpty()).isTrue();
    }

    @Test
    void unknownTool_isInvalidParams_andStartsNothing() {
        assertThatThrownBy(
                        () ->
                                methods(tool("order_lookup"))
                                        .toolsCall(call("other", "{}", false))
                                        .block())
                .isInstanceOfSatisfying(
                        McpServerException.class, e -> assertThat(e.getCode()).isEqualTo(-32602));
        assertThat(started).isEmpty();
    }

    @Test
    void nonObjectArguments_areInvalidParams() {
        assertThatThrownBy(
                        () ->
                                methods(tool("order_lookup"))
                                        .toolsCall(call("order_lookup", "[1]", false))
                                        .block())
                .isInstanceOfSatisfying(
                        McpServerException.class, e -> assertThat(e.getCode()).isEqualTo(-32602));
    }

    @Test
    void startFailure_isAnErrorResult_withoutEngineText() {
        JsonNode result = methods(tool("broken")).toolsCall(call("broken", "{}", false)).block();
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(text(result)).isEqualTo("The call could not be started.");
    }

    @Test
    void mismatchedParamHeader_isHeaderMismatch400_andStartsNothing() {
        McpTool region = new McpTool("execute_sql", "", json(REGION_SCHEMA), McpTool.Kind.WORKFLOW);
        McpServerProtocol.McpCall call = call("execute_sql", "{\"region\":\"us-west1\"}", false);
        call.headers().put("Mcp-Param-Region", "eu-west1");
        assertThatThrownBy(() -> methods(region).toolsCall(call).block())
                .isInstanceOfSatisfying(
                        McpServerException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo(-32020);
                            assertThat(e.getHttpStatus()).isEqualTo(400);
                        });
        assertThat(started).isEmpty();
    }

    @Test
    void headerArgumentThatCannotBeAHeader_isInvalidParams_andStartsNothing() {
        McpTool region = new McpTool("execute_sql", "", json(REGION_SCHEMA), McpTool.Kind.WORKFLOW);
        McpServerProtocol.McpCall call = call("execute_sql", "{\"region\":{\"a\":1}}", false);
        assertThatThrownBy(() -> methods(region).toolsCall(call).block())
                .isInstanceOfSatisfying(
                        McpServerException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo(-32602);
                            assertThat(e.getHttpStatus()).isEqualTo(200);
                            assertThat(e.getMessage()).contains("region");
                        });
        assertThat(started).isEmpty();
    }

    // ---- tools/call: long calls -----------------------------------------------------------

    @Test
    void zeroSyncWait_withTasks_givesAFlatCreateTaskResult() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        metadata.put("slow_compute", Map.of("mcp.syncWaitSeconds", 0));
        JsonNode result =
                methods(tool("slow_compute")).toolsCall(call("slow_compute", "{}", true)).block();
        assertThat(result.get("resultType").asString()).isEqualTo("task");
        assertThat(result.get("taskId").asString()).isEqualTo("exec-1");
        assertThat(result.get("status").asString()).isEqualTo("working");
        assertThat(result.get("createdAt").asString()).isEqualTo("2023-11-14T22:13:20Z");
        assertThat(result.get("lastUpdatedAt").asString()).isEqualTo("2023-11-14T22:13:25Z");
        assertThat(result.get("ttlMs").isIntegralNumber()).isTrue();
        assertThat(result.get("ttlMs").asLong()).isEqualTo(86_400_000L);
        assertThat(result.get("pollIntervalMs").asLong()).isEqualTo(5000L);
        assertThat(result.has("result")).isFalse();
        assertThat(result.has("task")).isFalse();
        assertThat(result.has("ttl")).isFalse();
        assertThat(result.has("pollInterval")).isFalse();
    }

    @Test
    void runningPastTheSyncWait_withTasks_givesATask() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        metadata.put("slow", Map.of("mcp.syncWaitSeconds", 1));
        long before = System.currentTimeMillis();
        JsonNode result = methods(tool("slow")).toolsCall(call("slow", "{}", true)).block();
        assertThat(System.currentTimeMillis() - before).isGreaterThanOrEqualTo(900);
        assertThat(result.get("resultType").asString()).isEqualTo("task");
        assertThat(reads.get()).isGreaterThan(1);
    }

    @Test
    void endingWithinTheSyncWait_withTasks_returnsTheResultBeforeTheWaitEnds() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        statuses.add(Workflow.WorkflowStatus.COMPLETED);
        metadata.put("slow", Map.of("mcp.syncWaitSeconds", 10));
        long before = System.currentTimeMillis();
        JsonNode result = methods(tool("slow")).toolsCall(call("slow", "{}", true)).block();
        assertThat(System.currentTimeMillis() - before).isLessThan(5_000);
        assertThat(result.get("resultType").asString()).isEqualTo("complete");
        assertThat(result.get("isError").asBoolean()).isFalse();
        assertThat(reads.get()).isEqualTo(3);
    }

    @Test
    void longCallWithoutTasks_blocksToTheCap_thenIsError_andTheExecutionKeepsRunning() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        metadata.put("slow", Map.of("mcp.syncWaitSeconds", 0));
        properties.setBlockingCapSeconds(1);
        long before = System.currentTimeMillis();
        JsonNode result = methods(tool("slow")).toolsCall(call("slow", "{}", false)).block();
        assertThat(System.currentTimeMillis() - before).isGreaterThanOrEqualTo(900);
        assertThat(result.get("resultType").asString()).isEqualTo("complete");
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(text(result)).isEqualTo("The call is still running. Execution id: exec-1");
        assertThat(result.has("taskId")).isFalse();
    }

    @Test
    void syncWaitLongerThanTheCap_withoutTasks_stopsAtTheCap() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        metadata.put("slow", Map.of("mcp.syncWaitSeconds", 5));
        properties.setBlockingCapSeconds(1);
        long before = System.currentTimeMillis();
        JsonNode result = methods(tool("slow")).toolsCall(call("slow", "{}", false)).block();
        long elapsed = System.currentTimeMillis() - before;
        assertThat(elapsed).isGreaterThanOrEqualTo(900).isLessThan(3000);
        assertThat(text(result)).isEqualTo("The call is still running. Execution id: exec-1");
    }

    @Test
    void syncWaitLongerThanTheCap_legacyClient_stopsAtTheCap() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        metadata.put("slow", Map.of("mcp.syncWaitSeconds", 5));
        properties.setBlockingCapSeconds(1);
        McpServerProtocol.McpCall modern = call("slow", "{}", false);
        McpServerProtocol.McpCall legacy =
                new McpServerProtocol.McpCall(
                        modern.id(),
                        modern.method(),
                        modern.params(),
                        modern.meta(),
                        false,
                        modern.headers(),
                        true);
        long before = System.currentTimeMillis();
        JsonNode result = methods(tool("slow")).toolsCall(legacy).block();
        assertThat(System.currentTimeMillis() - before)
                .isGreaterThanOrEqualTo(900)
                .isLessThan(3000);
        assertThat(text(result)).isEqualTo("The call is still running. Execution id: exec-1");
    }

    @Test
    void withoutTasks_aTaskSupportingToolFallsThroughToASyncResult() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        statuses.add(Workflow.WorkflowStatus.COMPLETED);
        metadata.put("slow_compute", Map.of("mcp.syncWaitSeconds", 0));
        JsonNode result =
                methods(tool("slow_compute")).toolsCall(call("slow_compute", "{}", false)).block();
        assertThat(result.get("resultType").asString()).isEqualTo("complete");
        assertThat(result.get("isError").asBoolean()).isFalse();
    }

    @Test
    void zeroBlockingCap_withoutTasks_readsOnce_thenStillRunning() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        metadata.put("slow", Map.of("mcp.syncWaitSeconds", 0));
        properties.setBlockingCapSeconds(0);
        JsonNode result = methods(tool("slow")).toolsCall(call("slow", "{}", false)).block();
        assertThat(result.get("resultType").asString()).isEqualTo("complete");
        assertThat(text(result)).isEqualTo("The call is still running. Execution id: exec-1");
        // The first wait used the whole (zero) cap: one read at its deadline, none after it.
        assertThat(reads.get()).isEqualTo(1);
    }

    // ---- tasks/get and tasks/cancel ---------------------------------------------------------

    private McpServerProtocol.McpCall taskCall(McpMethod method, String taskId, boolean tasks) {
        ObjectNode params = mapper.createObjectNode();
        params.put("taskId", taskId);
        return new McpServerProtocol.McpCall(
                mapper.getNodeFactory().numberNode(2),
                method,
                params,
                mapper.createObjectNode(),
                tasks,
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
    }

    private JsonNode get(Workflow.WorkflowStatus status) {
        statuses.add(status);
        return methods(tool("order_lookup"))
                .tasksGet(taskCall(McpMethod.TASKS_GET, "exec-1", true))
                .block();
    }

    @Test
    void resultThatCannotBeBuilt_isFailed_withAStatusMessage() {
        completedOutput = Map.of("value", new Unreadable());
        JsonNode task = get(Workflow.WorkflowStatus.COMPLETED);
        assertThat(task.get("status").asString()).isEqualTo("failed");
        assertThat(task.get("error").get("code").asInt()).isEqualTo(-32603);
        assertThat(task.get("statusMessage").asString())
                .isEqualTo("The call result could not be read. Execution id: exec-1")
                .doesNotContainIgnoringCase("workflow")
                .doesNotContainIgnoringCase("agent");
    }

    @Test
    void storeErrorInTaskMethods_isAServerError_notAnUnknownTask() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        McpServerMethods methods = methods(tool(executionName));
        for (Runnable outage :
                new Runnable[] {() -> storeDown = true, () -> executionStoreDown = true}) {
            storeDown = false;
            executionStoreDown = false;
            outage.run();
            assertTaskMethodsFailAsServerErrors(methods);
        }
        assertThat(terminated).isEmpty();
    }

    @Test
    void storeErrorInTheMetadataLookup_failsTheCallBeforeAnythingStarts() {
        metadataStoreDown = true;
        assertThatThrownBy(
                        () ->
                                methods(tool("order_lookup"))
                                        .toolsCall(call("order_lookup", "{}", true))
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(McpServerException.class);
        // Nothing started, so a client retry cannot run the tool twice.
        assertThat(started).isEmpty();
    }

    private void assertTaskMethodsFailAsServerErrors(McpServerMethods methods) {
        for (McpMethod method :
                new McpMethod[] {
                    McpMethod.TASKS_GET, McpMethod.TASKS_CANCEL, McpMethod.TASKS_UPDATE
                }) {
            // Not an McpServerException: the protocol layer maps it to HTTP 500 / -32603.
            assertThatThrownBy(
                            () ->
                                    methods.handlers()
                                            .get(method)
                                            .handle(taskCall(method, "exec-1", true))
                                            .block())
                    .isInstanceOf(IllegalStateException.class)
                    .isNotInstanceOf(McpServerException.class);
        }
    }

    @Test
    void executionOfAToolExposedButNotCallable_canStillBeReadAndCancelled() {
        // For example a workflow whose schema got an invalid x-mcp-header while it ran.
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        exposedOnly.add(executionName);
        McpServerMethods methods = methods();

        JsonNode task = methods.tasksGet(taskCall(McpMethod.TASKS_GET, "exec-1", true)).block();
        assertThat(task.get("status").asString()).isEqualTo("working");
        methods.tasksCancel(taskCall(McpMethod.TASKS_CANCEL, "exec-1", true)).block();
        assertThat(terminated).hasSize(1);
        assertThatThrownBy(() -> methods.toolsCall(call(executionName, "{}", true)).block())
                .isInstanceOfSatisfying(
                        McpServerException.class, e -> assertThat(e.getCode()).isEqualTo(-32602));
    }

    @Test
    void pollARunningWorkflow_isWorking() {
        JsonNode task = get(Workflow.WorkflowStatus.RUNNING);
        assertThat(task.get("taskId").asString()).isEqualTo("exec-1");
        assertThat(task.get("status").asString()).isEqualTo("working");
        assertThat(task.get("pollIntervalMs").asLong()).isEqualTo(5000L);
        assertThat(task.get("ttlMs").asLong()).isEqualTo(86_400_000L);
        assertThat(task.get("createdAt").asString()).isEqualTo("2023-11-14T22:13:20Z");
        assertThat(task.has("result")).isFalse();
        assertThat(task.has("error")).isFalse();
    }

    @Test
    void workflowWaitingOnAHumanTask_isWorking_notInputRequired() {
        // An execution blocked on HUMAN or WAIT is RUNNING; v1 reports it as working.
        JsonNode task = get(Workflow.WorkflowStatus.RUNNING);
        assertThat(task.get("status").asString()).isEqualTo("working");
        assertThat(task.toString()).doesNotContain("input_required");
    }

    @Test
    void pausedWorkflow_isWorking() {
        assertThat(get(Workflow.WorkflowStatus.PAUSED).get("status").asString())
                .isEqualTo("working");
    }

    @Test
    void pollACompletedWorkflow_inlinesTheResult() {
        JsonNode task = get(Workflow.WorkflowStatus.COMPLETED);
        assertThat(task.get("status").asString()).isEqualTo("completed");
        JsonNode result = task.get("result");
        assertThat(result.get("isError").asBoolean()).isFalse();
        assertThat(result.get("structuredContent").get("orderId").asString()).isEqualTo("o-1");
        assertThat(result.get("content")).hasSize(1);
        assertThat(task.has("pollIntervalMs")).isFalse();
        assertThat(result.has("_meta")).isFalse();
    }

    @Test
    void pollAFailedWorkflow_isCompletedWithAnErrorResult() {
        for (Workflow.WorkflowStatus status :
                new Workflow.WorkflowStatus[] {
                    Workflow.WorkflowStatus.FAILED, Workflow.WorkflowStatus.TIMED_OUT
                }) {
            statuses.clear();
            JsonNode task = get(status);
            assertThat(task.get("status").asString()).isEqualTo("completed");
            assertThat(task.get("result").get("isError").asBoolean()).isTrue();
            assertThat(task.has("error")).isFalse();
        }
    }

    @Test
    void terminatedWorkflow_isCancelled() {
        JsonNode task = get(Workflow.WorkflowStatus.TERMINATED);
        assertThat(task.get("status").asString()).isEqualTo("cancelled");
        assertThat(task.has("result")).isFalse();
    }

    @Test
    void unknownTask_isInvalidParams() {
        unknownExecution = true;
        assertThatThrownBy(
                        () ->
                                methods(tool("order_lookup"))
                                        .tasksGet(
                                                taskCall(
                                                        McpMethod.TASKS_GET,
                                                        "nonexistent-12345",
                                                        true))
                                        .block())
                .isInstanceOfSatisfying(
                        McpServerException.class, e -> assertThat(e.getCode()).isEqualTo(-32602));
    }

    @Test
    void executionOfAnUnexposedTool_isAnUnknownTask() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        executionName = "internal_cleanup";
        assertThatThrownBy(
                        () ->
                                methods(tool("order_lookup"))
                                        .tasksGet(taskCall(McpMethod.TASKS_GET, "exec-1", true))
                                        .block())
                .isInstanceOfSatisfying(
                        McpServerException.class, e -> assertThat(e.getCode()).isEqualTo(-32602));
    }

    @Test
    void tasksMethodsWithoutTheExtension_areMissingCapability400() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        for (McpMethod method :
                new McpMethod[] {
                    McpMethod.TASKS_GET, McpMethod.TASKS_CANCEL, McpMethod.TASKS_UPDATE
                }) {
            McpServerMethods methods = methods(tool("order_lookup"));
            McpServerProtocol.McpCall call = taskCall(method, "exec-1", false);
            assertThatThrownBy(() -> methods.handlers().get(method).handle(call).block())
                    .isInstanceOfSatisfying(
                            McpServerException.class,
                            e -> {
                                assertThat(e.getCode()).isEqualTo(-32021);
                                assertThat(e.getHttpStatus()).isEqualTo(400);
                                assertThat(
                                                e.getData()
                                                        .get("requiredCapabilities")
                                                        .get("extensions")
                                                        .has("io.modelcontextprotocol/tasks"))
                                        .isTrue();
                            });
        }
        assertThat(terminated).isEmpty();
    }

    @Test
    void cancelARunningWorkflow_terminatesIt_andAcks() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        JsonNode ack =
                methods(tool("order_lookup"))
                        .tasksCancel(taskCall(McpMethod.TASKS_CANCEL, "exec-1", true))
                        .block();
        assertThat(ack.get("resultType").asString()).isEqualTo("complete");
        assertThat(ack.size()).isEqualTo(1);
        assertThat(terminated).containsExactly("exec-1:Cancelled by MCP client");
    }

    @Test
    void cancelAFinishedWorkflow_changesNothing_andAcks() {
        statuses.add(Workflow.WorkflowStatus.COMPLETED);
        JsonNode ack =
                methods(tool("order_lookup"))
                        .tasksCancel(taskCall(McpMethod.TASKS_CANCEL, "exec-1", true))
                        .block();
        assertThat(ack.get("resultType").asString()).isEqualTo("complete");
        assertThat(ack.size()).isEqualTo(1);
        assertThat(terminated).isEmpty();
    }

    @Test
    void cancelThatRacesWithCompletion_stillAcks() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        terminateConflict = true;
        JsonNode ack =
                methods(tool("order_lookup"))
                        .tasksCancel(taskCall(McpMethod.TASKS_CANCEL, "exec-1", true))
                        .block();
        assertThat(ack.get("resultType").asString()).isEqualTo("complete");
        assertThat(ack.size()).isEqualTo(1);
    }

    @Test
    void storeErrorAfterTheStart_keepsTheExecutionId() {
        // The execution is started; only the reads during the wait fail.
        executionStoreDown = true;
        JsonNode task =
                methods(tool("order_lookup")).toolsCall(call("order_lookup", "{}", true)).block();
        assertThat(task.get("resultType").asString()).isEqualTo("task");
        assertThat(task.get("taskId").asString()).isEqualTo("exec-1");
        assertThat(task.get("status").asString()).isEqualTo("working");

        properties.setBlockingCapSeconds(0);
        JsonNode running =
                methods(tool("order_lookup")).toolsCall(call("order_lookup", "{}", false)).block();
        assertThat(running.get("isError").asBoolean()).isTrue();
        assertThat(text(running)).contains("still running").contains("exec-1");
        assertThat(started).hasSize(2);
    }

    @Test
    void argumentsRejectedByTheSchema_giveTheReason_inToolTerms() {
        JsonNode result = methods(tool("strict")).toolsCall(call("strict", "{}", true)).block();
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(text(result))
                .startsWith("The call could not be started: ")
                .contains("$.qty: string found, integer expected")
                .doesNotContainIgnoringCase("workflow");
    }

    @Test
    void handlers_registerTaskMethods() {
        assertThat(methods().handlers())
                .containsKeys(McpMethod.TASKS_GET, McpMethod.TASKS_CANCEL, McpMethod.TASKS_UPDATE);
    }

    @Test
    void tasksUpdate_withTheExtension_acksAndChangesNothing() {
        statuses.add(Workflow.WorkflowStatus.RUNNING);
        McpServerProtocol.McpCall call = taskCall(McpMethod.TASKS_UPDATE, "exec-1", true);
        JsonNode ack = methods(tool("order_lookup")).tasksUpdate(call).block();
        assertThat(ack.get("resultType").asString()).isEqualTo("complete");
        assertThat(ack.size()).isEqualTo(1);
        assertThat(terminated).isEmpty();
    }

    @Test
    void tasksUpdate_ofAnUnknownTask_isInvalidParams() {
        unknownExecution = true;
        McpServerProtocol.McpCall call = taskCall(McpMethod.TASKS_UPDATE, "missing", true);
        assertThatThrownBy(() -> methods(tool("order_lookup")).tasksUpdate(call).block())
                .isInstanceOfSatisfying(
                        McpServerException.class, e -> assertThat(e.getCode()).isEqualTo(-32602));
    }

    // ---- Mcp-Param-* validation --------------------------------------------------------------

    private static final String REGION_SCHEMA =
            "{\"type\":\"object\",\"properties\":"
                    + "{\"region\":{\"type\":\"string\",\"x-mcp-header\":\"Region\"}}}";

    private McpTool regionTool() {
        return new McpTool("execute_sql", "", json(REGION_SCHEMA), McpTool.Kind.WORKFLOW);
    }

    private static Map<String, String> headers(String... pairs) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < pairs.length; i += 2) {
            headers.put(pairs[i], pairs[i + 1]);
        }
        return headers;
    }

    private ObjectNode args(String json) {
        return (ObjectNode) json(json);
    }

    @Test
    void paramHeaderMatchingTheArgument_isAccepted() {
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                regionTool(),
                                args("{\"region\":\"us-west1\"}"),
                                headers("Mcp-Param-Region", "us-west1"),
                                mapper))
                .isNull();
    }

    @Test
    void integerParamHeader_matchesTheArgumentByValue() {
        McpTool tool =
                new McpTool(
                        "scale",
                        "",
                        json(
                                "{\"type\":\"object\",\"properties\":{\"count\":"
                                        + "{\"type\":\"integer\",\"x-mcp-header\":\"Count\"}}}"),
                        McpTool.Kind.WORKFLOW);
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                tool,
                                args("{\"count\":42}"),
                                headers("Mcp-Param-Count", "42.0"),
                                mapper))
                .isNull();
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                tool,
                                args("{\"count\":42}"),
                                headers("Mcp-Param-Count", "43"),
                                mapper))
                .isNotNull();
    }

    @Test
    void paramHeaderNameIsCaseInsensitive() {
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                regionTool(),
                                args("{\"region\":\"us-west1\"}"),
                                headers("mcp-param-region", "us-west1"),
                                mapper))
                .isNull();
    }

    @Test
    void mismatchedParamHeader_isReported() {
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                regionTool(),
                                args("{\"region\":\"us-west1\"}"),
                                headers("Mcp-Param-Region", "eu-west1"),
                                mapper))
                .contains("Mcp-Param-Region");
    }

    @Test
    void missingParamHeader_isReported() {
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                regionTool(), args("{\"region\":\"us-west1\"}"), headers(), mapper))
                .contains("Mcp-Param-Region");
    }

    @Test
    void paramHeaderWithoutAnArgument_isReported() {
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                regionTool(),
                                args("{}"),
                                headers("Mcp-Param-Region", "us-west1"),
                                mapper))
                .contains("Mcp-Param-Region");
    }

    @Test
    void unannotatedToolWithoutParamHeaders_isAccepted() {
        McpTool plain = new McpTool("t", "", json("{\"type\":\"object\"}"), McpTool.Kind.WORKFLOW);
        assertThat(
                        McpServerMethods.paramHeaderMismatch(
                                plain, args("{\"a\":1}"), headers(), mapper))
                .isNull();
    }

    // ---- sync wait from workflow metadata --------------------------------------------------

    private Duration syncWait(Object value) {
        Map<String, Object> metadataValues = new HashMap<>();
        if (value != null) {
            metadataValues.put("mcp.syncWaitSeconds", value);
        }
        return McpServerMethods.syncWait(metadataValues, properties);
    }

    @Test
    void syncWait_defaultIs10s() {
        assertThat(syncWait(null)).isEqualTo(Duration.ofSeconds(10));
        assertThat(McpServerMethods.syncWait(null, properties)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void syncWait_perWorkflow_wholeNumbersOnly_cappedAtTheMaximum() {
        assertThat(syncWait(20)).isEqualTo(Duration.ofSeconds(20));
        assertThat(syncWait("20")).isEqualTo(Duration.ofSeconds(20));
        assertThat(syncWait(20.0)).isEqualTo(Duration.ofSeconds(20));
        assertThat(syncWait("20.0")).isEqualTo(Duration.ofSeconds(20));
        assertThat(syncWait(0)).isEqualTo(Duration.ZERO);
        assertThat(syncWait(60)).isEqualTo(Duration.ofSeconds(25));
    }

    @Test
    void syncWait_negativeOrNotAWholeNumber_isIgnored() {
        for (Object value : new Object[] {-1, 2.5, "2.5", "soon", true}) {
            assertThat(syncWait(value)).as(String.valueOf(value)).isEqualTo(Duration.ofSeconds(10));
        }
    }
}
