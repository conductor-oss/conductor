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

import java.time.Duration;
import java.util.Map;

import org.conductoross.conductor.ai.mcp.MCPCallException;
import org.conductoross.conductor.ai.mcp.MCPClientMetrics;
import org.conductoross.conductor.ai.mcp.MCPFields;
import org.conductoross.conductor.ai.mcp.MCPHeaderParams;
import org.conductoross.conductor.ai.mcp.MCPOptions;
import org.conductoross.conductor.ai.mcp.MCPProtocolHeaders;
import org.conductoross.conductor.ai.mcp.MCPRequestException;
import org.conductoross.conductor.ai.mcp.MCPRpcError;
import org.conductoross.conductor.ai.mcp.MCPService;
import org.conductoross.conductor.ai.mcp.MCPTaskStatus;
import org.conductoross.conductor.ai.model.MCPToolCallRequest;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.HEADER_MISMATCH;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.INVALID_PARAMS;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.RESOURCE_NOT_FOUND;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.UNSUPPORTED_PROTOCOL_VERSION;

/**
 * Lifecycle of a {@code CALL_MCP_TOOL} call that selects {@code mcpOptions.protocolVersion} (the
 * MCP {@code 2026-07-28} form, which declares the MCP Tasks extension). MCP-specific; structured
 * like {@code A2AWorkers}.
 *
 * <p>The server decides whether a call runs as a task. An immediate result completes the Conductor
 * task in the same run. A created task is polled with {@code tasks/get} on later runs, scheduled by
 * the existing async system-task lifecycle ({@code IN_PROGRESS} plus {@code callbackAfterSeconds}).
 * The only state kept between runs is the {@code mcpTask} output object ({@code taskId}, {@code
 * pollFailures}, {@code retryCount}, and the server's last {@code pollIntervalMs} and {@code
 * ttlMs}).
 *
 * <p>The deadline of a call is the configured maximum duration ({@code
 * conductor.mcp.client.maxDuration}) from the task's start, or the server's {@code ttlMs} when that
 * is shorter: the server keeps the task only for its {@code ttlMs}, so the result cannot be counted
 * on after it. The deadline is measured from the task's start, which is before the server created
 * the task, so it never ends after the server's own {@code ttlMs}.
 *
 * <p>{@code tools/call} is not idempotent and has no idempotency key. If its response is lost after
 * the server started the call (a transport error), the Conductor task fails with a retry, and the
 * retry sends {@code tools/call} again: the remote call runs twice. The call without Tasks has the
 * same limit.
 *
 * <p>Every outcome is applied through the {@link TaskContext} result, as {@code A2AWorkers.finish}
 * does: the callback is {@code 0} for every status other than {@code IN_PROGRESS}, and at least
 * {@code 1} for {@code IN_PROGRESS}.
 */
@Slf4j
final class MCPTasksCall {

    static final String STATE_KEY = "mcpTask";

    /** The wait between checks when the server gives no {@code pollIntervalMs}. */
    static final Duration DEFAULT_POLL = Duration.ofSeconds(5);

    static final int MAX_POLL_FAILURES = 30;

    /**
     * Upper bound of the wait after failed checks. The wait starts at the poll interval and doubles
     * after each consecutive failure, but never drops below the server's interval.
     */
    static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    /**
     * Upper bound of the doublings in {@link #backoffMs}; the wait reaches {@link #MAX_BACKOFF}
     * long before. The shift cannot overflow: its base is at most {@link #MAX_DURATION_LIMIT}.
     */
    private static final int MAX_BACKOFF_DOUBLINGS = 20;

    /** Upper bound of {@code conductor.mcp.client.maxDuration}. */
    static final Duration MAX_DURATION_LIMIT = Duration.ofDays(30);

    private static final long MILLIS_PER_SECOND = Duration.ofSeconds(1).toMillis();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();

    private final MCPService mcpService;

    /**
     * How long a call may poll before it fails with a terminal error, unless the server's {@code
     * ttlMs} is shorter.
     */
    private final Duration maxDuration;

