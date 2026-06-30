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
package org.conductoross.conductor.ai.a2a;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AService.SendResult;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.PushNotificationConfig;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.model.A2ACallRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * System task that sends a message to a remote A2A agent ({@code message/send}) and works with the
 * resulting agent task.
 *
 * <p>Non-blocking by design. A fast reply (a direct message or an already-terminal task) completes
 * in {@link #start}. For long-running work there are three modes:
 *
 * <ul>
 *   <li><b>poll</b> (default): the task moves to {@code IN_PROGRESS} and is polled via {@code
 *       tasks/get} in {@link #execute} at the {@link #getEvaluationOffset} cadence — no worker
 *       thread is held.
 *   <li><b>push</b> ({@code pushNotification=true} + {@code conductor.a2a.callback.url} set): the
 *       task stays {@code IN_PROGRESS} and is completed when the agent's webhook hits {@code
 *       A2ACallbackResource}; a slow backstop poll (see {@link #getEvaluationOffset}) still
 *       finishes it if the webhook is never delivered.
 *   <li><b>streaming</b> ({@code streaming=true}): consumes {@code message/stream} (SSE) and
 *       aggregates events to completion (holds a thread for the stream's duration).
 * </ul>
 *
 * <p>When the remote task reaches {@code input-required}/{@code auth-required}, this task COMPLETES
 * and surfaces the agent's question plus the {@code taskId}/{@code contextId}, so the workflow can
 * resume by issuing another {@code AGENT} call with the same ids.
 */
@Slf4j
@Component(AgentTask.TASK_TYPE)
@Conditional(AIIntegrationEnabledCondition.class)
public class AgentTask extends WorkflowSystemTask {

    public static final String TASK_TYPE = "AGENT";

    /** Externally-reachable base URL the agent uses for push callbacks. */
    public static final String CALLBACK_URL_PROPERTY = "conductor.a2a.callback.url";

    private static final long DEFAULT_POLL_SECONDS = 5;
    private static final long DEFAULT_PUSH_BACKSTOP_SECONDS = 300;
    private static final long DEFAULT_MAX_DURATION_SECONDS = 24L * 60 * 60;
    private static final int DEFAULT_MAX_POLL_FAILURES = 30;
    private static final String PUSH_INPUT = "pushNotification";

    /** Push tokens expire after 24 h. Tasks running longer must use polling instead of push. */
    static final long PUSH_TOKEN_TTL_MS = 24L * 60 * 60 * 1000;

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final A2AService a2aService;
    private final Environment environment;

    public AgentTask(A2AService a2aService, Environment environment) {
        super(TASK_TYPE);
        this.a2aService = a2aService;
        this.environment = environment;
        log.info("{} initialized", TASK_TYPE);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        try (A2ALogging.Scope scope =
                A2ALogging.of(
                        A2ALogging.WORKFLOW_ID, task.getWorkflowInstanceId(),
                        A2ALogging.TASK_ID, task.getTaskId(),
                        A2ALogging.REF, task.getReferenceTaskName())) {
            A2ACallRequest request = parseRequest(task);
            if (!A2AService.isA2aAgentType(request.getAgentType())) {
                fail(
                        task,
                        "Unsupported agentType '"
                                + request.getAgentType()
                                + "' (only 'a2a' is supported)",
                        true);
                return;
            }
            if (StringUtils.isBlank(request.getAgentUrl())) {
                fail(task, "AGENT requires 'agentUrl'", true);
                return;
            }
            // Record the start time once, so execute() can enforce the absolute deadline across
            // restarts and retries (survives in the persisted task output).
            if (task.getOutputData().get(A2AResults.KEY_STARTED_AT) == null) {
                task.addOutput(A2AResults.KEY_STARTED_AT, System.currentTimeMillis());
            }
            try {
                A2AMessage message = buildMessage(request, task);
                SendResult result;
                if (request.isStreaming()) {
                    result =
                            a2aService.streamMessage(
                                    request.getAgentUrl(),
                                    message,
                                    buildConfiguration(request, task, false),
                                    request.getHeaders());
                    // A dropped/empty stream must not be reported as a successful completion —
                    // treat it as transient so the task retries (with the same messageId).
                    if (!result.isTask() && isEmptyMessage(result.getMessage())) {
                        throw new A2AException(
                                "Streaming produced no events (stream may have dropped); will"
                                        + " retry");
                    }
                } else {
                    boolean usePush = usePush(request);
                    result =
                            a2aService.sendMessage(
                                    request.getAgentUrl(),
                                    message,
                                    buildConfiguration(request, task, usePush),
                                    request.getHeaders());
                }
                applyResult(task, result);
            } catch (NonRetryableException e) {
                fail(task, e.getMessage(), true);
            } catch (Exception e) {
                fail(task, "Failed to call agent: " + e.getMessage(), false);
            }
        } finally {
            recordOutcome(task);
        }
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        String agentTaskId = asString(task.getOutputData().get(A2AResults.KEY_TASK_ID));
        try (A2ALogging.Scope scope =
                A2ALogging.of(
                        A2ALogging.WORKFLOW_ID, task.getWorkflowInstanceId(),
                        A2ALogging.TASK_ID, task.getTaskId(),
                        A2ALogging.REF, task.getReferenceTaskName())) {
            scope.add(A2ALogging.REMOTE_TASK_ID, agentTaskId);
            A2ACallRequest request = parseRequest(task);
            if (StringUtils.isBlank(agentTaskId) || StringUtils.isBlank(request.getAgentUrl())) {
                fail(task, "No remote A2A task to poll", true);
                return true;
            }
            // Liveness guard 1: absolute deadline. Without this a task could poll (or, in push
            // mode, backstop-poll) forever against an agent that never reaches a terminal state.
            if (deadlineExceeded(task, request)) {
                fail(
                        task,
                        "AGENT exceeded max duration of "
                                + maxDurationSeconds(request)
                                + "s without the remote agent reaching a terminal state",
                        true);
                return true;
            }
            try {
                A2ATask agentTask =
                        a2aService.getTask(
                                request.getAgentUrl(),
                                agentTaskId,
                                request.getHistoryLength(),
                                request.getHeaders());
                task.addOutput(A2AResults.KEY_POLL_FAILURES, 0); // reset on a successful poll
                handleTaskState(task, agentTask);
                // Still working -> stay IN_PROGRESS so the engine re-polls.
                return task.getStatus() != TaskModel.Status.IN_PROGRESS;
            } catch (NonRetryableException e) {
                fail(task, e.getMessage(), true);
                return true;
            } catch (Exception e) {
                // Liveness guard 2: bound consecutive transient failures so a dead agent doesn't
                // keep us polling indefinitely (until the deadline).
                A2AMetrics.clientPollFailure();
                int failures =
                        (int) asLong(task.getOutputData().get(A2AResults.KEY_POLL_FAILURES), 0) + 1;
                task.addOutput(A2AResults.KEY_POLL_FAILURES, failures);
                int max = maxPollFailures(request);
                if (failures >= max) {
                    fail(
                            task,
                            "A2A agent unreachable after "
                                    + failures
                                    + " consecutive poll failures: "
                                    + e.getMessage(),
                            true);
                    return true;
                }
                log.warn(
                        "Transient error polling A2A task {} on {} ({}/{}): {}",
                        agentTaskId,
                        request.getAgentUrl(),
                        failures,
                        max,
                        e.getMessage());
                return false;
            }
        } finally {
            recordOutcome(task);
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        String agentTaskId = asString(task.getOutputData().get(A2AResults.KEY_TASK_ID));
        try {
            A2ACallRequest request = parseRequest(task);
            if (!StringUtils.isBlank(request.getAgentUrl()) && !StringUtils.isBlank(agentTaskId)) {
                a2aService.cancelTask(request.getAgentUrl(), agentTaskId, request.getHeaders());
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to propagate cancel to remote A2A task {}: {}",
                    agentTaskId,
                    e.getMessage());
        }
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel task, long maxOffset) {
        // In push mode the agent's webhook completes the task quickly; we still poll at a slow
        // backstop interval so a lost/never-delivered webhook can't hang the task forever
        // (durability over a marginal efficiency gain). Otherwise poll at the normal cadence.
        if (pushEnabled(task)) {
            return Optional.of(pushBackstopSeconds(task));
        }
        return Optional.of(pollInterval(task));
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    private void applyResult(TaskModel task, SendResult result) {
        if (result.isTask()) {
            handleTaskState(task, result.getTask());
        } else {
            task.addOutput(A2AResults.messageOutput(result.getMessage(), objectMapper));
            task.setStatus(TaskModel.Status.COMPLETED);
        }
    }

    /** Routes a remote task's current state onto this Conductor task's status/output. */
    private void handleTaskState(TaskModel task, A2ATask agentTask) {
        String state = A2AResults.stateOf(agentTask);
        if (TaskState.isTerminal(state)) {
            applyTaskResult(task, agentTask);
        } else if (TaskState.isInterrupted(state)) {
            // input-required / auth-required: complete and surface the question for resumption.
            task.addOutput(A2AResults.taskOutput(agentTask, objectMapper));
            task.setStatus(TaskModel.Status.COMPLETED);
        } else {
            // submitted / working: record ids and keep polling.
            task.addOutput(A2AResults.KEY_STATE, TaskState.normalize(state));
            task.addOutput(A2AResults.KEY_TASK_ID, agentTask.getId());
            task.addOutput(A2AResults.KEY_CONTEXT_ID, agentTask.getContextId());
            task.setStatus(TaskModel.Status.IN_PROGRESS);
        }
    }

    private void applyTaskResult(TaskModel task, A2ATask agentTask) {
        String state = TaskState.normalize(A2AResults.stateOf(agentTask));
        task.addOutput(A2AResults.taskOutput(agentTask, objectMapper));
        switch (state) {
            case TaskState.COMPLETED:
                task.setStatus(TaskModel.Status.COMPLETED);
                break;
            case TaskState.CANCELED:
                task.setStatus(TaskModel.Status.CANCELED);
                break;
            default: // failed / rejected
                task.setStatus(TaskModel.Status.FAILED);
                task.setReasonForIncompletion(
                        "Remote agent task ended in state '"
                                + state
                                + "'"
                                + suffix(A2AResults.extractText(agentTask)));
                break;
        }
    }

    private A2AMessage buildMessage(A2ACallRequest request, TaskModel task) {
        A2AMessage message;
        if (request.getMessage() != null && !request.getMessage().isEmpty()) {
            message = objectMapper.convertValue(request.getMessage(), A2AMessage.class);
        } else {
            message = new A2AMessage();
            List<Part> parts = new ArrayList<>();
            if (request.getParts() != null && !request.getParts().isEmpty()) {
                for (Map<String, Object> raw : request.getParts()) {
                    parts.add(objectMapper.convertValue(raw, Part.class));
                }
            } else {
                String text = StringUtils.firstNonBlank(request.getText(), request.getPrompt());
                if (StringUtils.isBlank(text)) {
                    throw new NonRetryableException(
                            "AGENT requires one of: message, parts, text, or prompt");
                }
                Part part = new Part();
                part.setKind("text");
                part.setText(text);
                parts.add(part);
            }
            message.setParts(parts);
        }
        if (message.getRole() == null) {
            message.setRole("user");
        }
        if (message.getMessageId() == null) {
            // Deterministic idempotency key: stable across retries and server restarts of the
            // same logical call (Conductor reuses referenceTaskName + iteration across retries
            // even though it mints a new taskId), but distinct per DO_WHILE iteration. Agents
            // that dedupe on messageId thus get effectively-once delivery despite at-least-once.
            message.setMessageId(deterministicMessageId(task));
        }
        if (message.getKind() == null) {
            message.setKind("message");
        }
        if (message.getContextId() == null && !StringUtils.isBlank(request.getContextId())) {
            message.setContextId(request.getContextId());
        }
        if (message.getTaskId() == null && !StringUtils.isBlank(request.getTaskId())) {
            message.setTaskId(request.getTaskId());
        }
        if (message.getMetadata() == null && request.getMetadata() != null) {
            message.setMetadata(request.getMetadata());
        }
        return message;
    }

    private Map<String, Object> buildConfiguration(
            A2ACallRequest request, TaskModel task, boolean usePush) {
        Map<String, Object> configuration = new HashMap<>();
        if (request.getHistoryLength() != null) {
            configuration.put("historyLength", request.getHistoryLength());
        }
        if (usePush) {
            // Embed an expiry timestamp in the token so the callback resource can enforce a TTL
            // without any additional persistent state. Format: "{uuid}:{expiryEpochMillis}".
            long expiryMs = System.currentTimeMillis() + PUSH_TOKEN_TTL_MS;
            String token = UUID.randomUUID() + ":" + expiryMs;
            task.addOutput(A2AResults.KEY_PUSH_TOKEN, token);
            String base =
                    StringUtils.removeEnd(environment.getProperty(CALLBACK_URL_PROPERTY), "/");
            PushNotificationConfig pushConfig =
                    new PushNotificationConfig(
                            base + "/api/a2a/callback/" + task.getTaskId(), token);
            // Tell the agent to send the token as a Bearer credential when calling our webhook.
            // Agents that support the authentication field will use it; others fall back to the
            // token query-param path which our callback still accepts with a deprecation warning.
            pushConfig.setAuthentication(
                    Map.of("schemes", List.of("Bearer"), "credentials", token));
            configuration.put(
                    "pushNotificationConfig", objectMapper.convertValue(pushConfig, Map.class));
        }
        return configuration.isEmpty() ? null : configuration;
    }

    private boolean usePush(A2ACallRequest request) {
        if (!request.isPushNotification()) {
            return false;
        }
        if (StringUtils.isBlank(environment.getProperty(CALLBACK_URL_PROPERTY))) {
            log.warn(
                    "pushNotification requested but '{}' is not configured; falling back to polling",
                    CALLBACK_URL_PROPERTY);
            return false;
        }
        return true;
    }

    private long pollInterval(TaskModel task) {
        Integer value = parseRequest(task).getPollIntervalSeconds();
        return value != null ? Math.max(1, value) : DEFAULT_POLL_SECONDS;
    }

    /**
     * Deterministic, restart-stable idempotency key. Built from identity that Conductor preserves
     * across task retries ({@code workflowInstanceId} + {@code referenceTaskName} + {@code
     * iteration}) — NOT {@code taskId}, which changes per retry attempt.
     */
    private String deterministicMessageId(TaskModel task) {
        return "a2a-"
                + task.getWorkflowInstanceId()
                + ":"
                + task.getReferenceTaskName()
                + ":"
                + task.getIteration();
    }

    private boolean deadlineExceeded(TaskModel task, A2ACallRequest request) {
        long startedAt =
                asLong(
                        task.getOutputData().get(A2AResults.KEY_STARTED_AT),
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

    private boolean pushEnabled(TaskModel task) {
        return toBool(task.getInputData().get(PUSH_INPUT))
                && !StringUtils.isBlank(environment.getProperty(CALLBACK_URL_PROPERTY));
    }

    private long pushBackstopSeconds(TaskModel task) {
        Integer value = parseRequest(task).getPushBackstopPollSeconds();
        return value != null ? Math.max(1, value) : DEFAULT_PUSH_BACKSTOP_SECONDS;
    }

    private boolean isEmptyMessage(A2AMessage message) {
        return message == null || message.getParts() == null || message.getParts().isEmpty();
    }

    /**
     * Reads a numeric value this task previously wrote to its own output. Persistence round-trips
     * JSON numbers back as a {@link Number} (Integer/Long), so only that case can occur.
     */
    private static long asLong(Object value, long defaultValue) {
        return value instanceof Number ? ((Number) value).longValue() : defaultValue;
    }

    private A2ACallRequest parseRequest(TaskModel task) {
        return objectMapper.convertValue(task.getInputData(), A2ACallRequest.class);
    }

    private void fail(TaskModel task, String reason, boolean nonRetryable) {
        task.setStatus(
                nonRetryable
                        ? TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
                        : TaskModel.Status.FAILED);
        task.setReasonForIncompletion(reason);
    }

    /**
     * Emit a single {@code a2a_client_calls} metric once the task has settled into a terminal
     * status. Called from the {@code finally} of start()/execute(); a no-op while the task is still
     * IN_PROGRESS, so a polled call counts exactly once (on the cycle that resolves it).
     */
    private void recordOutcome(TaskModel task) {
        TaskModel.Status status = task.getStatus();
        if (status != null && status.isTerminal()) {
            A2AMetrics.clientCall(status.name().toLowerCase());
        }
    }

    private static String suffix(String text) {
        return text == null ? "" : ": " + text;
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
