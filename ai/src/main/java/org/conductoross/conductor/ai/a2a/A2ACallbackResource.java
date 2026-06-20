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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.models.A2ACallRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.service.TaskService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Receives A2A push notifications from remote agents and completes the corresponding {@code
 * CALL_AGENT} task that is waiting in push mode.
 *
 * <p>Token is read from the {@code Authorization: Bearer <token>} header (preferred), then the
 * {@code X-Conductor-A2A-Token} custom header, then — for backward compatibility — the {@code
 * token} query parameter (deprecated; logs a warning). Comparison is constant-time. Tokens embed an
 * expiry timestamp ({@code {uuid}:{epochMillis}}) and are rejected once expired.
 */
@RestController
@RequestMapping("/api/a2a")
@Conditional(AIIntegrationEnabledCondition.class)
public class A2ACallbackResource {

    private static final Logger log = LoggerFactory.getLogger(A2ACallbackResource.class);

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final TaskService taskService;
    private final A2AService a2aService;

    public A2ACallbackResource(TaskService taskService, A2AService a2aService) {
        this.taskService = taskService;
        this.a2aService = a2aService;
    }

    @PostMapping("/callback/{taskId}")
    public ResponseEntity<Void> onPushNotification(
            @PathVariable("taskId") String taskId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Conductor-A2A-Token", required = false) String customHeader,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestBody(required = false) JsonNode payload) {

        try (A2ALogging.Scope scope = A2ALogging.of(A2ALogging.TASK_ID, taskId)) {
            String token = resolveToken(authHeader, customHeader, queryToken);

            Task task = loadTask(taskId);
            if (task == null || !CallAgentTask.TASK_TYPE.equals(task.getTaskType())) {
                return ResponseEntity.notFound().build();
            }
            scope.add(A2ALogging.WORKFLOW_ID, task.getWorkflowInstanceId())
                    .add(A2ALogging.REF, task.getReferenceTaskName());

            Object storedToken =
                    task.getOutputData() != null
                            ? task.getOutputData().get(A2AResults.KEY_PUSH_TOKEN)
                            : null;
            if (!isValidToken(storedToken, token)) {
                log.warn("A2A push for task {} rejected: token mismatch or expired", taskId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (task.getStatus() != Task.Status.IN_PROGRESS) {
                // Already completed (or not waiting) — nothing to do; ack so the agent stops
                // retrying.
                return ResponseEntity.ok().build();
            }

            A2ACallRequest request =
                    objectMapper.convertValue(task.getInputData(), A2ACallRequest.class);
            String agentTaskId = asString(task.getOutputData().get(A2AResults.KEY_TASK_ID));
            if (isBlank(request.getAgentUrl()) || isBlank(agentTaskId)) {
                return ResponseEntity.ok().build();
            }

            A2ATask agentTask;
            try {
                agentTask =
                        a2aService.getTask(
                                request.getAgentUrl(),
                                agentTaskId,
                                request.getHistoryLength(),
                                request.getHeaders());
            } catch (Exception e) {
                log.warn(
                        "A2A push for task {}: failed to fetch agent task {} from {}: {}",
                        taskId,
                        agentTaskId,
                        request.getAgentUrl(),
                        e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            String state = agentTask.getStatus() != null ? agentTask.getStatus().getState() : null;
            if (!TaskState.isTerminal(state) && !TaskState.isInterrupted(state)) {
                // Not done yet — ack and wait for the next push.
                return ResponseEntity.ok().build();
            }

            Map<String, Object> output = A2AResults.taskOutput(agentTask, objectMapper);
            taskService.updateTask(
                    task.getWorkflowInstanceId(),
                    task.getReferenceTaskName(),
                    toResultStatus(state),
                    "a2a-callback",
                    output);
            log.debug("A2A push completed task {} (agent state {})", taskId, state);
            return ResponseEntity.ok().build();
        }
    }

    /**
     * Resolves the token from headers (preferred) then query param (deprecated). Bearer header
     * wins, then the custom header, then query param. Using the query param logs a deprecation
     * warning because tokens in URLs appear in server access logs.
     */
    private String resolveToken(String authHeader, String customHeader, String queryToken) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        if (customHeader != null && !customHeader.isBlank()) {
            return customHeader.trim();
        }
        if (queryToken != null && !queryToken.isBlank()) {
            log.warn(
                    "A2A push token received via query parameter — tokens in URLs appear in access"
                            + " logs. Configure the agent to send the token as"
                            + " 'Authorization: Bearer <token>' instead.");
            return queryToken;
        }
        return null;
    }

    /**
     * Validates the inbound token against the stored one using constant-time comparison and checks
     * the embedded expiry timestamp. Token format: {@code {uuid}:{expiryEpochMillis}}.
     */
    private boolean isValidToken(Object stored, String inbound) {
        if (stored == null || inbound == null) {
            return false;
        }
        String storedStr = stored.toString();
        // Constant-time comparison — prevents timing oracle on the UUID portion.
        boolean match =
                MessageDigest.isEqual(
                        storedStr.getBytes(StandardCharsets.UTF_8),
                        inbound.getBytes(StandardCharsets.UTF_8));
        if (!match) {
            return false;
        }
        // Extract and validate the expiry timestamp embedded in the token.
        int sep = storedStr.lastIndexOf(':');
        if (sep > 0) {
            try {
                long expiryMs = Long.parseLong(storedStr.substring(sep + 1));
                if (System.currentTimeMillis() > expiryMs) {
                    log.warn("A2A push token is expired (expiry was {})", expiryMs);
                    return false;
                }
            } catch (NumberFormatException ignored) {
                // Token pre-dates the timestamped format — accept it as valid (no expiry check).
            }
        }
        return true;
    }

    private Task loadTask(String taskId) {
        try {
            return taskService.getTask(taskId);
        } catch (Exception e) {
            return null;
        }
    }

    private TaskResult.Status toResultStatus(String state) {
        switch (TaskState.normalize(state)) {
            case TaskState.FAILED:
            case TaskState.REJECTED:
                return TaskResult.Status.FAILED;
            default:
                // completed / canceled / input-required / auth-required
                return TaskResult.Status.COMPLETED;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
