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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AException;
import org.conductoross.conductor.ai.a2a.A2ALogging;
import org.conductoross.conductor.ai.a2a.A2AMetrics;
import org.conductoross.conductor.ai.a2a.A2AResults;
import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.A2AService.SendResult;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.PushNotificationConfig;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentDelegate;
import org.conductoross.conductor.ai.model.A2AAgentCardRequest;
import org.conductoross.conductor.ai.model.A2AAgentCardResult;
import org.conductoross.conductor.ai.model.A2ACallRequest;
import org.conductoross.conductor.ai.model.A2ACallResult;
import org.conductoross.conductor.ai.model.A2ACancelRequest;
import org.conductoross.conductor.ai.model.A2ACancelResult;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.conductoross.conductor.core.execution.tasks.TaskCancellationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Portable workers for the A2A and Conductor-agent task types.
 *
 * <p>Spring registers this class as embedded annotated system tasks. External runtimes construct
 * the same class and register it with the Java SDK annotated worker executor.
 */
@Slf4j
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class A2AWorkers implements AnnotatedSystemTaskWorker, TaskCancellationHandler {

    public static final String GET_AGENT_CARD = "GET_AGENT_CARD";
    public static final String AGENT = "AGENT";
    public static final String CANCEL_AGENT = "CANCEL_AGENT";
    public static final String CALLBACK_URL_PROPERTY = "conductor.a2a.callback.url";

    private static final long DEFAULT_POLL_SECONDS = 5;
    private static final long DEFAULT_PUSH_BACKSTOP_SECONDS = 300;
    private static final long DEFAULT_MAX_DURATION_SECONDS = 24L * 60 * 60;
    private static final int DEFAULT_MAX_POLL_FAILURES = 30;
    private static final long PUSH_TOKEN_TTL_MS = 24L * 60 * 60 * 1000;

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final A2AService a2aService;
    private final Map<String, ConductorAgentClient> agentClients;
    private final String callbackUrl;

    /**
     * Spring constructor — receives all registered {@link ConductorAgentClient} implementations.
     * Each client self-declares its {@code agentType()}; the map is built once at startup so
     * routing in {@link #agent} is a plain map lookup with no conditional logic.
     */
    @Autowired
    public A2AWorkers(
            A2AService a2aService,
            @Lazy List<ConductorAgentClient> conductorAgentClients,
            Environment environment) {
        this(a2aService, conductorAgentClients, environment.getProperty(CALLBACK_URL_PROPERTY));
    }

    public A2AWorkers(
            A2AService a2aService,
            List<ConductorAgentClient> conductorAgentClients,
            String callbackUrl) {
        this.a2aService = a2aService;
        this.agentClients = new HashMap<>();
        conductorAgentClients.forEach(c -> agentClients.put(c.agentType().toLowerCase(), c));
        this.callbackUrl = StringUtils.trimToNull(callbackUrl);
    }

    public A2AWorkers(A2AService a2aService, List<ConductorAgentClient> conductorAgentClients) {
        this(a2aService, conductorAgentClients, (String) null);
    }

    /** Fetch a remote agent's Agent Card. */
    @WorkerTask(GET_AGENT_CARD)
    public A2AAgentCardResult getAgentCard(A2AAgentCardRequest request) {
        requireA2a(request.getAgentType());
        if (StringUtils.isBlank(request.getAgentUrl())) {
            throw new NonRetryableException("GET_AGENT_CARD requires 'agentUrl'");
        }
        AgentCard agentCard = a2aService.getAgentCard(request.getAgentUrl(), request.getHeaders());
        return new A2AAgentCardResult(agentCard);
    }

    /**
     * Start or advance an agent call.
     *
     * <p>The output is returned as a plain POJO. Task lifecycle state is applied through {@link
     * TaskContext}; {@code IN_PROGRESS} with a callback delay requeues the task without holding a
     * worker thread. Streaming is the one exception and enables lease extension because it blocks
     * while consuming the remote SSE response.
     */
    @WorkerTask(value = AGENT, leaseExtendEnabled = true)
    public A2ACallResult agent(A2ACallRequest request) {
        Task task = TaskContext.get().getTask();
        TaskResult result;
        ConductorAgentClient client = agentClients.get(
                StringUtils.defaultIfBlank(request.getAgentType(), "").toLowerCase());
        if (client != null) {
            result = new ConductorAgentDelegate(client).execute(task);
        } else {
            result = executeRemote(task, request);
        }
        return finish(result, A2ACallResult.class);
    }

    /** Explicitly cancel either a remote A2A task or a Conductor agent execution. */
    @WorkerTask(CANCEL_AGENT)
    public A2ACancelResult cancelAgent(A2ACancelRequest request) {
        Task task = TaskContext.get().getTask();
        TaskResult result = resultFor(task);
        ConductorAgentClient cancelClient = agentClients.get(
                StringUtils.defaultIfBlank(request.getAgentType(), "").toLowerCase());
        if (cancelClient != null) {
            String executionId = StringUtils.trimToNull(request.getExecutionId());
            if (executionId == null) {
                fail(result, "CANCEL_AGENT requires 'executionId'", true);
                return finish(result, A2ACancelResult.class);
            }
            try {
                cancelClient.cancelAgent(
                        ConductorAgentCancelRequest.builder()
                                .executionId(executionId)
                                .reason(
                                        StringUtils.firstNonBlank(
                                                request.getReason(),
                                                "Cancelled by CANCEL_AGENT task"))
                                .build());
                result.getOutputData().put("executionId", executionId);
                result.getOutputData().put("canceled", true);
                result.setStatus(TaskResult.Status.COMPLETED);
                return finish(result, A2ACancelResult.class);
            } catch (Exception e) {
                fail(
                        result,
                        "Failed to cancel agent execution "
                                + executionId
                                + ": "
                                + e.getMessage(),
                        false);
                return finish(result, A2ACancelResult.class);
            }
        }

        if (!A2AService.isA2aAgentType(request.getAgentType())) {
            fail(
                    result,
                    "Unsupported agentType '"
                            + request.getAgentType()
                            + "' (supported: 'a2a', 'conductor')",
                    true);
            return finish(result, A2ACancelResult.class);
        }
        if (StringUtils.isBlank(request.getAgentUrl())) {
            fail(result, "CANCEL_AGENT requires 'agentUrl'", true);
            return finish(result, A2ACancelResult.class);
        }
        if (StringUtils.isBlank(request.getTaskId())) {
            fail(result, "CANCEL_AGENT requires 'taskId'", true);
            return finish(result, A2ACancelResult.class);
        }
        try {
            String endpoint =
                    resolveRemoteEndpoint(task, request.getAgentUrl(), request.getHeaders());
            A2ATask canceled =
                    a2aService.cancelTask(endpoint, request.getTaskId(), request.getHeaders());
            result.getOutputData().put("task", objectMapper.convertValue(canceled, Map.class));
            result.setStatus(TaskResult.Status.COMPLETED);
            return finish(result, A2ACancelResult.class);
        } catch (Exception e) {
            fail(result, "Failed to cancel A2A task: " + e.getMessage(), false);
            return finish(result, A2ACancelResult.class);
        }
    }

    @Override
    public void cancel(Task task, String reason) {
        String taskType = task.getTaskType();
        if (!AGENT.equals(taskType)) {
            return;
        }
        A2ACallRequest request = parse(task, A2ACallRequest.class);
        ConductorAgentClient cancelClient = agentClients.get(
                StringUtils.defaultIfBlank(request.getAgentType(), "").toLowerCase());
        if (cancelClient != null) {
            new ConductorAgentDelegate(cancelClient).cancel(task, reason);
            return;
        }
        String remoteTaskId =
                asString(
                        task.getOutputData() != null
                                ? task.getOutputData().get(A2AResults.KEY_TASK_ID)
                                : null);
        try {
            request.setAgentUrl(
                    resolveRemoteEndpoint(task, request.getAgentUrl(), request.getHeaders()));
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve A2A endpoint while propagating {}: {}",
                    reason,
                    e.getMessage());
            return;
        }
        cancelRemoteBestEffort(request, remoteTaskId, reason);
    }

    private TaskResult executeRemote(Task task, A2ACallRequest request) {
        TaskResult result = resultFor(task);
        try (A2ALogging.Scope scope =
                A2ALogging.of(
                        A2ALogging.WORKFLOW_ID,
                        task.getWorkflowInstanceId(),
                        A2ALogging.TASK_ID,
                        task.getTaskId(),
                        A2ALogging.REF,
                        task.getReferenceTaskName())) {
            if (!A2AService.isA2aAgentType(request.getAgentType())) {
                return fail(
                        result,
                        "Unsupported agentType '"
                                + request.getAgentType()
                                + "' (supported: 'a2a', 'conductor')",
                        true);
            }
            if (StringUtils.isBlank(request.getAgentUrl())) {
                return fail(result, "AGENT requires 'agentUrl'", true);
            }
            try {
                request.setAgentUrl(
                        resolveRemoteEndpoint(task, request.getAgentUrl(), request.getHeaders()));
            } catch (NonRetryableException e) {
                return fail(result, e.getMessage(), true);
            } catch (Exception e) {
                return fail(result, "Failed to resolve A2A endpoint: " + e.getMessage(), false);
            }

            result.getOutputData()
                    .putIfAbsent(A2AResults.KEY_STARTED_AT, System.currentTimeMillis());
            String remoteTaskId = asString(result.getOutputData().get(A2AResults.KEY_TASK_ID));
            scope.add(A2ALogging.REMOTE_TASK_ID, remoteTaskId);

            if (StringUtils.isBlank(remoteTaskId)) {
                startRemote(task, result, request);
            } else {
                pollRemote(result, request, remoteTaskId);
            }
            return result;
        } finally {
            recordOutcome(result);
        }
    }

    /**
     * Resolves the JSON-RPC endpoint while keeping the configured discovery identity intact.
     *
     * <p>The workflow editor stores the fetched Agent Card in task metadata. Prefer its advertised
     * endpoint so a task configured with a website or well-known card URL does not POST JSON-RPC to
     * the discovery document. API-authored workflows without a snapshot still support direct Agent
     * Card URLs by discovering the endpoint at execution time. Direct endpoint URLs remain valid
     * and all returned values are trimmed before the SSRF-guarded A2A service uses them.
     */
    private String resolveRemoteEndpoint(
            Task task, String configuredUrl, Map<String, String> headers) {
        String configured = StringUtils.trimToNull(configuredUrl);
        if (configured == null) {
            throw new NonRetryableException("agentUrl must not be blank");
        }

        String snapshottedEndpoint = endpointFromSnapshot(task);
        if (snapshottedEndpoint != null) {
            return snapshottedEndpoint;
        }

        String pathWithoutQuery = StringUtils.substringBefore(configured, "?");
        if (StringUtils.endsWithIgnoreCase(pathWithoutQuery, ".json")) {
            AgentCard card = a2aService.getAgentCard(configured, headers);
            String discoveredEndpoint = card == null ? null : StringUtils.trimToNull(card.getUrl());
            if (discoveredEndpoint == null) {
                throw new NonRetryableException(
                        "Agent Card resolved from "
                                + configured
                                + " does not advertise a JSON-RPC 'url'");
            }
            return discoveredEndpoint;
        }
        return configured;
    }

    private String endpointFromSnapshot(Task task) {
        if (task == null
                || task.getWorkflowTask() == null
                || task.getWorkflowTask().getMetadata() == null) {
            return null;
        }
        JsonNode agent =
                objectMapper.valueToTree(task.getWorkflowTask().getMetadata()).path("agent");
        if (!A2AService.isA2aAgentType(agent.path("agentType").asText())) {
            return null;
        }
        return StringUtils.trimToNull(agent.path("a2a").path("agentCard").path("url").asText(null));
    }

    private void startRemote(Task task, TaskResult result, A2ACallRequest request) {
        try {
            A2AMessage message = buildMessage(request, task);
            SendResult sendResult;
            if (request.isStreaming()) {
                sendResult =
                        a2aService.streamMessage(
                                request.getAgentUrl(),
                                message,
                                buildConfiguration(request, task, result, false),
                                request.getHeaders(),
                                maxDurationSeconds(request));
                if (!sendResult.isTask() && isEmptyMessage(sendResult.getMessage())) {
                    throw new A2AException(
                            "Streaming produced no events (stream may have dropped); will retry");
                }
            } else {
                boolean usePush = usePush(request);
                sendResult =
                        a2aService.sendMessage(
                                request.getAgentUrl(),
                                message,
                                buildConfiguration(request, task, result, usePush),
                                request.getHeaders());
            }
            applySendResult(result, request, sendResult);
        } catch (NonRetryableException e) {
            fail(result, e.getMessage(), true);
        } catch (Exception e) {
            fail(result, "Failed to call agent: " + e.getMessage(), false);
        }
    }

    private void pollRemote(TaskResult result, A2ACallRequest request, String remoteTaskId) {
        if (deadlineExceeded(result, request)) {
            cancelRemoteBestEffort(request, remoteTaskId, "AGENT exceeded max duration");
            fail(
                    result,
                    "AGENT exceeded max duration of "
                            + maxDurationSeconds(request)
                            + "s without the remote agent reaching a terminal state",
                    true);
            return;
        }
        try {
            A2ATask agentTask =
                    a2aService.getTask(
                            request.getAgentUrl(),
                            remoteTaskId,
                            request.getHistoryLength(),
                            request.getHeaders());
            result.getOutputData().put(A2AResults.KEY_POLL_FAILURES, 0);
            handleTaskState(result, request, agentTask);
        } catch (NonRetryableException e) {
            fail(result, e.getMessage(), true);
        } catch (Exception e) {
            A2AMetrics.clientPollFailure();
            int failures =
                    (int) asLong(result.getOutputData().get(A2AResults.KEY_POLL_FAILURES), 0) + 1;
            result.getOutputData().put(A2AResults.KEY_POLL_FAILURES, failures);
            int maxFailures = maxPollFailures(request);
            if (failures >= maxFailures) {
                cancelRemoteBestEffort(request, remoteTaskId, "A2A agent unreachable");
                fail(
                        result,
                        "A2A agent unreachable after "
                                + failures
                                + " consecutive poll failures: "
                                + e.getMessage(),
                        true);
                return;
            }
            log.warn(
                    "Transient error polling A2A task {} on {} ({}/{}): {}",
                    remoteTaskId,
                    request.getAgentUrl(),
                    failures,
                    maxFailures,
                    e.getMessage());
            keepPolling(result, request);
        }
    }

    private void applySendResult(TaskResult result, A2ACallRequest request, SendResult sendResult) {
        if (sendResult.isTask()) {
            handleTaskState(result, request, sendResult.getTask());
        } else {
            result.getOutputData()
                    .putAll(A2AResults.messageOutput(sendResult.getMessage(), objectMapper));
            result.setStatus(TaskResult.Status.COMPLETED);
        }
    }

    private void handleTaskState(TaskResult result, A2ACallRequest request, A2ATask agentTask) {
        String state = A2AResults.stateOf(agentTask);
        if (TaskState.isTerminal(state)) {
            applyTaskResult(result, agentTask);
        } else if (TaskState.isInterrupted(state)) {
            result.getOutputData().putAll(A2AResults.taskOutput(agentTask, objectMapper));
            result.setStatus(TaskResult.Status.COMPLETED);
        } else {
            result.getOutputData().put(A2AResults.KEY_STATE, TaskState.normalize(state));
            result.getOutputData().put(A2AResults.KEY_TASK_ID, agentTask.getId());
            result.getOutputData().put(A2AResults.KEY_CONTEXT_ID, agentTask.getContextId());
            keepPolling(result, request);
        }
    }

    private void applyTaskResult(TaskResult result, A2ATask agentTask) {
        String state = TaskState.normalize(A2AResults.stateOf(agentTask));
        result.getOutputData().putAll(A2AResults.taskOutput(agentTask, objectMapper));
        switch (state) {
            case TaskState.COMPLETED:
                result.setStatus(TaskResult.Status.COMPLETED);
                break;
            case TaskState.CANCELED:
                result.setStatus(TaskResult.Status.CANCELED);
                result.setReasonForIncompletion(
                        "Remote agent task was canceled"
                                + suffix(A2AResults.extractText(agentTask)));
                break;
            default:
                result.setStatus(TaskResult.Status.FAILED);
                result.setReasonForIncompletion(
                        "Remote agent task ended in state '"
                                + state
                                + "'"
                                + suffix(A2AResults.extractText(agentTask)));
                break;
        }
    }

    private void keepPolling(TaskResult result, A2ACallRequest request) {
        result.setStatus(TaskResult.Status.IN_PROGRESS);
        result.setCallbackAfterSeconds(
                usePush(request) ? pushBackstopSeconds(request) : pollInterval(request));
    }

    private A2AMessage buildMessage(A2ACallRequest request, Task task) {
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
            message.setMessageId(deterministicMessageId(task));
        }
        if (message.getKind() == null) {
            message.setKind("message");
        }
        if (message.getContextId() == null && StringUtils.isNotBlank(request.getContextId())) {
            message.setContextId(request.getContextId());
        }
        if (message.getTaskId() == null && StringUtils.isNotBlank(request.getTaskId())) {
            message.setTaskId(request.getTaskId());
        }
        if (message.getMetadata() == null && request.getMetadata() != null) {
            message.setMetadata(request.getMetadata());
        }
        return message;
    }

    private Map<String, Object> buildConfiguration(
            A2ACallRequest request, Task task, TaskResult result, boolean usePush) {
        Map<String, Object> configuration = new HashMap<>();
        if (request.getHistoryLength() != null) {
            configuration.put("historyLength", request.getHistoryLength());
        }
        if (usePush) {
            long expiryMs = System.currentTimeMillis() + PUSH_TOKEN_TTL_MS;
            String token = UUID.randomUUID() + ":" + expiryMs;
            result.getOutputData().put(A2AResults.KEY_PUSH_TOKEN, token);
            PushNotificationConfig pushConfig =
                    new PushNotificationConfig(
                            StringUtils.removeEnd(callbackUrl, "/")
                                    + "/api/a2a/callback/"
                                    + task.getTaskId(),
                            token);
            pushConfig.setAuthentication(
                    Map.of("schemes", List.of("Bearer"), "credentials", token));
            configuration.put(
                    "pushNotificationConfig", objectMapper.convertValue(pushConfig, Map.class));
        }
        return configuration.isEmpty() ? null : configuration;
    }

    private void cancelRemoteBestEffort(
            A2ACallRequest request, String remoteTaskId, String reason) {
        if (StringUtils.isBlank(request.getAgentUrl()) || StringUtils.isBlank(remoteTaskId)) {
            return;
        }
        try {
            a2aService.cancelTask(request.getAgentUrl(), remoteTaskId, request.getHeaders());
        } catch (Exception e) {
            log.warn(
                    "Failed to propagate {} to remote A2A task {}: {}",
                    reason,
                    remoteTaskId,
                    e.getMessage());
        }
    }

    private boolean usePush(A2ACallRequest request) {
        if (!request.isPushNotification()) {
            return false;
        }
        if (callbackUrl == null) {
            log.warn(
                    "pushNotification requested but '{}' is not configured; falling back to polling",
                    CALLBACK_URL_PROPERTY);
            return false;
        }
        return true;
    }

    private boolean deadlineExceeded(TaskResult result, A2ACallRequest request) {
        long startedAt =
                asLong(
                        result.getOutputData().get(A2AResults.KEY_STARTED_AT),
                        System.currentTimeMillis());
        return System.currentTimeMillis() - startedAt > maxDurationSeconds(request) * 1000L;
    }

    private static long pollInterval(A2ACallRequest request) {
        return request.getPollIntervalSeconds() != null
                ? Math.max(1, request.getPollIntervalSeconds())
                : DEFAULT_POLL_SECONDS;
    }

    private static long pushBackstopSeconds(A2ACallRequest request) {
        return request.getPushBackstopPollSeconds() != null
                ? Math.max(1, request.getPushBackstopPollSeconds())
                : DEFAULT_PUSH_BACKSTOP_SECONDS;
    }

    private static long maxDurationSeconds(A2ACallRequest request) {
        return request.getMaxDurationSeconds() != null
                ? Math.max(1, request.getMaxDurationSeconds())
                : DEFAULT_MAX_DURATION_SECONDS;
    }

    private static int maxPollFailures(A2ACallRequest request) {
        return request.getMaxPollFailures() != null
                ? Math.max(1, request.getMaxPollFailures())
                : DEFAULT_MAX_POLL_FAILURES;
    }

    private static String deterministicMessageId(Task task) {
        return "a2a-"
                + task.getWorkflowInstanceId()
                + ":"
                + task.getReferenceTaskName()
                + ":"
                + task.getIteration();
    }

    private static TaskResult resultFor(Task task) {
        TaskResult result = new TaskResult(task);
        result.setOutputData(
                new HashMap<>(task.getOutputData() != null ? task.getOutputData() : Map.of()));
        return result;
    }

    /**
     * Separates worker output from Conductor's task execution envelope. The returned POJO is
     * serialized into task output data, while status, callback timing, and failure details remain
     * on the TaskResult owned by the current TaskContext.
     */
    private <T> T finish(TaskResult result, Class<T> outputType) {
        if (result.getStatus() != TaskResult.Status.IN_PROGRESS) {
            result.setCallbackAfterSeconds(0);
        }

        TaskResult contextResult = TaskContext.get().getTaskResult();
        contextResult.setStatus(result.getStatus());
        contextResult.setCallbackAfterSeconds(result.getCallbackAfterSeconds());
        contextResult.setReasonForIncompletion(result.getReasonForIncompletion());
        contextResult.setWorkerId(result.getWorkerId());
        contextResult.setSubWorkflowId(result.getSubWorkflowId());
        contextResult.setExternalOutputPayloadStoragePath(
                result.getExternalOutputPayloadStoragePath());
        contextResult.setExtendLease(result.isExtendLease());
        contextResult.setOutputData(result.getOutputData());
        contextResult.setLogs(result.getLogs());
        if (result.getExecutionMetadata() != null) {
            contextResult.setExecutionMetadata(result.getExecutionMetadata());
        }

        return objectMapper.convertValue(result.getOutputData(), outputType);
    }

    private <T> T parse(Task task, Class<T> type) {
        return objectMapper.convertValue(task.getInputData(), type);
    }

    private static <T extends TaskResult> T fail(T result, String reason, boolean nonRetryable) {
        result.setStatus(
                nonRetryable
                        ? TaskResult.Status.FAILED_WITH_TERMINAL_ERROR
                        : TaskResult.Status.FAILED);
        result.setReasonForIncompletion(reason);
        return result;
    }

    private static void requireA2a(String agentType) {
        if (!A2AService.isA2aAgentType(agentType)) {
            throw new NonRetryableException(
                    "Unsupported agentType '" + agentType + "' (only 'a2a' is supported)");
        }
    }

    private static void recordOutcome(TaskResult result) {
        if (result.getStatus() != null && result.getStatus() != TaskResult.Status.IN_PROGRESS) {
            A2AMetrics.clientCall(result.getStatus().name().toLowerCase());
        }
    }

    private static boolean isEmptyMessage(A2AMessage message) {
        return message == null || message.getParts() == null || message.getParts().isEmpty();
    }

    private static long asLong(Object value, long defaultValue) {
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    private static String suffix(String text) {
        return text == null ? "" : ": " + text;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
