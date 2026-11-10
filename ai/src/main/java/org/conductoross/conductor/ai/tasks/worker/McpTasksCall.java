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

import java.util.Map;

import org.conductoross.conductor.ai.mcp.MCPService;
import org.conductoross.conductor.ai.mcp.McpCallException;
import org.conductoross.conductor.ai.mcp.McpHeaderParams;
import org.conductoross.conductor.ai.mcp.McpOptions;
import org.conductoross.conductor.ai.mcp.McpProtocolHeaders;
import org.conductoross.conductor.ai.mcp.McpRequestException;
import org.conductoross.conductor.ai.model.MCPToolCallRequest;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lifecycle of a {@code CALL_MCP_TOOL} call that selects {@code mcpOptions.protocolVersion} (the
 * MCP {@code 2026-07-28} form, which declares the MCP Tasks extension). MCP-specific; structured
 * like {@code A2AWorkers}.
 *
 * <p>The server decides whether a call runs as a task. An immediate result completes the Conductor
 * task in the same run. A created task is polled with {@code tasks/get} on later runs, scheduled by
 * the existing async system-task lifecycle ({@code IN_PROGRESS} plus {@code callbackAfterSeconds}).
 * The only state kept between runs is the {@code mcpTask} output object ({@code taskId}, {@code
 * pollFailures}, {@code retryCount}, and the server's last {@code pollIntervalMs}).
 *
 * <p>Every outcome is applied through the {@link TaskContext} result, as {@code A2AWorkers.finish}
 * does: the callback is {@code 0} for every status other than {@code IN_PROGRESS}, and at least
 * {@code 1} for {@code IN_PROGRESS}.
 */
@Slf4j
final class McpTasksCall {

    static final String STATE_KEY = "mcpTask";
    static final long DEFAULT_POLL_SECONDS = 5;
    static final long MAX_DURATION_SECONDS = 24L * 60 * 60;
    static final int MAX_POLL_FAILURES = 30;

    /**
     * Upper bound of the wait after failed checks. The wait starts at the poll interval and doubles
     * after each consecutive failure, but never drops below the server's interval.
     */
    static final long MAX_BACKOFF_SECONDS = 300;

    private static final String WORKING = "working";
    private static final String COMPLETED = "completed";
    private static final String FAILED = "failed";
    private static final String CANCELLED = "cancelled";
    private static final String INPUT_REQUIRED = "input_required";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();

    private final MCPService mcpService;

    McpTasksCall(MCPService mcpService) {
        this.mcpService = mcpService;
    }

    MCPWorkers.ToolCallResult run(MCPToolCallRequest request) {
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            throw new McpCallException(
                    "CALL_MCP_TOOL requires the input 'method' (the name of the tool to call)");
        }
        Task task = TaskContext.get().getTask();
        // Reading the state from output data relies on in-progress output never being
        // externalized: while a call polls, its output holds only the small mcpTask object, as
        // A2A's polling state is small (design D5). Externalized output would read as empty here.
        Object state = task.getOutputData() == null ? null : task.getOutputData().get(STATE_KEY);
        // Resume only on a later run of this execution (IN_PROGRESS). A run that starts from
        // SCHEDULED is a first run, even if the output still holds an mcpTask: a task that the
        // engine resets in place (for example a CANCELED sibling re-scheduled when a sub-workflow
        // is retried) keeps its output, and must start a new tools/call, not resume the old task.
        if (state == null || task.getStatus() != Task.Status.IN_PROGRESS) {
            return start(request, task);
        }
        return poll(request, task, state);
    }

    /** First run: sends {@code tools/call}; completes now, or stores the created task's ID. */
    private MCPWorkers.ToolCallResult start(MCPToolCallRequest request, Task task) {
        MCPService.ToolCallOutcome outcome = callTool(request);
        if (!outcome.isTask()) {
            return finish(TaskResult.Status.COMPLETED, 0, null, toolResult(outcome.result()));
        }
        MCPService.McpTask created = outcome.task();
        log.debug(
                "MCP server {} created task {} for tool '{}'",
                request.getMcpServer(),
                created.taskId(),
                request.getMethod());
        return keepPolling(
                task, created.taskId(), 0, created.pollIntervalMs(), created.pollIntervalMs());
    }

    /** Later runs: one {@code tasks/get} for the stored task ID, mapped to a Conductor outcome. */
    private MCPWorkers.ToolCallResult poll(MCPToolCallRequest request, Task task, Object state) {
        String taskId = stateTaskId(state);
        int failures = statePollFailures(state);
        Long interval = statePollIntervalMs(state);
        if (taskId == null) {
            return fail(
                    null,
                    0,
                    "Invalid MCP polling state in task output '" + STATE_KEY + "': " + state);
        }
        MCPService.McpTask remote;
        try {
            remote = mcpService.getTask(request.getMcpServer(), taskId, request.getHeaders());
        } catch (McpRequestException e) {
            Integer code = e.getCode();
            if (e.hasDefinedMeaning()) {
                // A JSON-RPC error with a defined meaning is the MCP server's own answer, so its
                // code decides, whatever the HTTP status.
                if (code == McpRequestException.INVALID_PARAMS) {
                    return fail(
                            taskId,
                            failures,
                            "MCP task '"
                                    + taskId
                                    + "' expired or is unknown to the server: "
                                    + e.getMessage());
                }
                if (e.isInternalError()) {
                    return transientFailure(request, task, taskId, failures, interval, e);
                }
                // The server rejected the request; the remote task may still be running.
                cancelBestEffort(request, taskId, "tasks/get rejected");
                return fail(taskId, failures, e.getMessage());
            }
            // No JSON-RPC error (a gateway or proxy may have answered), or a code with no defined
            // meaning: the HTTP status decides.
            int status = e.getHttpStatus();
            if (status == 401 || status == 403) {
                return fail(
                        taskId,
                        failures,
                        "MCP server "
                                + request.getMcpServer()
                                + " rejected tasks/get for task '"
                                + taskId
                                + "' with HTTP "
                                + status
                                + ": "
                                + e.getMessage());
            }
            if ((status >= 500 && status < 600) || status == 408 || status == 429) {
                return transientFailure(request, task, taskId, failures, interval, e);
            }
            // Any other HTTP error: the remote task may still be running.
            cancelBestEffort(request, taskId, "tasks/get rejected");
            return fail(taskId, failures, e.getMessage());
        } catch (MCPService.TransportException e) {
            return transientFailure(request, task, taskId, failures, interval, e);
        } catch (RuntimeException e) {
            // An unusable response (invalid task fields, or over the depth or size limits) fails
            // the task. The remote task may still be running, so cancel it first. The spec does
            // not define tasks/cancel on a finished task; the server may acknowledge or reject
            // it, and either is harmless.
            String reason = "MCP tasks/get for task '" + taskId + "' failed: " + e.getMessage();
            cancelBestEffort(request, taskId, "unusable tasks/get response");
            return fail(taskId, failures, reason);
        }

        switch (remote.status()) {
            case WORKING:
                if (deadlineExceeded(task)) {
                    return deadlineFailure(request, taskId, 0);
                }
                // pollIntervalMs is optional on each response: keep the last one the server gave.
                // A value of 0 or less is no interval, as an absent one is.
                Long given = remote.pollIntervalMs();
                Long next = given != null && given > 0 ? given : interval;
                return keepPolling(task, taskId, 0, next, next);
            case COMPLETED:
                if (remote.result() == null) {
                    return fail(
                            taskId, 0, "MCP task '" + taskId + "' completed without a 'result'");
                }
                MCPWorkers.ToolCallResult result;
                try {
                    result = toolResult(mcpService.mapTaskResult(remote.result()));
                } catch (RuntimeException e) {
                    return fail(
                            taskId,
                            0,
                            "MCP task '"
                                    + taskId
                                    + "' completed with a result that cannot be mapped: "
                                    + e);
                }
                result.mcpTask = state(taskId, 0);
                return finish(TaskResult.Status.COMPLETED, 0, null, result);
            case FAILED:
                return fail(taskId, 0, "MCP task '" + taskId + "' failed: " + describe(remote));
            case CANCELLED:
                return fail(
                        taskId,
                        0,
                        "MCP server cancelled task '"
                                + taskId
                                + "'"
                                + (remote.statusMessage() != null
                                        ? ": " + remote.statusMessage()
                                        : ""));
            case INPUT_REQUIRED:
                cancelBestEffort(request, taskId, "input requests are not supported");
                return fail(
                        taskId,
                        0,
                        "MCP task '"
                                + taskId
                                + "' requires input, but task input requests are not supported;"
                                + " Conductor sent tasks/cancel");
            default:
                // Not one of the statuses the Tasks extension defines: the remote task may still be
                // running.
                cancelBestEffort(request, taskId, "unsupported task status");
                return fail(
                        taskId,
                        0,
                        "MCP task '"
                                + taskId
                                + "' has unsupported status '"
                                + remote.status()
                                + "'; Conductor sent tasks/cancel");
        }
    }

    /**
     * The maximum duration has passed and the remote task is not finished: best-effort {@code
     * tasks/cancel}, then {@code FAILED_WITH_TERMINAL_ERROR}. Terminal, so that a retry does not
     * start the tool again for another 24 hours. Checked after {@code tasks/get}, so a task that
     * finished in the last interval still completes.
     */
    private MCPWorkers.ToolCallResult deadlineFailure(
            MCPToolCallRequest request, String taskId, int failures) {
        cancelBestEffort(request, taskId, "maximum duration exceeded");
        return failTerminal(
                taskId,
                failures,
                "MCP task '"
                        + taskId
                        + "' did not finish within the maximum duration of "
                        + MAX_DURATION_SECONDS
                        + " seconds (24 h); Conductor sent tasks/cancel");
    }

    /**
     * A network error, a timeout, JSON-RPC {@code -32603}, or HTTP {@code 408}, {@code 429} or
     * {@code 5xx} without a JSON-RPC code of defined meaning, on {@code tasks/get}: retried up to
     * the limit, with a wait that doubles after each consecutive failure (see {@link #backoffMs}).
     */
    private MCPWorkers.ToolCallResult transientFailure(
            MCPToolCallRequest request,
            Task task,
            String taskId,
            int failures,
            Long pollIntervalMs,
            RuntimeException error) {
        if (deadlineExceeded(task)) {
            return deadlineFailure(request, taskId, failures + 1);
        }
        int count = failures + 1;
        if (count >= MAX_POLL_FAILURES) {
            cancelBestEffort(request, taskId, "consecutive polling failures");
            return fail(
                    taskId,
                    count,
                    "MCP task '"
                            + taskId
                            + "' could not be checked: "
                            + count
                            + " consecutive tasks/get failures reached the limit of "
                            + MAX_POLL_FAILURES
                            + "; Conductor sent tasks/cancel. Last error: "
                            + error.getMessage());
        }
        log.warn(
                "Transient error polling MCP task {} on {} ({}/{}): {}",
                taskId,
                request.getMcpServer(),
                count,
                MAX_POLL_FAILURES,
                error.getMessage());
        return keepPolling(task, taskId, count, pollIntervalMs, backoffMs(pollIntervalMs, count));
    }

    /**
     * The wait after {@code failures} consecutive failed checks: the server's interval (or the
     * default), doubled for each failure after the first, up to {@link #MAX_BACKOFF_SECONDS} but
     * never below the server's interval.
     */
    static long backoffMs(Long pollIntervalMs, int failures) {
        long base =
                pollIntervalMs != null && pollIntervalMs > 0
                        ? Math.min(pollIntervalMs, MAX_DURATION_SECONDS * 1000)
                        : DEFAULT_POLL_SECONDS * 1000;
        long cap = Math.max(base, MAX_BACKOFF_SECONDS * 1000);
        int doublings = Math.min(Math.max(0, failures - 1), 20);
        return Math.min(cap, base << doublings);
    }

    /**
     * Stays {@code IN_PROGRESS}: stores the state (with the server's interval) and waits {@code
     * waitMs} before the next check.
     */
    private MCPWorkers.ToolCallResult keepPolling(
            Task task, String taskId, int failures, Long pollIntervalMs, Long waitMs) {
        MCPWorkers.ToolCallResult result = new MCPWorkers.ToolCallResult();
        result.mcpTask = state(taskId, failures, pollIntervalMs);
        return finish(TaskResult.Status.IN_PROGRESS, callbackSeconds(task, waitMs), null, result);
    }

    private MCPWorkers.ToolCallResult fail(String taskId, int failures, String reason) {
        return fail(TaskResult.Status.FAILED, taskId, failures, reason);
    }

    /** Fails without a retry. */
    private MCPWorkers.ToolCallResult failTerminal(String taskId, int failures, String reason) {
        return fail(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, taskId, failures, reason);
    }

    private MCPWorkers.ToolCallResult fail(
            TaskResult.Status status, String taskId, int failures, String reason) {
        MCPWorkers.ToolCallResult result = new MCPWorkers.ToolCallResult();
        if (taskId != null) {
            result.mcpTask = state(taskId, failures);
        }
        return finish(status, 0, reason, result);
    }

    /**
     * Applies the outcome to the {@link TaskContext} result. The callback is {@code 0} for every
     * status other than {@code IN_PROGRESS}, because a positive callback forces {@code
     * IN_PROGRESS}.
     */
    private static MCPWorkers.ToolCallResult finish(
            TaskResult.Status status,
            long callbackAfterSeconds,
            String reason,
            MCPWorkers.ToolCallResult output) {
        TaskResult context = TaskContext.get().getTaskResult();
        context.setStatus(status);
        context.setCallbackAfterSeconds(
                status == TaskResult.Status.IN_PROGRESS ? Math.max(1, callbackAfterSeconds) : 0);
        context.setReasonForIncompletion(reason);
        return output;
    }

    /**
     * Seconds until the next check: the server's {@code pollIntervalMs} rounded up, or the default;
     * at least 1, and never past the deadline, so the deadline check runs.
     */
    static long callbackSeconds(Task task, Long pollIntervalMs) {
        long seconds =
                pollIntervalMs != null && pollIntervalMs > 0
                        ? (Math.min(pollIntervalMs, MAX_DURATION_SECONDS * 1000) + 999) / 1000
                        : DEFAULT_POLL_SECONDS;
        long startTime = task.getStartTime();
        if (startTime > 0) {
            long remaining = MAX_DURATION_SECONDS - (System.currentTimeMillis() - startTime) / 1000;
            seconds = Math.min(seconds, Math.max(1, remaining + 1));
        }
        return Math.max(1, seconds);
    }

    private static boolean deadlineExceeded(Task task) {
        long startTime = task.getStartTime();
        return startTime > 0
                && System.currentTimeMillis() - startTime > MAX_DURATION_SECONDS * 1000L;
    }

    /** The polling state for this execution: the remote task ID, failures and retry count. */
    private static MCPWorkers.McpTaskState state(String taskId, int failures) {
        return state(taskId, failures, null);
    }

    private static MCPWorkers.McpTaskState state(String taskId, int failures, Long pollIntervalMs) {
        return new MCPWorkers.McpTaskState(
                taskId,
                failures,
                TaskContext.get().getTask().getRetryCount(),
                pollIntervalMs != null && pollIntervalMs > 0 ? pollIntervalMs : null);
    }

    /**
     * Called when Conductor cancels a {@code CALL_MCP_TOOL} task, for example on workflow
     * termination. Sends a best-effort {@code tasks/cancel} when the call selected a protocol
     * version and a remote task exists; otherwise does nothing. Never throws.
     *
     * <p>The remote task is cancelled only if this execution created it: when {@code mcpTask} has a
     * {@code retryCount} that differs from the task's, the state was left by an earlier execution
     * (the engine reset the task in place) and no request is sent. State without {@code retryCount}
     * is still cancelled.
     *
     * <p>Limitation: the task input seen here is the literal input, so headers written as {@code
     * ${workflow.secrets.*}} are not resolved, and the server may reject the cancel.
     */
    void cancel(Task task, String reason) {
        try {
            MCPToolCallRequest request =
                    OBJECT_MAPPER.convertValue(task.getInputData(), MCPToolCallRequest.class);
            if (McpOptions.protocolVersion(request.getMcpOptions()) == null) {
                return;
            }
            Object state =
                    task.getOutputData() == null ? null : task.getOutputData().get(STATE_KEY);
            String taskId = stateTaskId(state);
            if (taskId == null || !createdByThisExecution(state, task)) {
                return;
            }
            cancelBestEffort(request, taskId, reason);
        } catch (RuntimeException e) {
            log.warn(
                    "Could not propagate cancellation of CALL_MCP_TOOL task {} ({}): {}",
                    task.getTaskId(),
                    reason,
                    e.getMessage());
        }
    }

    private static boolean createdByThisExecution(Object state, Task task) {
        if (state instanceof Map<?, ?> map && map.get("retryCount") instanceof Number recorded) {
            return recorded.intValue() == task.getRetryCount();
        }
        return true;
    }

    /** Sends {@code tasks/cancel}; failures are logged, never raised. */
    private void cancelBestEffort(MCPToolCallRequest request, String taskId, String why) {
        try {
            mcpService.cancelTask(request.getMcpServer(), taskId, request.getHeaders());
        } catch (RuntimeException e) {
            log.warn(
                    "Best-effort tasks/cancel for MCP task {} on {} ({}) failed: {}",
                    taskId,
                    request.getMcpServer(),
                    why,
                    e.getMessage());
        }
    }

    private static MCPWorkers.ToolCallResult toolResult(Map<String, Object> callToolResult) {
        return MCPWorkers.ToolCallResult.from(callToolResult);
    }

    private static String describe(MCPService.McpTask remote) {
        JsonNode error = remote.error();
        if (error == null) {
            return remote.statusMessage() != null ? remote.statusMessage() : "no error given";
        }
        JsonNode code = error.get("code");
        JsonNode message = error.get("message");
        return "JSON-RPC error "
                + (code != null ? code : "(no code)")
                + " "
                + (message != null && message.isString()
                        ? message.asString()
                        : String.valueOf(message));
    }

    private static String stateTaskId(Object state) {
        if (state instanceof Map<?, ?> map
                && map.get("taskId") instanceof String id
                && !id.isBlank()) {
            return id;
        }
        return null;
    }

    private static Long statePollIntervalMs(Object state) {
        if (state instanceof Map<?, ?> map
                && map.get("pollIntervalMs") instanceof Number n
                && n.longValue() > 0) {
            return n.longValue();
        }
        return null;
    }

    private static int statePollFailures(Object state) {
        if (state instanceof Map<?, ?> map && map.get("pollFailures") instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        return 0;
    }

    /**
     * Looks up the tool's schema, then sends the {@code tools/call} in the {@code 2026-07-28} form.
     * On {@code HeaderMismatch} ({@code -32020}) the schema is fetched again and the call is
     * retried once.
     */
    private MCPService.ToolCallOutcome callTool(MCPToolCallRequest request) {
        try {
            return attempt(request);
        } catch (McpRequestException e) {
            if (!isHeaderMismatch(e)) {
                throw toolsCallFailure(request, e);
            }
            log.info(
                    "MCP server {} rejected {} for tool '{}' with HeaderMismatch; refreshing the"
                            + " tool definition and retrying once: {}",
                    request.getMcpServer(),
                    e.getMethod(),
                    request.getMethod(),
                    e.getMessage());
        }

        // The single retry: attempt() runs tools/list again, so the headers use the fresh schema.
        try {
            return attempt(request);
        } catch (McpRequestException e) {
            throw toolsCallFailure(request, e);
        }
    }

    /**
     * One attempt: looks up the tool's schema with {@code tools/list}, builds its {@code
     * Mcp-Param-*} headers, and sends {@code tools/call}.
     *
     * <ul>
     *   <li>An HTTP or JSON-RPC error ({@link McpRequestException}) is left to the caller.
     *   <li>A failure that only this request form can have ({@link McpCallException}) keeps its own
     *       reason.
     *   <li>Every other failure (network, invalid URL or header, payload limit, refused redirect,
     *       malformed response) gets the reason of a legacy call.
     * </ul>
     *
     * @throws McpRequestException on an HTTP or JSON-RPC error from either request
     */
    private MCPService.ToolCallOutcome attempt(MCPToolCallRequest request) {
        Map<String, Object> arguments = request.getArguments();
        try {
            return mcpService.callToolTasks(
                    request.getMcpServer(),
                    request.getMethod(),
                    arguments,
                    request.getHeaders(),
                    paramHeaders(request, arguments));
        } catch (McpRequestException | McpCallException e) {
            throw e;
        } catch (MCPService.TransportException e) {
            throw synchronousFailure(request, e.getCause() != null ? e.getCause() : e);
        } catch (RuntimeException e) {
            throw synchronousFailure(request, e);
        }
    }

    /**
     * Finds the tool with a paged {@code tools/list} and builds its {@code Mcp-Param-*} headers.
     */
    private Map<String, String> paramHeaders(
            MCPToolCallRequest request, Map<String, Object> arguments) {
        String toolName = request.getMethod();
        JsonNode tool =
                mcpService
                        .listToolsPaged(request.getMcpServer(), toolName, request.getHeaders())
                        .orElseThrow(
                                () ->
                                        new McpCallException(
                                                "MCP tool '"
                                                        + toolName
                                                        + "' is not listed by the MCP server "
                                                        + request.getMcpServer()));
        return McpHeaderParams.headersFor(
                toolName, tool.get("inputSchema"), arguments == null ? Map.of() : arguments);
    }

    private static boolean isHeaderMismatch(McpRequestException e) {
        return e.getCode() != null && e.getCode() == McpRequestException.HEADER_MISMATCH;
    }

    /**
     * Maps a failed {@code tools/call} in the {@code 2026-07-28} form to the exception that fails
     * the task. Protocol errors that only the {@code 2026-07-28} form can produce get their own
     * reason; every other failure keeps the reason of a legacy call.
     */
    private static RuntimeException toolsCallFailure(
            MCPToolCallRequest request, McpRequestException e) {
        String server = request.getMcpServer();
        Integer code = e.getCode();
        if (code != null && code == McpRequestException.UNSUPPORTED_PROTOCOL_VERSION) {
            JsonNode supported = e.getData() != null ? e.getData().get("supported") : null;
            return new RuntimeException(
                    "MCP server "
                            + server
                            + " does not support MCP protocol version "
                            + McpProtocolHeaders.PROTOCOL_VERSION
                            + " (supported versions: "
                            + (supported != null ? supported : "not given by the server")
                            + "). Remove mcpOptions.protocolVersion to use the legacy MCP request.");
        }
        if (code != null && code == McpRequestException.MISSING_REQUIRED_CLIENT_CAPABILITY) {
            JsonNode required =
                    e.getData() != null ? e.getData().get("requiredCapabilities") : null;
            return new RuntimeException(
                    "MCP server "
                            + server
                            + " requires client capabilities that Conductor does not declare: "
                            + (required != null ? required : "not given by the server"));
        }
        if (isHeaderMismatch(e)) {
            return new RuntimeException(
                    "MCP server "
                            + server
                            + " rejected "
                            + e.getMethod()
                            + " for tool '"
                            + request.getMethod()
                            + "' with HeaderMismatch again after the tool definition was"
                            + " refreshed: "
                            + e.getMessage());
        }
        if (e.isOlderServerRejection()) {
            return new RuntimeException(
                    "MCP server "
                            + server
                            + " rejected the MCP "
                            + McpProtocolHeaders.PROTOCOL_VERSION
                            + " request form for tool '"
                            + request.getMethod()
                            + "' with HTTP "
                            + e.getHttpStatus()
                            + ". The server may support only an older MCP protocol version, or"
                            + " the URL may not be an MCP endpoint. If the server is an older MCP"
                            + " server, remove mcpOptions.protocolVersion to use the legacy MCP"
                            + " request. "
                            + e.getMessage());
        }
        String detail = e.synchronousDetail();
        return synchronousFailure(request, new RuntimeException(detail));
    }

    /**
     * Builds the exception that a legacy call throws for the same failure, so that the task's
     * reason is the same.
     */
    private static RuntimeException synchronousFailure(
            MCPToolCallRequest request, Throwable cause) {
        return new RuntimeException(
                "Failed to call MCP tool '"
                        + request.getMethod()
                        + "' on "
                        + request.getMcpServer()
                        + ": "
                        + cause.getMessage(),
                cause);
    }
}
