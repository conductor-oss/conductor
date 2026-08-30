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
package org.conductoross.conductor.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.agent.tools.AgentToolDispatch;
import org.conductoross.conductor.ai.agent.tools.AgentToolDispatcher;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives the {@code agentType=conductor} branch through the portable {@link ConductorAgentClient}.
 *
 * <p>Every invocation is one short start/respond/status call. Durable state lives in the owning
 * Conductor task's output, so the same code works as an embedded annotated system task and as a
 * remotely-polled Java SDK worker.
 */
@Slf4j
public class ConductorAgentDelegate {

    private static final long DEFAULT_POLL_SECONDS = 5;
    private static final long DEFAULT_MAX_DURATION_SECONDS = 24L * 60 * 60;
    private static final int DEFAULT_MAX_POLL_FAILURES = 30;

    private final ConductorAgentClient conductorAgentClient;
    private final AgentToolDispatcher toolDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public ConductorAgentDelegate(ConductorAgentClient conductorAgentClient) {
        this(conductorAgentClient, null);
    }

    /**
     * @param toolDispatcher runs the agent's tool calls as workflow tasks when the task asks for
     *     it. Null where that is not available — a remotely-polled worker has no engine to schedule
     *     on — in which case a tool request is handed back to the workflow as before.
     */
    public ConductorAgentDelegate(
            ConductorAgentClient conductorAgentClient, AgentToolDispatcher toolDispatcher) {
        this.conductorAgentClient = conductorAgentClient;
        this.toolDispatcher = toolDispatcher;
    }

    /** Starts/resumes a run on the first invocation and polls it on later invocations. */
    public TaskResult execute(Task task) {
        TaskResult result = resultFor(task);
        ConductorAgentRequest request = parseRequest(task);
        result.getOutputData()
                .putIfAbsent(ConductorAgentResults.KEY_START_TIME, System.currentTimeMillis());

        String executionId =
                asString(result.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        if (StringUtils.isNotBlank(executionId) && deadlineExceeded(result, request)) {
            cancelBestEffort(executionId, request, "AGENT exceeded max duration");
            return fail(
                    result,
                    "AGENT exceeded max duration of "
                            + maxDurationSeconds(request)
                            + "s without the agent reaching a terminal state",
                    true);
        }

        String toolDispatchId =
                asString(result.getOutputData().get(ConductorAgentResults.KEY_TOOL_DISPATCH_ID));
        if (StringUtils.isNotBlank(toolDispatchId)) {
            return advanceToolDispatch(
                    result, request, executionId, toolDispatchId, task.getReferenceTaskName());
        }

        try {
            ConductorAgentExecution execution;
            if (StringUtils.isBlank(executionId)) {
                execution = startOrResume(task, request);
            } else {
                execution =
                        fromStatus(
                                conductorAgentClient.getAgentStatus(executionId, request),
                                asString(
                                        result.getOutputData()
                                                .get(ConductorAgentResults.KEY_AGENT_NAME)));
                result.getOutputData().put(ConductorAgentResults.KEY_POLL_FAILURES, 0);
            }
            if (shouldRunToolsHere(execution, request)) {
                return dispatchTools(result, execution, request, task.getReferenceTaskName());
            }
            applyExecution(result, execution, request, pollInterval(request));
            return result;
        } catch (NonRetryableException | IllegalArgumentException e) {
            return fail(result, e.getMessage(), true);
        } catch (Exception e) {
            if (StringUtils.isBlank(executionId)) {
                return fail(result, "Conductor agent call failed: " + e.getMessage(), false);
            }
            return handlePollFailure(result, request, executionId, e);
        }
    }

    /** Best-effort cancellation hook used by embedded parent cancellation. */
    public void cancel(Task task, String reason) {
        ConductorAgentRequest request = parseRequest(task);
        String executionId =
                asString(
                        task.getOutputData() != null
                                ? task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID)
                                : null);
        if (StringUtils.isBlank(executionId)) {
            executionId = StringUtils.trimToNull(request.getExecutionId());
        }
        // Tools running on this agent's behalf are not wanted either, and nothing else will stop
        // them — cascade termination is explicit, not something the engine does off
        // parentWorkflowId.
        cancelToolDispatch(task);
        cancelBestEffort(
                executionId,
                request,
                StringUtils.defaultIfBlank(reason, "Cancelled by parent workflow"));
    }

