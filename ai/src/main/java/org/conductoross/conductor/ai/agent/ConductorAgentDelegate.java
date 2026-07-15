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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives the {@code AGENT} (conductor) task branch directly through Conductor's {@link
 * WorkflowExecutor}. Non-blocking, mirroring {@link org.conductoross.conductor.ai.a2a.AgentTask}'s
 * A2A branch: a fresh call starts (or, when an {@code executionId} is supplied, resumes) an
 * execution; while the run is not terminal the task stays {@code IN_PROGRESS} and is re-polled at
 * the task's evaluation cadence.
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
 * <p>Agent executions are ordinary Conductor workflows. Keeping the translation here avoids a
 * dependency from the {@code AGENT} system task back into AgentSpan's service layer.
 */
@Slf4j
public class ConductorAgentDelegate {

    private static final long DEFAULT_MAX_DURATION_SECONDS = 24L * 60 * 60;
    private static final int DEFAULT_MAX_POLL_FAILURES = 30;

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    /**
     * Delivery channel for answering a {@code PULL_WORKFLOW_MESSAGES} wait. Optional because the
     * workflow message queue is a conditionally-enabled feature; when absent, resuming a
     * message-pull wait fails terminally with a pointer to the queue API.
     */
    private final Optional<WorkflowMessageQueueDAO> workflowMessageQueueDAO;

    public ConductorAgentDelegate() {
        this(Optional.empty());
    }

    public ConductorAgentDelegate(Optional<WorkflowMessageQueueDAO> workflowMessageQueueDAO) {
        this.workflowMessageQueueDAO = workflowMessageQueueDAO;
    }

    /** Handles {@code start}: fresh-start or resume, then routes the resulting snapshot. */
    public void start(TaskModel task, WorkflowExecutor executor) {
        if (executor == null) {
            fail(task, "Conductor agent execution requires a WorkflowExecutor", true);
            return;
        }
        // Guarded parse: ConductorAgentRequest has fields A2ACallRequest does not, so input that
        // passed AgentTask's dispatch parse can still fail here — a permanent error either way.
        ConductorAgentRequest request;
        try {
            request = parseRequest(task);
        } catch (Exception e) {
            fail(task, "Malformed AGENT input: " + e.getMessage(), true);
            return;
        }

        // Record the start time once so execute() can enforce the absolute deadline across
        // restarts and retries (survives in the persisted task output).
        if (task.getOutputData().get(ConductorAgentResults.KEY_STARTED_AT) == null) {
            task.addOutput(ConductorAgentResults.KEY_STARTED_AT, System.currentTimeMillis());
        }

        String executionId = StringUtils.trimToNull(request.getExecutionId());
        try {
            ConductorAgentExecution execution;
            if (executionId != null) {
                // Resume path: feed the caller's message back into a waiting execution as the
                // pending tool/human result, then re-read the resulting snapshot. A blank prompt
                // is a permanent bad input — fail terminally rather than letting Map.of() NPE and
                // get misclassified as a retryable failure.
                if (StringUtils.isBlank(request.getPrompt())) {
                    fail(task, "AGENT (conductor) requires 'prompt'", true);
                    return;
                }
                respond(executor, executionId, Map.of("result", request.getPrompt()));
                execution = getStatus(executor, executionId);
            } else {
                // Fresh start: name and a prompt are both required.
                if (StringUtils.isBlank(request.getName())) {
                    fail(task, "AGENT (conductor) requires 'name'", true);
                    return;
                }
                if (StringUtils.isBlank(request.getPrompt())) {
                    fail(task, "AGENT (conductor) requires 'prompt'", true);
                    return;
                }
                // request IS an AgentStartRequest — forward it as-is (agentConfig, media,
                // framework, rawConfig, skillRef, timeoutSeconds, staticPlan, ... all carry
                // through); only the idempotency key may need the deterministic fallback.
                request.setIdempotencyKey(
                        StringUtils.firstNonBlank(
                                request.getIdempotencyKey(), idempotencyKey(task)));
                AgentStartResponse response = executor.startAgentExecution(request);
                execution =
                        ConductorAgentExecution.builder()
                                .executionId(response.getExecutionId())
                                .agentName(response.getAgentName())
                                .state(ConductorAgentState.RUNNING)
                                .build();
            }
            applyExecution(task, execution);
        } catch (NonRetryableException
                | NotFoundException
                | IllegalStateException
                | IllegalArgumentException e) {
            // Permanent errors: unknown agent (NotFoundException from startAgentExecution),
            // invalid request (IllegalArgumentException), or nothing pending to resume
            // (IllegalStateException). Retrying cannot fix any of these — fail terminally.
            fail(task, e.getMessage(), true);
        } catch (Exception e) {
            // Executor call failures are transient — let the task retry with the same
            // deterministic idempotency key so a re-issued start is deduplicated.
            fail(task, "Conductor agent call failed: " + e.getMessage(), false);
        }
    }

