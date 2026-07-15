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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.model.A2ACallRequest;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives the {@code AGENT} (conductor) task branch against the embedded {@link
 * ConductorAgentRuntime}. Non-blocking, mirroring {@link
 * org.conductoross.conductor.ai.a2a.AgentTask}'s A2A branch: a fresh call starts (or, when an
 * {@code executionId} is supplied, resumes) an execution; while the run is not terminal the task
 * stays {@code IN_PROGRESS} and is re-polled at the task's evaluation cadence.
 *
 * <p>Two liveness guards prevent a stuck run from polling forever:
 *
 * <ul>
 *   <li><b>Guard 1 — absolute deadline:</b> anchored once via {@link
 *       ConductorAgentResults#KEY_STARTED_AT}, the task fails terminally after {@code
 *       maxDurationSeconds} (default 24h) if the run never reaches a terminal state.
 *   <li><b>Guard 2 — poll-failure cap:</b> consecutive transient {@code getStatus} failures are
 *       counted in {@link ConductorAgentResults#KEY_POLL_FAILURES}; after {@code maxPollFailures}
 *       (default 30) the task fails terminally.
 * </ul>
 *
 * <p>When the embedded runtime is absent the branch fails terminally: Conductor agents can only run
 * when the agentspan runtime is embedded ({@code agentspan.embedded=true}).
 */
@Slf4j
public class ConductorAgentDelegate {

    private static final long DEFAULT_MAX_DURATION_SECONDS = 24L * 60 * 60;
    private static final int DEFAULT_MAX_POLL_FAILURES = 30;

    private static final String RUNTIME_ABSENT_MESSAGE =
            "Conductor agents require the embedded agentspan runtime (agentspan.embedded=true)";

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final Optional<ConductorAgentRuntime> runtime;

    public ConductorAgentDelegate(Optional<ConductorAgentRuntime> runtime) {
        this.runtime = runtime;
    }

    /**
     * Handles {@code start}: fresh-start or resume, then routes the resulting snapshot. The parsed
     * request is supplied by the caller ({@code AgentTask}) so it is parsed exactly once per call.
     */
    public void start(TaskModel task, A2ACallRequest request) {
        if (runtime.isEmpty()) {
            fail(task, RUNTIME_ABSENT_MESSAGE, true);
            return;
        }
        ConductorAgentRuntime rt = runtime.get();

        // Record the start time once so execute() can enforce the absolute deadline across
        // restarts and retries (survives in the persisted task output).
        if (task.getOutputData().get(ConductorAgentResults.KEY_STARTED_AT) == null) {
            task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());
        }

        String prompt = resolvePrompt(request);
        String executionId = StringUtils.trimToNull(request.getExecutionId());
        try {
            ConductorAgentExecution execution;
            if (executionId != null) {
                // Resume path: feed the caller's message back into a waiting execution as the
                // pending tool/human result, then re-read the resulting snapshot. A blank prompt
                // is a permanent bad input — fail terminally rather than letting Map.of() NPE and
                // get misclassified as a retryable failure.
                if (StringUtils.isBlank(prompt)) {
                    fail(task, "AGENT (conductor) requires 'text' or 'prompt'", true);
                    return;
                }
                rt.respond(executionId, Map.of("result", prompt));
                execution = rt.getStatus(executionId);
            } else {
                // Fresh start: agentName and a prompt are both required.
                if (StringUtils.isBlank(request.getAgentName())) {
                    fail(task, "AGENT (conductor) requires 'agentName'", true);
                    return;
                }
                if (StringUtils.isBlank(prompt)) {
                    fail(task, "AGENT (conductor) requires 'text' or 'prompt'", true);
                    return;
                }
                ConductorAgentStartRequest startRequest =
                        ConductorAgentStartRequest.builder()
                                .agentName(request.getAgentName())
                                .agentVersion(request.getAgentVersion())
                                .prompt(prompt)
                                .context(request.getContext())
                                .sessionId(request.getSessionId())
                                .runId(request.getRunId())
                                .idempotencyKey(idempotencyKey(task))
                                .build();
                execution = rt.start(startRequest);
            }
            applyExecution(task, execution);
        } catch (NonRetryableAgentException | NonRetryableException e) {
            // Permanent runtime error (unknown agent, malformed request, nothing pending to resume)
            // — retrying cannot fix it, so fail terminally rather than retryable.
            fail(task, e.getMessage(), true);
        } catch (Exception e) {
            // Runtime call failures are transient — let the task retry. The retry reuses the same
            // deterministic idempotency key, from which the runtime derives a deterministic
            // workflow
            // id; because that id is the execution store's primary key, a re-issued start resolves
            // to
            // the same execution instead of duplicating the agent run.
            fail(task, "Conductor agent call failed: " + e.getMessage(), false);
        }
    }

    /**
     * Handles {@code execute}: polls the run and applies the snapshot. The parsed request is
     * supplied by the caller ({@code AgentTask}) so it is parsed exactly once per call.
     */
    public boolean execute(TaskModel task, A2ACallRequest request) {
        if (runtime.isEmpty()) {
            fail(task, RUNTIME_ABSENT_MESSAGE, true);
            return true;
        }
        ConductorAgentRuntime rt = runtime.get();
        String executionId =
                asString(task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        if (StringUtils.isBlank(executionId)) {
            fail(task, "No conductor agent execution to poll", true);
            return true;
        }
        // Liveness guard 1: absolute deadline.
        if (deadlineExceeded(task, request)) {
            fail(
                    task,
                    "AGENT exceeded max duration of "
                            + maxDurationSeconds(request)
                            + "s without the agent reaching a terminal state",
                    true);
            return true;
        }
        try {
            ConductorAgentExecution execution = rt.getStatus(executionId);
            task.addOutput(ConductorAgentResults.KEY_POLL_FAILURES, 0); // reset on success
            applyExecution(task, execution);
            // Still working -> stay IN_PROGRESS so the engine re-polls.
            return task.getStatus() != TaskModel.Status.IN_PROGRESS;
        } catch (NonRetryableAgentException | NonRetryableException e) {
            // Permanent runtime error — do not count against the transient poll-failure cap.
            fail(task, e.getMessage(), true);
            return true;
        } catch (Exception e) {
            // Liveness guard 2: bound consecutive transient failures so an unreachable runtime
            // doesn't keep us polling indefinitely (until the deadline).
            int failures =
                    (int)
                                    asLong(
                                            task.getOutputData()
                                                    .get(ConductorAgentResults.KEY_POLL_FAILURES),
                                            0)
                            + 1;
            task.addOutput(ConductorAgentResults.KEY_POLL_FAILURES, failures);
            int max = maxPollFailures(request);
            if (failures >= max) {
                fail(
                        task,
                        "Conductor agent unreachable after "
                                + failures
                                + " consecutive poll failures: "
                                + e.getMessage(),
                        true);
                return true;
            }
            log.warn(
                    "Transient error polling conductor agent execution {} ({}/{}): {}",
                    executionId,
                    failures,
                    max,
                    e.getMessage());
            return false;
        }
    }

    /** Handles {@code cancel}: best-effort propagation to the runtime, then marks CANCELED. */
    public void cancel(TaskModel task, String reason) {
        String executionId =
                asString(task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        if (runtime.isPresent() && !StringUtils.isBlank(executionId)) {
            try {
                runtime.get().cancel(executionId, reason);
            } catch (Exception e) {
                log.warn(
                        "Failed to propagate cancel to conductor agent execution {}: {}",
                        executionId,
                        e.getMessage());
            }
        }
        task.setStatus(TaskModel.Status.CANCELED);
    }

    /** Routes an execution snapshot onto this Conductor task's status/output. */
    private void applyExecution(TaskModel task, ConductorAgentExecution execution) {
        // Identity fields are captured at start(); a later poll snapshot may not repopulate them
        // (the runtime adapter's getStatus() cannot always resurface agentName/sessionId). Never
        // overwrite an already-recorded value with null.
        putIfPresent(task, ConductorAgentResults.KEY_EXECUTION_ID, execution.getExecutionId());
        putIfPresent(task, ConductorAgentResults.KEY_AGENT_NAME, execution.getAgentName());
        putIfPresent(task, ConductorAgentResults.KEY_SESSION_ID, execution.getSessionId());
        ConductorAgentState state =
                execution.getState() != null ? execution.getState() : ConductorAgentState.RUNNING;
        task.addOutput(ConductorAgentResults.KEY_STATE, state.name());
        switch (state) {
            case WAITING:
                // Paused for external input: complete and surface the pending request so the
                // workflow can resume with another AGENT call carrying the same executionId.
                task.addOutput(ConductorAgentResults.KEY_WAITING, true);
                if (execution.getPendingTool() != null) {
                    task.addOutput(
                            ConductorAgentResults.KEY_PENDING_TOOL, execution.getPendingTool());
                }
                if (execution.getText() != null) {
                    task.addOutput(ConductorAgentResults.KEY_TEXT, execution.getText());
                }
                task.setStatus(TaskModel.Status.COMPLETED);
                break;
            case COMPLETED:
                ConductorAgentResults.writeCompleted(task, execution);
                task.setStatus(TaskModel.Status.COMPLETED);
                break;
            case FAILED:
                task.setStatus(TaskModel.Status.FAILED);
                task.setReasonForIncompletion(
                        execution.getReasonForIncompletion() != null
                                ? execution.getReasonForIncompletion()
                                : "Conductor agent execution failed");
                break;
            case CANCELED:
                task.setStatus(TaskModel.Status.CANCELED);
                if (execution.getReasonForIncompletion() != null) {
                    task.setReasonForIncompletion(execution.getReasonForIncompletion());
                }
                break;
            case RUNNING:
            default:
                task.setStatus(TaskModel.Status.IN_PROGRESS);
                break;
        }
    }

    /**
     * Resolves the prompt from the same precedence the A2A branch uses: text carried in an explicit
     * {@code message}/{@code parts} first, then the convenience {@code text}, then {@code prompt}.
     */
    private String resolvePrompt(A2ACallRequest request) {
        String fromMessage = textFromMessage(request.getMessage());
        if (StringUtils.isBlank(fromMessage)) {
            fromMessage = textFromParts(request.getParts());
        }
        return StringUtils.firstNonBlank(fromMessage, request.getText(), request.getPrompt());
    }

    private String textFromMessage(Map<String, Object> message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        A2AMessage converted = objectMapper.convertValue(message, A2AMessage.class);
        return partsText(converted.getParts());
    }

    private String textFromParts(List<Map<String, Object>> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        List<Part> converted = new ArrayList<>();
        for (Map<String, Object> raw : parts) {
            converted.add(objectMapper.convertValue(raw, Part.class));
        }
        return partsText(converted);
    }

    private static String partsText(List<Part> parts) {
        if (parts == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : parts) {
            if (part != null && part.getText() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(part.getText());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * Deterministic, restart-stable idempotency key. Built from identity Conductor preserves across
     * task retries ({@code workflowInstanceId} + {@code referenceTaskName} + {@code iteration}) —
     * NOT {@code taskId}, which changes per retry attempt.
     */
    private String idempotencyKey(TaskModel task) {
        return "conductor-agent-"
                + task.getWorkflowInstanceId()
                + ":"
                + task.getReferenceTaskName()
                + ":"
                + task.getIteration();
    }

    private boolean deadlineExceeded(TaskModel task, A2ACallRequest request) {
        long startedAt =
                asLong(
                        task.getOutputData().get(ConductorAgentResults.KEY_STARTED_AT),
                        System.currentTimeMillis());
        return System.currentTimeMillis() - startedAt > maxDurationSeconds(request) * 1000L;
    }

    private long maxDurationSeconds(A2ACallRequest request) {
        return request.getMaxDurationSeconds() != null
                ? Math.max(1, request.getMaxDurationSeconds())
                : DEFAULT_MAX_DURATION_SECONDS;
    }

    private int maxPollFailures(A2ACallRequest request) {
        return request.getMaxPollFailures() != null
                ? Math.max(1, request.getMaxPollFailures())
                : DEFAULT_MAX_POLL_FAILURES;
    }

    private void fail(TaskModel task, String reason, boolean nonRetryable) {
        task.setStatus(
                nonRetryable
                        ? TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
                        : TaskModel.Status.FAILED);
        task.setReasonForIncompletion(reason);
    }

    /**
     * Reads a numeric value this task previously wrote to its own output. Persistence round-trips
     * JSON numbers back as a {@link Number} (Integer/Long), so only that case can occur.
     */
    private static long asLong(Object value, long defaultValue) {
        return value instanceof Number ? ((Number) value).longValue() : defaultValue;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Writes an output value only when non-null, so a poll snapshot can't erase a recorded one. */
    private static void putIfPresent(TaskModel task, String key, Object value) {
        if (value != null) {
            task.addOutput(key, value);
        }
    }
}