    private void cancelToolDispatch(Task task) {
        if (toolDispatcher == null || task.getOutputData() == null) {
            return;
        }
        String toolDispatchId =
                asString(task.getOutputData().get(ConductorAgentResults.KEY_TOOL_DISPATCH_ID));
        if (StringUtils.isBlank(toolDispatchId)) {
            return;
        }
        try {
            toolDispatcher.cancel(toolDispatchId);
        } catch (Exception e) {
            log.warn("Failed to cancel agent tool dispatch {}: {}", toolDispatchId, e.getMessage());
        }
    }

    private ConductorAgentExecution startOrResume(Task task, ConductorAgentRequest request) {
        String executionId = StringUtils.trimToNull(request.getExecutionId());
        if (executionId != null) {
            if (StringUtils.isBlank(request.getPrompt())) {
                throw new NonRetryableException(
                        "AGENT (conductor) requires 'prompt' when resuming an execution");
            }
            ConductorAgentStatusResponse afterRespond =
                    conductorAgentClient.respondWithStatus(
                            ConductorAgentRespondRequest.builder()
                                    .executionId(executionId)
                                    .agentUrl(request.getAgentUrl())
                                    .body(Map.of("result", request.getPrompt()))
                                    .credentials(request.getCredentials())
                                    .rawConfig(request.getRawConfig())
                                    .pendingTool(pendingToolFrom(task))
                                    .pendingTools(pendingToolsFrom(task))
                                    .build());
            // A runtime with no status API answers inside respond(); everything else says null and
            // is polled as usual.
            return fromStatus(
                    afterRespond != null
                            ? afterRespond
                            : conductorAgentClient.getAgentStatus(executionId, request),
                    null);
        }

        if (StringUtils.isBlank(request.getPrompt())) {
            throw new NonRetryableException("AGENT requires 'prompt'");
        }
        request.setIdempotencyKey(
                StringUtils.firstNonBlank(request.getIdempotencyKey(), idempotencyKey(task)));
        ConductorAgentStartResponse response = conductorAgentClient.startAgent(request);
        // Bedrock and friends stream the whole turn inside startAgent, so the run can already be
        // finished or blocked on a tool. Taking that state here is what puts the result in the task
        // output rather than leaving it in the client for a poll that may land elsewhere.
        return ConductorAgentExecution.builder()
                .executionId(response.getExecutionId())
                .agentName(response.getAgentName())
                .state(
                        response.getState() != null
                                ? response.getState()
                                : ConductorAgentState.RUNNING)
                .output(response.getOutput())
                .text(response.getOutput() != null ? resultText(response.getOutput()) : null)
                .pendingTool(response.getPendingTool())
                .pendingTools(response.getPendingTools())
                .executedTools(response.getExecutedTools())
                .reasonForIncompletion(response.getReasonForIncompletion())
                .build();
    }

    private boolean shouldRunToolsHere(
            ConductorAgentExecution execution, ConductorAgentRequest request) {
        return toolDispatcher != null
                && Boolean.TRUE.equals(request.getAutoRunTools())
                && execution.getState() == ConductorAgentState.WAITING
                && execution.getPendingTools() != null
                && !execution.getPendingTools().isEmpty();
    }

