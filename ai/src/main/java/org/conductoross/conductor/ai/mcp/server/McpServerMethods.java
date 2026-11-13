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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.conductoross.conductor.ai.mcp.McpCallException;
import org.conductoross.conductor.ai.mcp.McpHeaderParams;
import org.conductoross.conductor.ai.mcp.McpMethod;
import org.conductoross.conductor.ai.mcp.McpProtocolHeaders;
import org.conductoross.conductor.ai.mcp.McpResultType;
import org.conductoross.conductor.ai.mcp.McpTaskStatus;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.HEADER_MISMATCH;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INTERNAL_ERROR;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.MISSING_REQUIRED_CLIENT_CAPABILITY;

/** The MCP method handlers of the Conductor MCP server. */
public class McpServerMethods {

    private static final Logger log = LoggerFactory.getLogger(McpServerMethods.class);

    private final McpToolRegistry registry;
    private final ObjectMapper mapper;
    private final McpServerProperties properties;
    private final McpResults results;
    private final McpExecutions executions;

    static final String CANCEL_REASON = "Cancelled by MCP client";

    /** Tool metadata key for the tool's own sync wait, in seconds. */
    static final String METADATA_SYNC_WAIT = "mcp.syncWaitSeconds";

    /** How often an execution is read while a call waits for it. */
    static final Duration READ_INTERVAL = Duration.ofMillis(250);

    public McpServerMethods(
            McpToolRegistry registry,
            ObjectMapper mapper,
            McpServerProperties properties,
            McpResults results,
            McpExecutions executions) {
        this.registry = registry;
        this.mapper = mapper;
        this.properties = properties;
        this.results = results;
        this.executions = executions;
    }

    /** Handlers by JSON-RPC method, for {@link McpServerProtocol}. */
    public Map<McpMethod, McpServerProtocol.MethodHandler> handlers() {
        Map<McpMethod, McpServerProtocol.MethodHandler> handlers = new EnumMap<>(McpMethod.class);
        handlers.put(McpMethod.TOOLS_LIST, this::toolsList);
        handlers.put(McpMethod.TOOLS_CALL, this::toolsCall);
        handlers.put(McpMethod.TASKS_GET, this::tasksGet);
        handlers.put(McpMethod.TASKS_CANCEL, this::tasksCancel);
        handlers.put(McpMethod.TASKS_UPDATE, this::tasksUpdate);
        return handlers;
    }

    // ---- tasks ----------------------------------------------------------------------------------