    MCPTasksCall(MCPService mcpService, Duration maxDuration) {
        if (maxDuration == null
                || maxDuration.isNegative()
                || maxDuration.isZero()
                || maxDuration.compareTo(MAX_DURATION_LIMIT) > 0) {
            throw new IllegalArgumentException(
                    "conductor.mcp.client.maxDuration must be positive and at most "
                            + MAX_DURATION_LIMIT
                            + ", but is "
                            + maxDuration);
        }
        this.mcpService = mcpService;
        this.maxDuration = maxDuration;
    }

    MCPWorkers.ToolCallResult run(MCPToolCallRequest request) {
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            // Missing input: a retry cannot fix it.
            throw new NonRetryableException(
                    "CALL_MCP_TOOL requires the input 'method' (the name of the tool to call)");
        }
        Task task = TaskContext.get().getTask();
        // Reading the state from output data relies on in-progress output never being
        // externalized: while a call polls, its output holds only the small mcpTask object, as
        // A2A's polling state is small. Externalized output would read as empty here.
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
        MCPService.MCPTask created = outcome.task();
        log.debug(
                "MCP server {} created task {} for tool '{}'",
                request.getMcpServer(),
                created.taskId(),
                request.getMethod());
        return keepPolling(
                task,
                created.taskId(),
                0,
                created.pollIntervalMs(),
                created.ttlMs(),
                created.pollIntervalMs());
    }

    /** Later runs: one {@code tasks/get} for the stored task ID, mapped to a Conductor outcome. */
    private MCPWorkers.ToolCallResult poll(MCPToolCallRequest request, Task task, Object state) {
        MCPWorkers.MCPTaskState stored = MCPWorkers.MCPTaskState.from(state);
        String taskId = stored.taskId();
        int failures = stored.pollFailures();
        Long interval = stored.pollIntervalMs();
        Long ttl = stored.ttlMs();
        if (taskId == null) {
            return fail(
                    null,
                    0,
                    "Invalid MCP polling state in task output '" + STATE_KEY + "': " + state);
        }
        MCPService.MCPTask remote;
        try {
            remote = mcpService.getTask(request.getMcpServer(), taskId, request.getHeaders());
        } catch (MCPRequestException e) {
            Integer code = e.getCode();
            if (e.hasDefinedMeaning()) {
                // A JSON-RPC error with a defined meaning is the MCP server's own answer, so its
                // code decides, whatever the HTTP status.
                // The spec answers an unknown task with -32602; some servers send -32002.
                if (code == INVALID_PARAMS || code == RESOURCE_NOT_FOUND) {
                    if (deadlineExceeded(task, ttl)) {
                        // Past the deadline the server may have dropped the task, as its ttlMs
                        // allows. A retry would run into the same deadline, so do not retry.
                        return failTerminal(
                                taskId,
                                failures,
                                "MCP task '"
                                        + taskId
                                        + "' is no longer known to the server after "
                                        + describeDeadline(ttl)
                                        + ": "
                                        + e.getMessage());
                    }
                    return fail(
                            taskId,
                            failures,
                            "MCP task '"
                                    + taskId
                                    + "' expired or is unknown to the server: "
                                    + e.getMessage());
                }
                if (e.isInternalError()) {
                    return transientFailure(request, task, taskId, failures, interval, ttl, e);
                }
                // The server rejected the request; the remote task may still be running.
                cancelBestEffort(request, taskId, "tasks/get rejected");
                return fail(taskId, failures, e.getMessage());
            }
            // No JSON-RPC error (a gateway or proxy may have answered), or a code with no defined
            // meaning: the HTTP status decides.
            if (e.isAuthRejection()) {
                return fail(
                        taskId,
                        failures,
                        "MCP server "
                                + request.getMcpServer()
                                + " rejected tasks/get for task '"
                                + taskId
                                + "' with HTTP "
                                + e.getHttpStatus()
                                + ": "
                                + e.getMessage());
            }
            if (e.isTransientHttpError()) {
                return transientFailure(request, task, taskId, failures, interval, ttl, e);
            }
            // Any other HTTP error: the remote task may still be running.
            cancelBestEffort(request, taskId, "tasks/get rejected");
            return fail(taskId, failures, e.getMessage());
        } catch (MCPService.TransportException e) {
            return transientFailure(request, task, taskId, failures, interval, ttl, e);
        } catch (RuntimeException e) {
            // An unusable response (invalid task fields, or over the depth or size limits) fails
            // the task. The remote task may still be running, so cancel it first. The spec does
            // not define tasks/cancel on a finished task; the server may acknowledge or reject
            // it, and either is harmless.
            String reason = "MCP tasks/get for task '" + taskId + "' failed: " + e.getMessage();
            cancelBestEffort(request, taskId, "unusable tasks/get response");
            return fail(taskId, failures, reason);
        }

        switch (MCPTaskStatus.of(remote.status())) {
            case WORKING:
                // pollIntervalMs and ttlMs are optional on each response: keep the last ones the
                // server gave. A value of 0 or less counts as absent.
                Long nextTtl = positiveOr(remote.ttlMs(), ttl);
                if (deadlineExceeded(task, nextTtl)) {
                    return deadlineFailure(request, taskId, 0, nextTtl);
                }
                Long next = positiveOr(remote.pollIntervalMs(), interval);
                return keepPolling(task, taskId, 0, next, nextTtl, next);
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
                // As A2AWorkers does for a canceled agent task: no retry, which would start the
                // call the server cancelled again.
                return fail(
                        TaskResult.Status.CANCELED,
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
            case UNKNOWN:
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
     * The deadline has passed and the remote task is not finished: best-effort {@code
     * tasks/cancel}, then {@code FAILED_WITH_TERMINAL_ERROR}. Terminal, so that a retry does not
     * start the tool again only to run into the same deadline. Checked after {@code tasks/get}, so
     * a task that finished in the last interval still completes.
     */
    private MCPWorkers.ToolCallResult deadlineFailure(
            MCPToolCallRequest request, String taskId, int failures, Long ttlMs) {
        cancelBestEffort(request, taskId, "deadline exceeded");
        return failTerminal(
                taskId,
                failures,
                "MCP task '"
                        + taskId
                        + "' did not finish within "
                        + describeDeadline(ttlMs)
                        + "; Conductor sent tasks/cancel");
    }

    /** The deadline in words, for failure reasons. */
    private String describeDeadline(Long ttlMs) {
        long deadline = deadlineMs(ttlMs);
        if (deadline < maxDuration.toMillis()) {
            return "the server's ttlMs of " + deadline + " ms";
        }
        return "the maximum duration of " + describe(maxDuration);
    }

    /** A duration in whole hours when it is one, otherwise in milliseconds. */
    private static String describe(Duration duration) {
        if (duration.toMillis() % Duration.ofHours(1).toMillis() != 0) {
            return duration.toMillis() + " ms";
        }
        long hours = duration.toHours();
        return hours + (hours == 1 ? " hour" : " hours");
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
            Long ttlMs,
            RuntimeException error) {
        MCPClientMetrics.clientPollFailure();
        if (deadlineExceeded(task, ttlMs)) {
            return deadlineFailure(request, taskId, failures + 1, ttlMs);
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
        return keepPolling(
                task, taskId, count, pollIntervalMs, ttlMs, backoffMs(pollIntervalMs, count));
    }

    /**
     * The wait after {@code failures} consecutive failed checks: the server's interval (or the
     * default), doubled for each failure after the first, up to {@link #MAX_BACKOFF} but never
     * below the server's interval.
     */
    long backoffMs(Long pollIntervalMs, int failures) {
        long base =
                pollIntervalMs != null && pollIntervalMs > 0
                        ? Math.min(pollIntervalMs, maxDuration.toMillis())
                        : DEFAULT_POLL.toMillis();
        long cap = Math.max(base, MAX_BACKOFF.toMillis());
        int doublings = Math.min(Math.max(0, failures - 1), MAX_BACKOFF_DOUBLINGS);
        return Math.min(cap, base << doublings);
    }

    /**
     * Stays {@code IN_PROGRESS}: stores the state (with the server's interval and TTL) and waits
     * {@code waitMs} before the next check.
     */
    private MCPWorkers.ToolCallResult keepPolling(
            Task task, String taskId, int failures, Long pollIntervalMs, Long ttlMs, Long waitMs) {
        MCPWorkers.ToolCallResult result = new MCPWorkers.ToolCallResult();
        result.mcpTask = state(taskId, failures, pollIntervalMs, ttlMs);
        return finish(
                TaskResult.Status.IN_PROGRESS, callbackSeconds(task, waitMs, ttlMs), null, result);
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
    long callbackSeconds(Task task, Long pollIntervalMs, Long ttlMs) {
        long seconds =
                pollIntervalMs != null && pollIntervalMs > 0
                        ? Math.ceilDiv(
                                Math.min(pollIntervalMs, maxDuration.toMillis()), MILLIS_PER_SECOND)
                        : DEFAULT_POLL.toSeconds();
        long startTime = task.getStartTime();
        if (startTime > 0) {
            long remaining =
                    Duration.ofMillis(deadlineMs(ttlMs)).toSeconds()
                            - Duration.ofMillis(System.currentTimeMillis() - startTime).toSeconds();
            seconds = Math.min(seconds, Math.max(1, remaining + 1));
        }
        return Math.max(1, seconds);
    }

    /**
     * The deadline from the task's start: the server's {@code ttlMs} if shorter than the maximum
     * duration.
     */
    long deadlineMs(Long ttlMs) {
        return ttlMs != null && ttlMs > 0
                ? Math.min(ttlMs, maxDuration.toMillis())
                : maxDuration.toMillis();
    }

    private boolean deadlineExceeded(Task task, Long ttlMs) {
        long startTime = task.getStartTime();
        return startTime > 0 && System.currentTimeMillis() - startTime > deadlineMs(ttlMs);
    }

    /** {@code value} if positive, otherwise {@code fallback}. */
    private static Long positiveOr(Long value, Long fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    /** The polling state for this execution: the remote task ID, failures and retry count. */
    private static MCPWorkers.MCPTaskState state(String taskId, int failures) {
        return state(taskId, failures, null, null);
    }

    private static MCPWorkers.MCPTaskState state(
            String taskId, int failures, Long pollIntervalMs, Long ttlMs) {
        return new MCPWorkers.MCPTaskState(
                taskId,
                failures,
                TaskContext.get().getTask().getRetryCount(),
                positiveOr(pollIntervalMs, null),
                positiveOr(ttlMs, null));
    }

    /**
     * Called when Conductor cancels a {@code CALL_MCP_TOOL} task, for example on workflow
     * termination. Sends a best-effort {@code tasks/cancel} when the call selected a protocol
     * version and a remote task exists; otherwise does nothing. Never throws.
     *
     * <p>The remote task is cancelled only if this execution created it: when {@code mcpTask} has a
     * {@code retryCount} that differs from the task's, the state was left by an earlier execution
     * (the engine reset the task in place) and no request is sent. State without {@code retryCount}
     * is still cancelled. A rerun resets a failed task in place without changing its {@code
     * retryCount}; if that task is cancelled before it runs again, the old remote task gets a
     * cancel. That task has already ended, so the request is harmless.
     *
     * <p>Limitation: the task input seen here is the literal input, so headers written as {@code
     * ${workflow.secrets.*}} are not resolved, and the server may reject the cancel.
     */
    void cancel(Task task, String reason) {
        try {
            MCPToolCallRequest request =
                    OBJECT_MAPPER.convertValue(task.getInputData(), MCPToolCallRequest.class);
            if (MCPOptions.protocolVersion(request.getMcpOptions()) == null) {
                return;
            }
            MCPWorkers.MCPTaskState stored =
                    MCPWorkers.MCPTaskState.from(
                            task.getOutputData() == null
                                    ? null
                                    : task.getOutputData().get(STATE_KEY));
            if (stored.taskId() == null || !createdByThisExecution(stored, task)) {
                return;
            }
            cancelBestEffort(request, stored.taskId(), reason);
        } catch (RuntimeException e) {
            log.warn(
                    "Could not propagate cancellation of CALL_MCP_TOOL task {} ({}): {}",
                    task.getTaskId(),
                    reason,
                    e.getMessage());
        }
    }

    private static boolean createdByThisExecution(MCPWorkers.MCPTaskState stored, Task task) {
        return stored.retryCount() == null || stored.retryCount() == task.getRetryCount();
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

    private static String describe(MCPService.MCPTask remote) {
        if (remote.error() == null) {
            return remote.statusMessage() != null ? remote.statusMessage() : "no error given";
        }
        return MCPRpcError.from(remote.error()).describe();
    }

    /**
     * Looks up the tool's schema, then sends the {@code tools/call} in the {@code 2026-07-28} form.
     * On {@code HeaderMismatch} ({@code -32020}) the schema is fetched again and the call is
     * retried once.
     */
    private MCPService.ToolCallOutcome callTool(MCPToolCallRequest request) {
        try {
            return attempt(request);
        } catch (MCPRequestException e) {
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
        } catch (MCPRequestException e) {
            throw toolsCallFailure(request, e);
        }
    }

    /**
     * One attempt: looks up the tool's schema with {@code tools/list}, builds its {@code
     * Mcp-Param-*} headers, and sends {@code tools/call}.
     *
     * <ul>
     *   <li>An HTTP or JSON-RPC error ({@link MCPRequestException}) is left to the caller.
     *   <li>A failure that only this request form can have ({@link MCPCallException}) keeps its own
     *       reason.
     *   <li>Every other failure (network, invalid URL or header, payload limit, refused redirect,
     *       malformed response) gets the reason of a legacy call.
     * </ul>
     *
     * @throws MCPRequestException on an HTTP or JSON-RPC error from either request
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
        } catch (MCPRequestException | MCPCallException | NonRetryableException e) {
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
                                        new MCPCallException(
                                                "MCP tool '"
                                                        + toolName
                                                        + "' is not listed by the MCP server "
                                                        + request.getMcpServer()));
        try {
            return MCPHeaderParams.headersFor(
                    toolName,
                    tool.get(MCPFields.Tool.INPUT_SCHEMA),
                    arguments == null ? Map.of() : arguments);
        } catch (MCPCallException e) {
            // An invalid x-mcp-header annotation or argument: the same input fails the same way.
            throw new NonRetryableException(e.getMessage());
        }
    }

    private static boolean isHeaderMismatch(MCPRequestException e) {
        return e.getCode() != null && e.getCode() == HEADER_MISMATCH;
    }

    /**
     * Maps a failed {@code tools/call} in the {@code 2026-07-28} form to the exception that fails
     * the task. Protocol errors that only the {@code 2026-07-28} form can produce get their own
     * reason; every other failure keeps the reason of a legacy call.
     */
    private static RuntimeException toolsCallFailure(
            MCPToolCallRequest request, MCPRequestException e) {
        String server = request.getMcpServer();
        Integer code = e.getCode();
        if (code != null && code == UNSUPPORTED_PROTOCOL_VERSION) {
            JsonNode supported =
                    e.getData() != null ? e.getData().get(MCPFields.Error.SUPPORTED) : null;
            return new NonRetryableException(
                    "MCP server "
                            + server
                            + " does not support MCP protocol version "
                            + MCPProtocolHeaders.PROTOCOL_VERSION
                            + " (supported versions: "
                            + (supported != null ? supported : "not given by the server")
                            + "). Remove mcpOptions.protocolVersion to use the legacy MCP request.");
        }
        if (code != null && code == MISSING_REQUIRED_CLIENT_CAPABILITY) {
            JsonNode required =
                    e.getData() != null
                            ? e.getData().get(MCPFields.Error.REQUIRED_CAPABILITIES)
                            : null;
            return new NonRetryableException(
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
                            + MCPProtocolHeaders.PROTOCOL_VERSION
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