    /**
     * Schedules the requested tools and keeps this task IN_PROGRESS while they run, so the agent
     * and its tools stay one node in the workflow rather than a hand-wired dispatch branch.
     */
    private TaskResult dispatchTools(
            TaskResult result,
            ConductorAgentExecution execution,
            ConductorAgentRequest request,
            String taskRefName) {
        Map<String, Object> output = result.getOutputData();
        output.put(ConductorAgentResults.KEY_EXECUTION_ID, execution.getExecutionId());
        if (execution.getAgentName() != null) {
            output.put(ConductorAgentResults.KEY_AGENT_NAME, execution.getAgentName());
        }
        output.put(ConductorAgentResults.KEY_PENDING_TOOLS, execution.getPendingTools());
        if (execution.getPendingTool() != null) {
            output.put(ConductorAgentResults.KEY_PENDING_TOOL, execution.getPendingTool());
        }

        AgentToolDispatch dispatch =
                toolDispatcher.dispatch(
                        new AgentToolDispatcher.Request(
                                result.getWorkflowInstanceId(),
                                result.getTaskId(),
                                taskRefName,
                                execution.getExecutionId(),
                                execution.getPendingTools(),
                                request.getToolTaskNames()));

        ConductorAgentResults.writeExecutedTools(output, execution);
        output.put(ConductorAgentResults.KEY_TOOL_DISPATCH_ID, dispatch.dispatchId());
        // Both, matching the SUB_WORKFLOW system task: the field carries the relationship, and the
        // output copy is what the execution view reads to offer a drill-in from the agent to the
        // tools it is waiting on.
        result.setSubWorkflowId(dispatch.dispatchId());
        output.put(ConductorAgentResults.KEY_SUB_WORKFLOW_ID, dispatch.dispatchId());
        result.setStatus(TaskResult.Status.IN_PROGRESS);
        result.setCallbackAfterSeconds(pollInterval(request));
        log.debug(
                "Agent execution {} requested {} tool(s); dispatched as {}",
                execution.getExecutionId(),
                execution.getPendingTools().size(),
                dispatch.dispatchId());
        return result;
    }

    /** Waits on an in-flight tool batch, then feeds its results back to the agent. */
    private TaskResult advanceToolDispatch(
            TaskResult result,
            ConductorAgentRequest request,
            String executionId,
            String toolDispatchId,
            String taskRefName) {
        AgentToolDispatch dispatch;
        try {
            dispatch = toolDispatcher.status(toolDispatchId);
        } catch (Exception e) {
            return handlePollFailure(result, request, executionId, e);
        }

        switch (dispatch.state()) {
            case RUNNING:
                result.setStatus(TaskResult.Status.IN_PROGRESS);
                result.setCallbackAfterSeconds(pollInterval(request));
                return result;
            case FAILED:
                // The tool's own retry policy is its contract; exhausting it fails the agent task
                // and
                // lets the workflow's error handling take over. The model never sees the failure.
                toolDispatcher.cancel(toolDispatchId);
                cancelBestEffort(executionId, request, "Agent tool execution failed");
                return fail(result, dispatch.reason(), true);
            case COMPLETED:
            default:
                break;
        }

        try {
            ConductorAgentStatusResponse afterRespond =
                    conductorAgentClient.respondWithStatus(
                            ConductorAgentRespondRequest.builder()
                                    .executionId(executionId)
                                    .agentUrl(request.getAgentUrl())
                                    .toolResults(dispatch.resultsByToolCallId())
                                    .credentials(request.getCredentials())
                                    .rawConfig(request.getRawConfig())
                                    .build());
            // Cleared only once the results are actually in. Clearing before the call would, on a
            // transient failure, leave a task that looks as if it never dispatched anything - and
            // the next poll would read the same outstanding tool calls and run every one again.
            result.getOutputData().remove(ConductorAgentResults.KEY_TOOL_DISPATCH_ID);
            result.getOutputData().remove(ConductorAgentResults.KEY_PENDING_TOOL);
            result.getOutputData().remove(ConductorAgentResults.KEY_PENDING_TOOLS);
            ConductorAgentExecution execution =
                    fromStatus(
                            afterRespond != null
                                    ? afterRespond
                                    : conductorAgentClient.getAgentStatus(executionId, request),
                            asString(
                                    result.getOutputData()
                                            .get(ConductorAgentResults.KEY_AGENT_NAME)));
            // The next turn may ask for tools again — that is another batch, not a failure.
            if (shouldRunToolsHere(execution, request)) {
                return dispatchTools(result, execution, request, taskRefName);
            }
            applyExecution(result, execution, request, pollInterval(request));
            return result;
        } catch (NonRetryableException | IllegalArgumentException e) {
            return fail(result, e.getMessage(), true);
        } catch (Exception e) {
            return handlePollFailure(result, request, executionId, e);
        }
    }