    /**
     * Reports the execution as a task. A failed execution is a tool error, so it is {@code
     * completed} with {@code result.isError}; {@code failed} is kept for an internal error.
     * Executions blocked on HUMAN or WAIT are {@code working}: v1 has no {@code input_required}.
     */
    Mono<JsonNode> tasksGet(McpServerProtocol.McpCall call) {
        return Mono.fromCallable(
                        () -> {
                            Workflow execution = exposedExecution(call);
                            return (JsonNode) taskView(execution);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Terminates a running or paused execution and acknowledges with an empty {@code complete}
     * result; an ended execution is left as it is and gets the same acknowledgement. An execution
     * that ends between the read and the terminate (the engine refuses with a conflict) is ended
     * too, so it gets the acknowledgement as well.
     */
    Mono<JsonNode> tasksCancel(McpServerProtocol.McpCall call) {
        return Mono.fromCallable(
                        () -> {
                            Workflow execution = exposedExecution(call);
                            if (!isEnded(execution)) {
                                try {
                                    executions.terminate(execution.getWorkflowId(), CANCEL_REASON);
                                } catch (ConflictException e) {
                                    log.debug(
                                            "MCP server: execution {} ended before the cancel: {}",
                                            execution.getWorkflowId(),
                                            e.getMessage());
                                }
                            }
                            ObjectNode ack = mapper.createObjectNode();
                            ack.put(McpResultType.FIELD, McpResultType.COMPLETE.value());
                            return (JsonNode) ack;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * {@code tasks/update} delivers input a task asked for. v1 never asks ({@code input_required}),
     * so no input is outstanding: as the Tasks extension asks of servers, responses for unknown
     * keys are ignored and the request gets the empty acknowledgement. The task must exist.
     */
    Mono<JsonNode> tasksUpdate(McpServerProtocol.McpCall call) {
        return Mono.fromCallable(
                        () -> {
                            exposedExecution(call);
                            ObjectNode ack = mapper.createObjectNode();
                            ack.put(McpResultType.FIELD, McpResultType.COMPLETE.value());
                            return (JsonNode) ack;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Rejects a Tasks method from a client that did not declare the Tasks extension. */
    private void requireTasks(McpServerProtocol.McpCall call) {
        if (!call.declaresTasks()) {
            ObjectNode data = mapper.createObjectNode();
            data.putObject("requiredCapabilities")
                    .putObject("extensions")
                    .putObject(McpProtocolHeaders.TASKS_EXTENSION);
            throw new McpServerException(
                    HTTP_BAD_REQUEST,
                    MISSING_REQUIRED_CLIENT_CAPABILITY,
                    call.method()
                            + " needs the "
                            + McpProtocolHeaders.TASKS_EXTENSION
                            + " extension",
                    data);
        }
    }

    /** The execution named by {@code params.taskId}, checked against the Tasks capability. */
    private Workflow exposedExecution(McpServerProtocol.McpCall call) {
        requireTasks(call);
        JsonNode taskIdNode = call.params().get("taskId");
        if (taskIdNode == null || !taskIdNode.isString() || taskIdNode.asString().isBlank()) {
            throw McpServerException.invalidParams(call.method() + " needs a string 'taskId'");
        }
        String taskId = taskIdNode.asString();
        Workflow execution;
        try {
            execution = executions.get(taskId);
        } catch (NotFoundException e) {
            execution = null;
        }
        // getWorkflowName() throws when the definition is missing, so check it first.
        if (execution == null
                || execution.getWorkflowDefinition() == null
                || execution.getWorkflowName() == null
                || !registry.exposes(execution.getWorkflowName())) {
            throw McpServerException.invalidParams("Unknown task: " + taskId);
        }
        return execution;
    }

    private ObjectNode taskView(Workflow execution) {
        Workflow.WorkflowStatus status = execution.getStatus();
        if (status == null || !status.isTerminal()) {
            return results.task(execution, McpTaskStatus.WORKING);
        }
        if (status == Workflow.WorkflowStatus.TERMINATED) {
            ObjectNode task = results.task(execution, McpTaskStatus.CANCELLED);
            String reason = McpResults.clientReason(execution.getReasonForIncompletion());
            if (reason != null) {
                task.put("statusMessage", reason);
            }
            return task;
        }
        try {
            ObjectNode task = results.task(execution, McpTaskStatus.COMPLETED);
            task.set("result", results.callResult(execution));
            return task;
        } catch (RuntimeException e) {
            log.warn(
                    "MCP server: result of execution {} could not be built",
                    execution.getWorkflowId(),
                    e);
            ObjectNode task = results.task(execution, McpTaskStatus.FAILED);
            task.put(
                    "statusMessage",
                    "The call result could not be read. Execution id: "
                            + execution.getWorkflowId());
            ObjectNode error = task.putObject("error");
            error.put("code", INTERNAL_ERROR);
            error.put("message", "Internal error");
            return task;
        }
    }

    /**
     * Starts the tool and waits up to its sync wait. An ended execution gives a {@code
     * CallToolResult}. A running one gives a {@code CreateTaskResult} to a Tasks client; other
     * clients keep waiting up to the blocking cap, then get an {@code isError} result while the
     * execution keeps running.
     */
    Mono<JsonNode> toolsCall(McpServerProtocol.McpCall call) {
        return Mono.fromCallable(() -> startCall(call))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        started -> {
                            if (started.immediate() != null) {
                                return Mono.just(started.immediate());
                            }
                            return waitFor(started.executionId(), started.syncWait())
                                    .flatMap(
                                            execution -> {
                                                if (isEnded(execution)) {
                                                    return Mono.just(
                                                            (JsonNode)
                                                                    results.callResult(execution));
                                                }
                                                if (call.declaresTasks()) {
                                                    return Mono.just(
                                                            (JsonNode)
                                                                    results.createTask(execution));
                                                }
                                                Duration rest =
                                                        Duration.ofSeconds(
                                                                        properties
                                                                                .getBlockingCapSeconds())
                                                                .minus(started.syncWait());
                                                if (!rest.isPositive()) {
                                                    // The first wait used the whole cap.
                                                    return Mono.just(
                                                            (JsonNode)
                                                                    results.stillRunning(
                                                                            execution));
                                                }
                                                return waitFor(started.executionId(), rest)
                                                        .map(
                                                                latest ->
                                                                        isEnded(latest)
                                                                                ? results
                                                                                        .callResult(
                                                                                                latest)
                                                                                : results
                                                                                        .stillRunning(
                                                                                                latest));
                                            });
                        });
    }

    /** A started call, or the immediate result when the call could not start. */
    private record Started(String executionId, Duration syncWait, JsonNode immediate) {}

    private Started startCall(McpServerProtocol.McpCall call) {
        JsonNode nameNode = call.params().get("name");
        if (nameNode == null || !nameNode.isString() || nameNode.asString().isBlank()) {
            throw McpServerException.invalidParams("tools/call needs a string 'name'");
        }
        String name = nameNode.asString();
        JsonNode argumentsNode = call.params().get("arguments");
        if (argumentsNode != null && !argumentsNode.isNull() && !argumentsNode.isObject()) {
            throw McpServerException.invalidParams("'arguments' must be an object");
        }
        ObjectNode arguments =
                argumentsNode == null || argumentsNode.isNull()
                        ? mapper.createObjectNode()
                        : (ObjectNode) argumentsNode;
        McpToolRegistry.Resolved resolved =
                registry.resolve(name).orElseThrow(() -> McpServerException.unknownTool(name));
        McpTool tool = resolved.tool();

        // Legacy clients send no Mcp-Param-* headers; the rule is 2026-07-28 only.
        String mismatch;
        try {
            mismatch =
                    call.legacy()
                            ? null
                            : paramHeaderMismatch(tool, arguments, call.headers(), mapper);
        } catch (McpCallException e) {
            // An annotated argument whose value cannot be a header value: a client error. (Tools
            // with an invalid annotation are not offered, so that case only guards a race.)
            throw McpServerException.invalidParams(e.getMessage());
        }
        if (mismatch != null) {
            throw new McpServerException(HTTP_BAD_REQUEST, HEADER_MISMATCH, mismatch, null);
        }

        Duration syncWait = syncWait(resolved.metadata(), properties);
        if (!call.declaresTasks()) {
            // The blocking cap bounds the whole wait of a client without Tasks.
            Duration cap = Duration.ofSeconds(properties.getBlockingCapSeconds());
            syncWait = syncWait.compareTo(cap) > 0 ? cap : syncWait;
        }
        String executionId;
        try {
            executionId = resolved.start(arguments);
        } catch (McpServerException e) {
            throw e;
        } catch (SchemaValidationException e) {
            // The arguments do not match the tool's registered input schema: the caller can fix
            // them, so it gets the reason (in tool terms), as the tools spec asks of input errors.
            log.info("MCP server: tool {} arguments rejected: {}", name, e.getMessage());
            return new Started(
                    null, syncWait, results.startFailed(McpResults.clientReason(e.getMessage())));
        } catch (RuntimeException e) {
            log.warn("MCP server: tool {} could not be started: {}", name, e.getMessage());
            return new Started(null, syncWait, results.startFailed());
        }
        return new Started(executionId, syncWait, null);
    }

    /**
     * Reads the execution until it ends or {@code timeout} passes, and emits the last state read.
     * The same approach as Conductor's synchronous execute ({@code WorkflowSignalResponder}): no
     * thread waits between reads, and any node can serve the wait, since it reads the store.
     */
    private Mono<Workflow> waitFor(String executionId, Duration timeout) {
        Mono<Workflow> read =
                Mono.fromCallable(() -> executions.get(executionId))
                        .subscribeOn(Schedulers.boundedElastic());
        Mono<Workflow> wait =
                !timeout.isPositive()
                        ? read
                        : Flux.interval(Duration.ZERO, READ_INTERVAL)
                                .onBackpressureDrop()
                                .concatMap(tick -> read, 1)
                                .filter(McpServerMethods::isEnded)
                                .next()
                                .timeout(timeout, read);
        // The execution is already started: a read error must not hide its id, or a client that
        // retries starts a second one. Treat it as still running; the client gets the id (a task,
        // or the still-running result) and can check again.
        return wait.onErrorResume(
                e -> {
                    log.warn(
                            "MCP server: could not read execution {} during the wait: {}",
                            executionId,
                            e.getMessage());
                    Workflow running = new Workflow();
                    running.setWorkflowId(executionId);
                    running.setStatus(Workflow.WorkflowStatus.RUNNING);
                    return Mono.just(running);
                });
    }

    static boolean isEnded(Workflow workflow) {
        return workflow.getStatus() != null && workflow.getStatus().isTerminal();
    }

    /**
     * A tool's sync wait: metadata {@code mcp.syncWaitSeconds}, a whole number from 0 to {@code
     * maxSyncWaitSeconds} (larger values are capped), given as a number or a numeric string such as
     * {@code "20"} or {@code "20.0"}; otherwise the configured default.
     */
    static Duration syncWait(Map<String, Object> metadata, McpServerProperties properties) {
        int max = properties.getMaxSyncWaitSeconds();
        int seconds = Math.min(properties.getSyncWaitSeconds(), max);
        Object value = metadata == null ? null : metadata.get(METADATA_SYNC_WAIT);
        Double number = null;
        if (value instanceof Number n) {
            number = n.doubleValue();
        } else if (value instanceof String s) {
            try {
                number = Double.valueOf(s.trim());
            } catch (NumberFormatException e) {
                number = null;
            }
        }
        if (number != null && number >= 0 && number == Math.rint(number)) {
            seconds = (int) Math.min(number, max);
        }
        return Duration.ofSeconds(Math.max(0, seconds));
    }

    /** All exposed tools in one result, with no {@code nextCursor}. */
    Mono<JsonNode> toolsList(McpServerProtocol.McpCall call) {
        return Mono.fromCallable(
                        () -> {
                            ObjectNode result = mapper.createObjectNode();
                            ArrayNode tools = result.putArray("tools");
                            for (McpTool tool : registry.listTools()) {
                                ObjectNode listed = tools.addObject();
                                listed.put("name", tool.name());
                                listed.put("description", tool.description());
                                listed.set("inputSchema", tool.inputSchema());
                            }
                            return (JsonNode) McpServerProtocol.withCachingHints(result);
                        })
                // Metadata reads, like the other handlers: not on the servlet thread.
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Whether a header value is the same integer as the expected one, written another way (for
     * example {@code 42.0} for {@code 42}): servers SHOULD compare integer values numerically. Only
     * an expected value that is an integer is compared this way.
     */
    private static boolean sameInteger(String expected, String actual) {
        if (actual == null || !expected.matches("-?\\d+")) {
            return false;
        }
        try {
            return new BigDecimal(actual.trim()).compareTo(new BigDecimal(expected)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Compares the request's {@code Mcp-Param-*} headers with the ones the client must send for
     * this tool and these arguments (the {@code x-mcp-header} rules the #1799 client applies).
     * Values are compared after Base64 sentinel decoding, header names ignoring case.
     *
     * @return a message for the first mismatch, or {@code null} when the headers match
     */
    static String paramHeaderMismatch(
            McpTool tool, ObjectNode arguments, Map<String, String> headers, ObjectMapper mapper) {
        @SuppressWarnings("unchecked")
        Map<String, Object> argumentMap =
                arguments == null ? Map.of() : mapper.convertValue(arguments, Map.class);
        Map<String, String> expected = new HashMap<>();
        McpHeaderParams.headersFor(tool.name(), tool.inputSchema(), argumentMap)
                .forEach(
                        (name, value) ->
                                expected.put(
                                        name.toLowerCase(Locale.ROOT),
                                        McpProtocolHeaders.decodeHeaderValue(value)));

        String prefix = McpHeaderParams.HEADER_PREFIX.toLowerCase(Locale.ROOT);
        Map<String, String> actual = new HashMap<>();
        headers.forEach(
                (name, value) -> {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        actual.put(
                                name.toLowerCase(Locale.ROOT),
                                McpProtocolHeaders.decodeHeaderValue(value));
                    }
                });

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String value = actual.get(entry.getKey());
            if (!entry.getValue().equals(value) && !sameInteger(entry.getValue(), value)) {
                return headerName(entry.getKey())
                        + " header must equal the argument value for tool '"
                        + tool.name()
                        + "'";
            }
        }
        for (String name : actual.keySet()) {
            if (!expected.containsKey(name)) {
                return headerName(name)
                        + " header has no matching argument for tool '"
                        + tool.name()
                        + "'";
            }
        }
        return null;
    }

    /** {@code mcp-param-region} to {@code Mcp-Param-Region}, for messages. */
    private static String headerName(String lowerCase) {
        String suffix = lowerCase.substring(McpHeaderParams.HEADER_PREFIX.length());
        return McpHeaderParams.HEADER_PREFIX
                + (suffix.isEmpty()
                        ? ""
                        : Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1));
    }
}
