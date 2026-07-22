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
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public ConductorAgentDelegate(ConductorAgentClient conductorAgentClient) {
        this.conductorAgentClient = conductorAgentClient;
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
            cancelBestEffort(executionId, "AGENT exceeded max duration");
            return fail(
                    result,
                    "AGENT exceeded max duration of "
                            + maxDurationSeconds(request)
                            + "s without the agent reaching a terminal state",
                    true);
        }

        try {
            ConductorAgentExecution execution;
            if (StringUtils.isBlank(executionId)) {
                execution = startOrResume(task, request);
            } else {
                execution =
                        fromStatus(
                                conductorAgentClient.getAgentStatus(executionId),
                                asString(
                                        result.getOutputData()
                                                .get(ConductorAgentResults.KEY_AGENT_NAME)));
                result.getOutputData().put(ConductorAgentResults.KEY_POLL_FAILURES, 0);
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
        String executionId =
                asString(
                        task.getOutputData() != null
                                ? task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID)
                                : null);
        if (StringUtils.isBlank(executionId)) {
            executionId = StringUtils.trimToNull(parseRequest(task).getExecutionId());
        }
        cancelBestEffort(
                executionId, StringUtils.defaultIfBlank(reason, "Cancelled by parent workflow"));
    }

    private ConductorAgentExecution startOrResume(Task task, ConductorAgentRequest request) {
        String executionId = StringUtils.trimToNull(request.getExecutionId());
        if (executionId != null) {
            if (StringUtils.isBlank(request.getPrompt())) {
                throw new NonRetryableException(
                        "AGENT (conductor) requires 'prompt' when resuming an execution");
            }
            conductorAgentClient.respond(
                    ConductorAgentRespondRequest.builder()
                            .executionId(executionId)
                            .body(Map.of("result", request.getPrompt()))
                            .build());
            return fromStatus(conductorAgentClient.getAgentStatus(executionId), null);
        }

        if (StringUtils.isBlank(request.getName())) {
            throw new NonRetryableException("AGENT (conductor) requires 'name'");
        }
        if (StringUtils.isBlank(request.getPrompt())) {
            throw new NonRetryableException("AGENT (conductor) requires 'prompt'");
        }
        request.setIdempotencyKey(
                StringUtils.firstNonBlank(request.getIdempotencyKey(), idempotencyKey(task)));
        ConductorAgentStartResponse response = conductorAgentClient.startAgent(request);
        return ConductorAgentExecution.builder()
                .executionId(response.getExecutionId())
                .agentName(response.getAgentName())
                .state(ConductorAgentState.RUNNING)
                .build();
    }

    private TaskResult handlePollFailure(
            TaskResult result, ConductorAgentRequest request, String executionId, Exception error) {
        int failures =
                (int) asLong(result.getOutputData().get(ConductorAgentResults.KEY_POLL_FAILURES), 0)
                        + 1;
        result.getOutputData().put(ConductorAgentResults.KEY_POLL_FAILURES, failures);
        int maxFailures = maxPollFailures(request);
        if (failures >= maxFailures) {
            cancelBestEffort(executionId, "Conductor agent unreachable");
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
                if (execution.getText() != null) {
                    output.put(ConductorAgentResults.KEY_TEXT, execution.getText());
                }
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
                .reasonForIncompletion(status.getReasonForIncompletion())
                .startTime(status.getStartTime())
                .endTime(status.getEndTime())
                .build();
    }

    private static String resultText(Map<String, Object> output) {
        Object text = output.get("text");
        if (!(text instanceof CharSequence)) {
            text = output.get("result");
        }
        return text instanceof CharSequence ? text.toString() : null;
    }

    private void cancelBestEffort(String executionId, String reason) {
        if (StringUtils.isBlank(executionId)) {
            return;
        }
        try {
            ConductorAgentStatusResponse status = conductorAgentClient.getAgentStatus(executionId);
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
