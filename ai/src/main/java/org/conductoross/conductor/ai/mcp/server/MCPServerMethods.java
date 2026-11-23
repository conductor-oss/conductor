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
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.conductoross.conductor.ai.mcp.MCPCallException;
import org.conductoross.conductor.ai.mcp.MCPFields;
import org.conductoross.conductor.ai.mcp.MCPHeaderParams;
import org.conductoross.conductor.ai.mcp.MCPMethod;
import org.conductoross.conductor.ai.mcp.MCPProtocolHeaders;
import org.conductoross.conductor.ai.mcp.server.model.MCPAck;
import org.conductoross.conductor.ai.mcp.server.model.MCPCallToolResult;
import org.conductoross.conductor.ai.mcp.server.model.MCPCapabilities;
import org.conductoross.conductor.ai.mcp.server.model.MCPListedTool;
import org.conductoross.conductor.ai.mcp.server.model.MCPMissingCapability;
import org.conductoross.conductor.ai.mcp.server.model.MCPTask;
import org.conductoross.conductor.ai.mcp.server.model.MCPToolsList;
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
import tools.jackson.databind.node.ObjectNode;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.HEADER_MISMATCH;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.INTERNAL_ERROR;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.MISSING_REQUIRED_CLIENT_CAPABILITY;

/**
 * The MCP method handlers of the Conductor MCP server.
 *
 * <p>How a failure reaches the client (the same exception can mean different things per method):
 *
 * <ul>
 *   <li>{@code tasks/*}: a task id the store does not know or cannot parse ({@code
 *       NotFoundException}, {@code IllegalArgumentException}) is {@code -32602} "Unknown task".
 *   <li>{@code tasks/cancel}: an execution that ends before the terminate ({@code
 *       ConflictException}) gets the empty acknowledgement.
 *   <li>{@code tasks/get}: a result that cannot be built is a {@code failed} task with {@code
 *       -32603}.
 *   <li>{@code tools/call}: an argument that cannot be sent as its {@code Mcp-Param-*} header
 *       ({@code MCPCallException}) is {@code -32602}.
 *   <li>{@code tools/call}: arguments the caller can fix ({@code MCPToolArgumentsException}, {@code
 *       SchemaValidationException}) give an {@code isError} result with the reason; any other start
 *       failure gives one without a reason.
 *   <li>{@code tools/call}: a read error while waiting counts as still running, so the client keeps
 *       the execution id.
 *   <li>Any method: {@link MCPServerException} keeps its own code and HTTP status; any other
 *       exception is {@code -32603} with HTTP 500 ({@link MCPServerProtocol}).
 * </ul>
 */
public class MCPServerMethods {

    private static final Logger log = LoggerFactory.getLogger(MCPServerMethods.class);

    static final String CANCEL_REASON = "Cancelled by MCP client";

    /** Tool metadata key for the tool's own sync wait, in seconds. */
    static final String METADATA_SYNC_WAIT = "mcp.syncWaitSeconds";

    /** How often an execution is read while a call waits for it. */
    static final Duration READ_INTERVAL = Duration.ofMillis(250);

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private final MCPToolRegistry registry;
    private final ObjectMapper mapper;
    private final MCPServerProperties properties;
    private final MCPResults results;
    private final MCPExecutions executions;

    public MCPServerMethods(
            MCPToolRegistry registry,
            ObjectMapper mapper,
            MCPServerProperties properties,
            MCPResults results,
            MCPExecutions executions) {
        this.registry = registry;
        this.mapper = mapper;
        this.properties = properties;
        this.results = results;
        this.executions = executions;
    }