    private TaskResult handlePollFailure(
            TaskResult result, ConductorAgentRequest request, String executionId, Exception error) {
        int failures =
                (int) asLong(result.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES), 0)
                        + 1;
        result.getOutputData().put(ConductorAgentResults.KEY_POLL_FAILURES, failures);
        int maxFailures = maxPollFailures(request);
        if (failures >= maxFailures) {
            cancelBestEffort(executionId, request, "Conductor agent unreachable");
            return fail(
                    result,
                    "Conductor agent unreachable after "
                            + failures
                            + " consecutive poll failures: "
                            + error.getMessage(),
                    true);
        }
        log.warn(
                "Transient error polling conductor agent execution {} ({}/{}): {}",
                executionId,
                failures,
                maxFailures,
                error.getMessage());
        result.setStatus(TaskResult.Status.IN_PROGRESS);
        result.setCallbackAfterSeconds(pollInterval(request));
        return result;
    }

    private void applyExecution(
            TaskResult result,
            ConductorAgentExecution execution,
            ConductorAgentRequest request,
            long pollIntervalSeconds) {
        Map<String, Object> output = result.getOutputData();
        output.put(ConductorAgentResults.KEY_EXECUTION_ID, execution.getExecutionId());
        if (execution.getAgentName() != null) {
            output.put(ConductorAgentResults.KEY_AGENT_NAME, execution.getAgentName());
        }
        result.setSubWorkflowId(execution.getExecutionId());

        ConductorAgentState state =
                execution.getState() != null ? execution.getState() : ConductorAgentState.RUNNING;
        if (execution.getStartTime() != null && execution.getStartTime() > 0) {
            output.put(ConductorAgentResults.KEY_START_TIME, execution.getStartTime());
        }
        if (state.isTerminal()) {
            long endTime =
                    execution.getEndTime() != null && execution.getEndTime() > 0
                            ? execution.getEndTime()
                            : System.currentTimeMillis();
            execution.setEndTime(endTime);
            output.put(ConductorAgentResults.KEY_END_TIME, endTime);
        }
        switch (state) {
            case WAITING:
                output.put(ConductorAgentResults.KEY_WAITING, true);
                if (execution.getPendingTool() != null) {
                    output.put(ConductorAgentResults.KEY_PENDING_TOOL, execution.getPendingTool());
                }
                if (execution.getPendingTools() != null && !execution.getPendingTools().isEmpty()) {
                    output.put(
                            ConductorAgentResults.KEY_PENDING_TOOLS, execution.getPendingTools());
                }
                if (execution.getText() != null) {
                    output.put(ConductorAgentResults.KEY_TEXT, execution.getText());
                }
                // A turn that stops to ask for a function has usually run built-in tools getting
                // there. writeCompleted is the only other writer of these, so without this they
                // are lost on every turn that is not the last one.
                ConductorAgentResults.writeExecutedTools(output, execution);
                result.setStatus(TaskResult.Status.COMPLETED);
                break;
            case COMPLETED:
                ConductorAgentResults.writeCompleted(output, execution);
                result.setStatus(TaskResult.Status.COMPLETED);
                break;
            case FAILED:
                result.setStatus(TaskResult.Status.FAILED);
                result.setReasonForIncompletion(
                        StringUtils.defaultIfBlank(
                                execution.getReasonForIncompletion(),
                                "Conductor agent execution failed"));
                break;
            case CANCELED:
                result.setStatus(TaskResult.Status.CANCELED);
                result.setReasonForIncompletion(
                        StringUtils.defaultIfBlank(
                                execution.getReasonForIncompletion(),
                                "Conductor agent execution was canceled"));
                break;
            case RUNNING:
            default:
                result.setStatus(TaskResult.Status.IN_PROGRESS);
                result.setCallbackAfterSeconds(pollIntervalSeconds);
                break;
        }
        ConductorAgentResults.writeA2AOutput(
                output, execution, request.getSessionId(), objectMapper);
    }

    private ConductorAgentExecution fromStatus(
            ConductorAgentStatusResponse status, String knownAgentName) {
        ConductorAgentState state =
                status.isWaiting()
                        ? ConductorAgentState.WAITING
                        : status.getStatus() != null
                                ? status.getStatus()
                                : ConductorAgentState.RUNNING;
        Map<String, Object> output = status.isComplete() ? status.getOutput() : null;
        return ConductorAgentExecution.builder()
                .executionId(status.getExecutionId())
                .agentName(knownAgentName)
                .state(state)
                .output(output)
                .text(output != null ? resultText(output) : null)
                .pendingTool(status.getPendingTool())
                .pendingTools(status.getPendingTools())
                .executedTools(status.getExecutedTools())
                .reasonForIncompletion(status.getReasonForIncompletion())
                .startTime(status.getStartTime())
                .endTime(status.getEndTime())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pendingToolsFrom(Task task) {
        if (task.getOutputData() == null) {
            return null;
        }
        Object pendingTools = task.getOutputData().get(ConductorAgentResults.KEY_PENDING_TOOLS);
        return pendingTools instanceof List<?> list ? (List<Map<String, Object>>) list : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pendingToolFrom(Task task) {
        if (task.getOutputData() == null) {
            return null;
        }
        Object pendingTool = task.getOutputData().get(ConductorAgentResults.KEY_PENDING_TOOL);
        return pendingTool instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String resultText(Map<String, Object> output) {
        Object text = output.get("text");
        if (!(text instanceof CharSequence)) {
            text = output.get("result");
        }
        return text instanceof CharSequence ? text.toString() : null;
    }

    private void cancelBestEffort(
            String executionId, ConductorAgentRequest request, String reason) {
        if (StringUtils.isBlank(executionId)) {
            return;
        }
        try {
            ConductorAgentStatusResponse status =
                    conductorAgentClient.getAgentStatus(executionId, request);
            if (status != null && status.isComplete()) {
                return;
            }
        } catch (Exception ignored) {
            // Still attempt cancellation when the status probe is unavailable.
        }
        try {
            conductorAgentClient.cancelAgent(
                    ConductorAgentCancelRequest.builder()
                            .executionId(executionId)
                            .reason(reason)
                            .agentUrl(request.getAgentUrl())
                            .credentials(request.getCredentials())
                            .rawConfig(request.getRawConfig())
                            .build());
        } catch (Exception e) {
            log.warn(
                    "Failed to propagate {} to conductor agent execution {}: {}",
                    reason,
                    executionId,
                    e.getMessage());
        }
    }

    private ConductorAgentRequest parseRequest(Task task) {
        return objectMapper.convertValue(task.getInputData(), ConductorAgentRequest.class);
    }

    private static TaskResult resultFor(Task task) {
        TaskResult result = new TaskResult(task);
        result.setOutputData(
                new LinkedHashMap<>(
                        task.getOutputData() != null ? task.getOutputData() : Map.of()));
        return result;
    }

    private static TaskResult fail(TaskResult result, String reason, boolean nonRetryable) {
        result.setStatus(
                nonRetryable
                        ? TaskResult.Status.FAILED_WITH_TERMINAL_ERROR
                        : TaskResult.Status.FAILED);
        result.setReasonForIncompletion(reason);
        return result;
    }

    private boolean deadlineExceeded(TaskResult result, ConductorAgentRequest request) {
        long startedAt =
                asLong(
                        result.getOutputData().get(ConductorAgentResults.KEY_START_TIME),
                        System.currentTimeMillis());
        return System.currentTimeMillis() - startedAt > maxDurationSeconds(request) * 1000L;
    }

    private static long pollInterval(ConductorAgentRequest request) {
        return request.getPollIntervalSeconds() != null
                ? Math.max(1, request.getPollIntervalSeconds())
                : DEFAULT_POLL_SECONDS;
    }

    private static long maxDurationSeconds(ConductorAgentRequest request) {
        return request.getMaxDurationSeconds() != null
                ? Math.max(1, request.getMaxDurationSeconds())
                : DEFAULT_MAX_DURATION_SECONDS;
    }

    private static int maxPollFailures(ConductorAgentRequest request) {
        return request.getMaxPollFailures() != null
                ? Math.max(1, request.getMaxPollFailures())
                : DEFAULT_MAX_POLL_FAILURES;
    }

    private static String idempotencyKey(Task task) {
        return "conductor-agent-"
                + task.getWorkflowInstanceId()
                + ":"
                + task.getReferenceTaskName()
                + ":"
                + task.getIteration();
    }

    private static long asLong(Object value, long defaultValue) {
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