    /** Handles {@code execute}: polls the run and applies the snapshot. */
    public boolean execute(TaskModel task, WorkflowExecutor executor) {
        if (executor == null) {
            fail(task, "Conductor agent execution requires a WorkflowExecutor", true);
            return true;
        }
        ConductorAgentRequest request;
        try {
            request = parseRequest(task);
        } catch (Exception e) {
            fail(task, "Malformed AGENT input: " + e.getMessage(), true);
            return true;
        }
        String executionId =
                asString(task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        if (StringUtils.isBlank(executionId)) {
            fail(task, "No conductor agent execution to poll", true);
            return true;
        }
        // Liveness guard 1: absolute deadline.
        if (deadlineExceeded(task, request)) {
            terminateChildBestEffort(executor, executionId, "AGENT exceeded max duration");
            fail(
                    task,
                    "AGENT exceeded max duration of "
                            + maxDurationSeconds(request)
                            + "s without the agent reaching a terminal state",
                    true);
            return true;
        }
        try {
            ConductorAgentExecution execution = getStatus(executor, executionId);
            task.addOutput(ConductorAgentResults.KEY_POLL_FAILURES, 0); // reset on success
            applyExecution(task, execution);
            // Still working -> stay IN_PROGRESS so the engine re-polls.
            return task.getStatus() != TaskModel.Status.IN_PROGRESS;
        } catch (NonRetryableException
                | NotFoundException
                | IllegalStateException
                | IllegalArgumentException e) {
            // Permanent: the execution no longer exists (purged/wrong id) or the request is
            // invalid — burning the poll-failure budget on retries cannot fix it.
            fail(task, e.getMessage(), true);
            return true;
        } catch (Exception e) {
            // Liveness guard 2: bound consecutive transient failures so an unavailable executor
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
                terminateChildBestEffort(executor, executionId, "Conductor agent unreachable");
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

    /**
     * Handles {@code cancel}: best-effort termination of the child execution. Preserves an
     * already-terminal task status (e.g. {@code TIMED_OUT} set by the decider) — this is a cleanup
     * hook, not a status transition; only a still-running task becomes {@code CANCELED}.
     */
    public void cancel(TaskModel task, WorkflowExecutor executor, String reason) {
        String executionId =
                asString(task.getOutputData().get(ConductorAgentResults.KEY_EXECUTION_ID));
        terminateChildBestEffort(
                executor, executionId, reason != null ? reason : "Cancelled by user");
        if (task.getStatus() == null || !task.getStatus().isTerminal()) {
            task.setStatus(TaskModel.Status.CANCELED);
        }
    }

    /**
     * Best-effort propagation of a cancel/liveness-guard failure to the child conductor agent
     * execution, so an abandoned Conductor task doesn't leave the agent's own workflow running
     * indefinitely. Never throws — a failure here is logged and otherwise ignored, since the caller
     * proceeds to fail/cancel the Conductor task regardless.
     */
    private void terminateChildBestEffort(
            WorkflowExecutor executor, String executionId, String reason) {
        if (executor == null || StringUtils.isBlank(executionId)) {
            return;
        }
        // Cancel is a cleanup hook and may fire after the child already finished on its own
        // (e.g. the child hit its own workflow timeout -> TIMED_OUT). Re-terminating would
        // rewrite that terminal status to TERMINATED; leave finished executions untouched.
        // The probe is itself best-effort: an unreachable child still gets the terminate call.
        try {
            WorkflowModel child = executor.getWorkflow(executionId, false);
            if (child != null && child.getStatus() != null && child.getStatus().isTerminal()) {
                return;
            }
        } catch (Exception ignored) {
            // fall through to the terminate attempt
        }
        try {
            executor.terminateWorkflow(executionId, reason);
        } catch (Exception e) {
            log.warn(
                    "Failed to propagate {} to conductor agent execution {}: {}",
                    reason,
                    executionId,
                    e.getMessage());
        }
    }

    /** Reads a workflow execution and maps it onto the state exposed by the AGENT task. */
    private ConductorAgentExecution getStatus(WorkflowExecutor executor, String executionId) {
        WorkflowModel workflow = executor.getWorkflow(executionId, true);
        TaskModel waitingTask = findWaitingTask(workflow);
        ConductorAgentState state = deriveState(workflow.getStatus(), waitingTask != null);
        Map<String, Object> output =
                workflow.getStatus().isTerminal() ? workflow.getOutput() : null;

        return ConductorAgentExecution.builder()
                .executionId(workflow.getWorkflowId())
                .agentName(workflow.getWorkflowName())
                .state(state)
                .output(output)
                .text(output != null ? extractText(output) : null)
                .pendingTool(waitingTask != null ? pendingTool(waitingTask) : null)
                .reasonForIncompletion(workflow.getReasonForIncompletion())
                .build();
    }

    /**
     * Final text of a completed run. The agent compiler emits the canonical {@code result} output
     * key (the LLM's final answer — a String for textual runs, a Map for schema-typed ones); an
     * explicit {@code text} key wins if a definition ever provides one. Structured results stay in
     * {@code output} only.
     */
    private static String extractText(Map<String, Object> output) {
        Object text = output.get("text");
        if (text instanceof String s && !s.isBlank()) {
            return s;
        }
        Object result = output.get("result");
        return result instanceof String s ? s : null;
    }

    private static ConductorAgentState deriveState(
            WorkflowModel.Status status, boolean waitingForInput) {
        if (waitingForInput) {
            return ConductorAgentState.WAITING;
        }
        return switch (status) {
            case COMPLETED -> ConductorAgentState.COMPLETED;
            case FAILED, TIMED_OUT -> ConductorAgentState.FAILED;
            case TERMINATED -> ConductorAgentState.CANCELED;
            case RUNNING, PAUSED -> ConductorAgentState.RUNNING;
        };
    }

    private static TaskModel findWaitingTask(WorkflowModel workflow) {
        if (workflow.getTasks() == null) {
            return null;
        }
        for (TaskModel candidate : workflow.getTasks()) {
            if (("HUMAN".equals(candidate.getTaskType())
                            || "PULL_WORKFLOW_MESSAGES".equals(candidate.getTaskType()))
                    && candidate.getStatus() == TaskModel.Status.IN_PROGRESS) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, Object> pendingTool(TaskModel waitingTask) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("taskRefName", waitingTask.getReferenceTaskName());
        Map<String, Object> input = waitingTask.getInputData();
        if (input != null) {
            pending.put("tool_name", input.get("tool_name"));
            pending.put("parameters", input.get("parameters"));
            List<Map<String, Object>> toolCalls = extractToolCalls(input.get("tool_calls"));
            if (toolCalls != null) {
                pending.put("toolCalls", toolCalls);
            }
            if (input.get("response_schema") != null) {
                pending.put("response_schema", input.get("response_schema"));
            }
            if (input.get("response_ui_schema") != null) {
                pending.put("response_ui_schema", input.get("response_ui_schema"));
            }
        }
        return pending;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractToolCalls(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> calls = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> call = (Map<String, Object>) map;
            Object name = call.get("name");
            Object args =
                    call.containsKey("inputParameters")
                            ? call.get("inputParameters")
                            : call.get("input");
            Map<String, Object> normalized = new LinkedHashMap<>();
            if (name != null) {
                normalized.put("name", name);
            }
            if (args != null) {
                normalized.put("args", args);
            }
            if (!normalized.isEmpty()) {
                calls.add(normalized);
            }
        }
        return calls.isEmpty() ? null : calls;
    }

    /**
     * Answers the execution's pending wait through the channel that wait actually consumes: a HUMAN
     * task is completed directly ({@code updateTask}), while a PULL_WORKFLOW_MESSAGES task pops its
     * input from the workflow message queue — so the response is pushed there and the workflow is
     * re-decided to wake the waiting task. This mirrors {@code getStatus()}, which reports {@code
     * WAITING} for both task types.
     */
    private void respond(
            WorkflowExecutor executor, String executionId, Map<String, Object> output) {
        WorkflowModel workflow = executor.getWorkflow(executionId, true);
        TaskModel pendingTask = null;
        for (TaskModel candidate : workflow.getTasks()) {
            if (("HUMAN".equals(candidate.getTaskType())
                            || "PULL_WORKFLOW_MESSAGES".equals(candidate.getTaskType()))
                    && candidate.getStatus() == TaskModel.Status.IN_PROGRESS) {
                pendingTask = candidate;
                break;
            }
        }
        if (pendingTask == null) {
            throw new IllegalStateException(
                    "No pending HUMAN or PULL_WORKFLOW_MESSAGES task found in execution "
                            + executionId);
        }

        if ("PULL_WORKFLOW_MESSAGES".equals(pendingTask.getTaskType())) {
            deliverWorkflowMessage(executor, executionId, output);
            return;
        }

        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(pendingTask.getTaskId());
        taskResult.setWorkflowInstanceId(executionId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        Map<String, Object> outputData = new LinkedHashMap<>();
        if (pendingTask.getOutputData() != null) {
            outputData.putAll(pendingTask.getOutputData());
        }
        outputData.putAll(output);
        taskResult.setOutputData(outputData);
        executor.updateTask(taskResult);
    }

    /**
     * Delivers the response to a PULL_WORKFLOW_MESSAGES wait via the workflow message queue
     * (mirroring {@code WorkflowMessageQueueResource}), then re-decides the workflow so the waiting
     * task pops the message immediately instead of on its next poll.
     */
    private void deliverWorkflowMessage(
            WorkflowExecutor executor, String executionId, Map<String, Object> payload) {
        WorkflowMessageQueueDAO queue =
                workflowMessageQueueDAO.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Execution "
                                                + executionId
                                                + " is waiting on PULL_WORKFLOW_MESSAGES, but the"
                                                + " workflow message queue is not enabled — deliver"
                                                + " the message via POST"
                                                + " /api/workflow/{workflowId}/messages on a"
                                                + " deployment with"
                                                + " conductor.workflow-message-queue.enabled=true"));
        queue.push(
                executionId,
                new WorkflowMessage(
                        UUID.randomUUID().toString(),
                        executionId,
                        payload,
                        java.time.Instant.now().toString()));
        executor.decide(executionId);
    }

    /** Routes an execution snapshot onto this Conductor task's status/output. */
    private void applyExecution(TaskModel task, ConductorAgentExecution execution) {
        task.addOutput(ConductorAgentResults.KEY_EXECUTION_ID, execution.getExecutionId());
        task.addOutput(ConductorAgentResults.KEY_AGENT_NAME, execution.getAgentName());
        // A conductor agent execution is an ordinary child workflow; expose it the same way
        // SUB_WORKFLOW does so the UI's existing "open sub-workflow" affordance also opens the
        // agent's execution.
        task.setSubWorkflowId(execution.getExecutionId());
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

    private boolean deadlineExceeded(TaskModel task, ConductorAgentRequest request) {
        long startedAt =
                asLong(
                        task.getOutputData().get(ConductorAgentResults.KEY_STARTED_AT),
                        System.currentTimeMillis());
        return System.currentTimeMillis() - startedAt > maxDurationSeconds(request) * 1000L;
    }

    private long maxDurationSeconds(ConductorAgentRequest request) {
        return request.getMaxDurationSeconds() != null
                ? Math.max(1, request.getMaxDurationSeconds())
                : DEFAULT_MAX_DURATION_SECONDS;
    }

    private int maxPollFailures(ConductorAgentRequest request) {
        return request.getMaxPollFailures() != null
                ? Math.max(1, request.getMaxPollFailures())
                : DEFAULT_MAX_POLL_FAILURES;
    }

    private ConductorAgentRequest parseRequest(TaskModel task) {
        return objectMapper.convertValue(task.getInputData(), ConductorAgentRequest.class);
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
}