    /** Handlers by JSON-RPC method, for {@link MCPServerProtocol}, with metrics. */
    public Map<MCPMethod, MCPServerProtocol.MethodHandler> handlers() {
        Map<MCPMethod, MCPServerProtocol.MethodHandler> handlers = new EnumMap<>(MCPMethod.class);
        handlers.put(MCPMethod.TOOLS_LIST, this::toolsList);
        handlers.put(MCPMethod.TOOLS_CALL, this::toolsCall);
        handlers.put(MCPMethod.TASKS_GET, this::tasksGet);
        handlers.put(MCPMethod.TASKS_CANCEL, this::tasksCancel);
        handlers.put(MCPMethod.TASKS_UPDATE, this::tasksUpdate);
        handlers.replaceAll(MCPServerMetrics::timed);
        return handlers;
    }

    /**
     * Reports the execution as a task. A failed execution is a tool error, so it is {@code
     * completed} with {@code result.isError}; {@code failed} is kept for an internal error.
     * Executions blocked on HUMAN or WAIT are {@code working}: v1 has no {@code input_required}.
     */
    Mono<JsonNode> tasksGet(MCPServerProtocol.MCPCall call) {
        return offRequestThread(() -> json(taskView(exposedExecution(call))));
    }

    /**
     * Terminates a running or paused execution and acknowledges with an empty {@code complete}
     * result; an ended execution is left as it is and gets the same acknowledgement. An execution
     * that ends between the read and the terminate also gets the acknowledgement. The engine
     * refuses to terminate a COMPLETED execution (a conflict, caught here), but turns one that has
     * just FAILED or TIMED_OUT into TERMINATED: in that narrow window the cancel wins and the
     * failure reason is replaced.
     */
    Mono<JsonNode> tasksCancel(MCPServerProtocol.MCPCall call) {
        return offRequestThread(
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
                    return json(MCPAck.COMPLETE);
                });
    }

    /**
     * {@code tasks/update} delivers input a task asked for. v1 never asks ({@code input_required}),
     * so no input is outstanding: as the Tasks extension asks of servers, responses for unknown
     * keys are ignored and the request gets the empty acknowledgement. The task must exist.
     */
    Mono<JsonNode> tasksUpdate(MCPServerProtocol.MCPCall call) {
        return offRequestThread(
                () -> {
                    exposedExecution(call);
                    return json(MCPAck.COMPLETE);
                });
    }

    /** Rejects a Tasks method from a client that did not declare the Tasks extension. */
    private void requireTasks(MCPServerProtocol.MCPCall call) {
        if (!call.declaresTasks()) {
            JsonNode data =
                    json(
                            new MCPMissingCapability(
                                    MCPCapabilities.extension(MCPProtocolHeaders.TASKS_EXTENSION)));
            throw new MCPServerException(
                    HTTP_BAD_REQUEST,
                    MISSING_REQUIRED_CLIENT_CAPABILITY,
                    call.method()
                            + " needs the "
                            + MCPProtocolHeaders.TASKS_EXTENSION
                            + " extension",
                    data);
        }
    }

    /** The execution named by {@code params.taskId}, checked against the Tasks capability. */
    private Workflow exposedExecution(MCPServerProtocol.MCPCall call) {
        requireTasks(call);
        JsonNode taskIdNode = call.params().get(MCPFields.Task.TASK_ID);
        if (taskIdNode == null || !taskIdNode.isString() || taskIdNode.asString().isBlank()) {
            throw MCPServerException.invalidParams(call.method() + " needs a string 'taskId'");
        }
        String taskId = taskIdNode.asString();
        Workflow execution;
        try {
            execution = executions.get(taskId);
        } catch (NotFoundException | IllegalArgumentException e) {
            // A store that parses ids (Cassandra needs a UUID) rejects a malformed one with
            // IllegalArgumentException; it is an unknown task, not a server error to retry.
            execution = null;
        }
        // getWorkflowName() throws when the definition is missing, so check it first.
        if (execution == null
                || execution.getWorkflowDefinition() == null
                || execution.getWorkflowName() == null
                || !registry.exposes(execution.getWorkflowName())) {
            throw MCPServerException.invalidParams("Unknown task: " + taskId);
        }
        return execution;
    }

    private MCPTask taskView(Workflow execution) {
        Workflow.WorkflowStatus status = execution.getStatus();
        if (status == null || !status.isTerminal()) {
            return results.working(execution);
        }
        if (status == Workflow.WorkflowStatus.TERMINATED) {
            return results.cancelled(execution);
        }
        try {
            return results.completed(execution, results.callResult(execution));
        } catch (RuntimeException e) {
            log.warn(
                    "MCP server: result of execution {} could not be built",
                    execution.getWorkflowId(),
                    e);
            return results.failed(execution, INTERNAL_ERROR);
        }
    }

    /** Runs blocking work (store reads, starting an execution) off the servlet request thread. */
    private static <T> Mono<T> offRequestThread(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    /** A result record as the JSON the protocol layer sends. */
    private JsonNode json(Object result) {
        return mapper.valueToTree(result);
    }

    /**
     * Starts the tool and waits up to its sync wait. An ended execution gives a {@code
     * CallToolResult}. A running one gives a {@code CreateTaskResult} to a Tasks client; other
     * clients keep waiting up to the blocking cap, then get an {@code isError} result while the
     * execution keeps running.
     */
    Mono<JsonNode> toolsCall(MCPServerProtocol.MCPCall call) {
        return offRequestThread(() -> startCall(call))
                .flatMap(
                        started ->
                                started.immediate() != null
                                        ? Mono.<Object>just(started.immediate())
                                        : firstWait(call, started))
                .map(this::json);
    }

    /** Waits up to the call's sync wait. */
    private Mono<Object> firstWait(MCPServerProtocol.MCPCall call, Started started) {
        return waitFor(started.executionId(), started.syncWait())
                .flatMap(execution -> afterFirstWait(call, started, execution));
    }

    /**
     * An ended execution gives its result; a running one gives a task to a Tasks client, and a
     * second wait, up to the blocking cap, to any other client.
     */
    private Mono<Object> afterFirstWait(
            MCPServerProtocol.MCPCall call, Started started, Workflow execution) {
        if (isEnded(execution)) {
            return Mono.just(results.callResult(execution));
        }
        if (call.declaresTasks()) {
            return Mono.just(results.createTask(execution));
        }
        Duration rest =
                Duration.ofSeconds(properties.getBlockingCapSeconds()).minus(started.syncWait());
        // The first wait can use the whole cap.
        return rest.isPositive()
                ? secondWait(started.executionId(), rest)
                : Mono.just(results.stillRunning(execution));
    }

    /** A client without Tasks keeps waiting, up to the blocking cap. */
    private Mono<Object> secondWait(String executionId, Duration rest) {
        return waitFor(executionId, rest)
                .map(
                        latest ->
                                isEnded(latest)
                                        ? results.callResult(latest)
                                        : results.stillRunning(latest));
    }

    /** A started call, or the immediate result when the call could not start. */
    private record Started(String executionId, Duration syncWait, MCPCallToolResult immediate) {}

    private Started startCall(MCPServerProtocol.MCPCall call) {
        JsonNode nameNode = call.params().get(MCPFields.Tool.NAME);
        if (nameNode == null || !nameNode.isString() || nameNode.asString().isBlank()) {
            throw MCPServerException.invalidParams(call.method() + " needs a string 'name'");
        }
        String name = nameNode.asString();
        JsonNode argumentsNode = call.params().get(MCPFields.Tool.ARGUMENTS);
        if (argumentsNode != null && !argumentsNode.isNull() && !argumentsNode.isObject()) {
            throw MCPServerException.invalidParams("'arguments' must be an object");
        }
        ObjectNode arguments =
                argumentsNode == null || argumentsNode.isNull()
                        ? mapper.createObjectNode()
                        : (ObjectNode) argumentsNode;
        MCPToolRegistry.Resolved resolved =
                registry.resolve(name).orElseThrow(() -> MCPServerException.unknownTool(name));
        MCPTool tool = resolved.tool();

        // Legacy clients send no Mcp-Param-* headers; the rule is 2026-07-28 only.
        String mismatch;
        try {
            mismatch =
                    call.version().isLegacy()
                            ? null
                            : paramHeaderMismatch(tool, arguments, call.headers(), mapper);
        } catch (MCPCallException e) {
            // An argument that cannot be sent as its header: a client error.
            throw MCPServerException.invalidParams(e.getMessage());
        }
        if (mismatch != null) {
            throw new MCPServerException(HTTP_BAD_REQUEST, HEADER_MISMATCH, mismatch, null);
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
        } catch (MCPServerException e) {
            throw e;
        } catch (MCPToolArgumentsException | SchemaValidationException e) {
            // The caller can fix these, so it gets the reason, as the tools spec asks of input
            // errors.
            log.info("MCP server: tool {} arguments rejected: {}", name, e.getMessage());
            return new Started(null, syncWait, results.startFailed(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("MCP server: tool {} could not be started", name, e);
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
        Mono<Workflow> read = offRequestThread(() -> executions.get(executionId));
        Mono<Workflow> wait =
                !timeout.isPositive()
                        ? read
                        : Flux.interval(Duration.ZERO, READ_INTERVAL)
                                .onBackpressureDrop()
                                .concatMap(tick -> read, 1)
                                .filter(MCPServerMethods::isEnded)
                                .next()
                                .timeout(timeout, read);
        // A read error must not hide the started execution's id: a retry would start a second one.
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
    static Duration syncWait(Map<String, Object> metadata, MCPServerProperties properties) {
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
    Mono<JsonNode> toolsList(MCPServerProtocol.MCPCall call) {
        return offRequestThread(
                () -> {
                    MCPToolsList list =
                            new MCPToolsList(
                                    registry.tools()
                                            .map(
                                                    tool ->
                                                            new MCPListedTool(
                                                                    tool.name(),
                                                                    tool.description(),
                                                                    tool.inputSchema(),
                                                                    tool.outputSchema()))
                                            .toList());
                    return (JsonNode) MCPServerProtocol.withCachingHints(mapper.valueToTree(list));
                });
    }

    /**
     * Whether a header value is the same integer as the expected one, written another way (for
     * example {@code 42.0} for {@code 42}): servers SHOULD compare integer values numerically. Only
     * an expected value that is an integer is compared this way.
     */
    private static boolean sameInteger(String expected, String actual) {
        if (actual == null || !INTEGER.matcher(expected).matches()) {
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
            MCPTool tool, ObjectNode arguments, Map<String, String> headers, ObjectMapper mapper) {
        Map<String, String> expected = new HashMap<>();
        // The arguments become a map only for a tool with x-mcp-header annotations.
        MCPHeaderParams.headersFor(
                        tool.name(), tool.inputSchema(), () -> argumentMap(arguments, mapper))
                .forEach(
                        (name, value) ->
                                expected.put(
                                        name.toLowerCase(Locale.ROOT),
                                        MCPProtocolHeaders.decodeHeaderValue(value)));

        String prefix = MCPHeaderParams.HEADER_PREFIX.toLowerCase(Locale.ROOT);
        Map<String, String> actual = new HashMap<>();
        headers.forEach(
                (name, value) -> {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        actual.put(
                                name.toLowerCase(Locale.ROOT),
                                MCPProtocolHeaders.decodeHeaderValue(value));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argumentMap(ObjectNode arguments, ObjectMapper mapper) {
        return arguments == null ? Map.of() : mapper.convertValue(arguments, Map.class);
    }

    /** {@code mcp-param-region} to {@code Mcp-Param-Region}, for messages. */
    private static String headerName(String lowerCase) {
        String suffix = lowerCase.substring(MCPHeaderParams.HEADER_PREFIX.length());
        return MCPHeaderParams.HEADER_PREFIX
                + (suffix.isEmpty()
                        ? ""
                        : Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1));
    }
}
