/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Processes inbound webhook HTTP requests synchronously.
 *
 * <p>This is the OSS equivalent of Orkes's {@code IncomingWebhookService} + {@code WebhookWorker}
 * combined. Rather than storing events in a queue for async processing, OSS completes matching
 * {@code WAIT_FOR_WEBHOOK} tasks inline within the HTTP request:
 *
 * <ol>
 *   <li>Build an {@link IncomingWebhookEvent} from the request.
 *   <li>Look up the {@link WebhookConfig} by id.
 *   <li>Verify the request with the configured {@link WebhookVerifier}.
 *   <li>Handle challenge/response handshakes (e.g. Slack URL verification).
 *   <li>For each stored matcher (baseHashKey → criteria): compute hash from the payload and
 *       complete any {@code WAIT_FOR_WEBHOOK} tasks registered under that hash.
 *   <li>Start any workflows listed in {@link WebhookConfig#getWorkflowsToStart()} with the inbound
 *       payload as input.
 * </ol>
 *
 * <p>Ported from Orkes Enterprise; org context is applied by {@link WebhookOrgContextProvider}
 * before this service is called — no {@code OrkesRequestContext} references here.
 */
public class IncomingWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingWebhookService.class);

    private final WebhookConfigDAO webhookConfigDAO;
    private final WebhookTaskDAO webhookTaskDAO;
    private final WebhookHashingService webhookHashingService;
    private final Map<String, WebhookVerifier> verifiersByType;
    private final ExecutionDAOFacade executionDAOFacade;
    private final WorkflowExecutor workflowExecutor;
    private final ObjectMapper objectMapper;

    public IncomingWebhookService(
            WebhookConfigDAO webhookConfigDAO,
            WebhookTaskDAO webhookTaskDAO,
            WebhookHashingService webhookHashingService,
            List<WebhookVerifier> verifiers,
            ExecutionDAOFacade executionDAOFacade,
            WorkflowExecutor workflowExecutor,
            ObjectMapper objectMapper) {
        this.webhookConfigDAO = webhookConfigDAO;
        this.webhookTaskDAO = webhookTaskDAO;
        this.webhookHashingService = webhookHashingService;
        this.verifiersByType =
                verifiers.stream()
                        .collect(Collectors.toMap(WebhookVerifier::getType, Function.identity()));
        this.executionDAOFacade = executionDAOFacade;
        this.workflowExecutor = workflowExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles a {@code POST /webhook/{id}} inbound event.
     *
     * @param id the webhook config id from the URL path
     * @param bodyStr the raw request body
     * @param requestParams query parameters
     * @param headers HTTP request headers
     * @return a challenge string to echo back, or {@code null} if no challenge is required
     * @throws IllegalArgumentException if the webhook config does not exist, no verifier is
     *     registered for the config's type, or verification fails
     */
    public String handleWebhook(
            String id, String bodyStr, Map<String, Object> requestParams, HttpHeaders headers) {
        IncomingWebhookEvent event = buildEvent(id, bodyStr, headers, requestParams);

        WebhookConfig config = webhookConfigDAO.get(id);
        if (config == null) {
            LOGGER.warn("Inbound event received for unknown webhook config id={}", id);
            throw new IllegalArgumentException("Webhook config not found: " + id);
        }

        WebhookVerifier verifier = resolveVerifier(config);

        List<String> errors = verifier.verify(config, event);
        if (!errors.isEmpty()) {
            String msg =
                    "Webhook verification failed for id=" + id + ": " + String.join(", ", errors);
            LOGGER.error(msg);
            throw new IllegalArgumentException(msg);
        }

        // Mark URL as verified on first successful event
        if (!config.isUrlVerified()) {
            config.setUrlVerified(true);
            webhookConfigDAO.save(id, config);
        }

        // Challenge/response (e.g. Slack URL verification) — return challenge, skip task dispatch
        String challenge = verifier.extractChallenge(event, config);
        if (challenge != null) {
            LOGGER.debug("Returning challenge response for webhook id={}", id);
            return challenge;
        }

        Map<String, Object> safeParams =
                requestParams != null ? requestParams : Collections.emptyMap();
        Map<String, Object> payload = parsePayload(bodyStr, safeParams);
        dispatchToWaitingTasks(id, bodyStr, safeParams, payload);
        startWorkflowsToStart(config, payload, event.getId());
        return null;
    }

    /**
     * Handles a {@code GET /webhook/{id}} ping request from the webhook provider.
     *
     * @param id the webhook config id
     * @param requestParams query parameters (may contain challenge value)
     * @return the ping response to echo back, or {@code null}
     */
    public String handlePing(String id, Map<String, Object> requestParams) {
        WebhookConfig config = webhookConfigDAO.get(id);
        if (config == null) {
            LOGGER.warn("Ping received for unknown webhook config id={}", id);
            return null;
        }

        WebhookVerifier verifier = resolveVerifier(config);
        String response =
                verifier.handlePing(
                        config, requestParams != null ? requestParams : Collections.emptyMap());

        if (response != null) {
            // Ping confirmed — mark URL as verified
            config.setUrlVerified(true);
            webhookConfigDAO.save(id, config);
            LOGGER.debug("Webhook id={} URL verified via ping", id);
        }

        return response;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private IncomingWebhookEvent buildEvent(
            String webhookId, String body, HttpHeaders headers, Map<String, Object> requestParams) {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId(UUID.randomUUID().toString());
        event.setWebhookId(webhookId);
        event.setBody(body != null ? body : "{}");
        event.setHeaders(headers);
        event.setRequestParams(requestParams != null ? requestParams : Collections.emptyMap());
        event.setTimeStamp(System.currentTimeMillis());
        return event;
    }

    private WebhookVerifier resolveVerifier(WebhookConfig config) {
        if (config.getVerifier() == null) {
            throw new IllegalArgumentException(
                    "Webhook config id=" + config.getId() + " has no verifier configured");
        }
        WebhookVerifier verifier = verifiersByType.get(config.getVerifier().toString());
        if (verifier == null) {
            throw new IllegalArgumentException(
                    "No verifier registered for type: " + config.getVerifier());
        }
        return verifier;
    }

    /**
     * For each matcher stored for this webhook config, compute the routing hash from the inbound
     * payload and complete any registered {@code WAIT_FOR_WEBHOOK} tasks that match.
     */
    private void dispatchToWaitingTasks(
            String webhookId,
            String body,
            Map<String, Object> requestParams,
            Map<String, Object> payload) {
        Map<String, Map<String, Object>> matchers = webhookConfigDAO.getMatchers(webhookId);
        if (matchers.isEmpty()) {
            LOGGER.debug("No matchers stored for webhook id={}", webhookId);
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : matchers.entrySet()) {
            String baseKey = entry.getKey();
            Map<String, Object> criteria = entry.getValue();

            String hash =
                    webhookHashingService.computeInboundHash(
                            baseKey, criteria, body, requestParams);
            if (hash == null) {
                LOGGER.debug(
                        "Inbound payload did not match criteria for baseKey={} webhookId={}",
                        baseKey,
                        webhookId);
                continue;
            }

            List<String> taskIds = webhookTaskDAO.get(hash);
            if (taskIds.isEmpty()) {
                LOGGER.debug("No waiting tasks found for hash={}", hash);
                continue;
            }

            LOGGER.debug(
                    "Completing {} task(s) for hash={} webhookId={}",
                    taskIds.size(),
                    hash,
                    webhookId);
            for (String taskId : taskIds) {
                completeTask(taskId, hash, payload);
            }
        }
    }

    private void completeTask(String taskId, String hash, Map<String, Object> payload) {
        TaskModel task = executionDAOFacade.getTaskModel(taskId);
        if (task == null) {
            LOGGER.warn("Task not found for taskId={} — removing stale hash entry", taskId);
            webhookTaskDAO.remove(hash, taskId);
            return;
        }
        if (task.getStatus().isTerminal()) {
            LOGGER.debug("Task {} is already terminal ({}), skipping", taskId, task.getStatus());
            webhookTaskDAO.remove(hash, taskId);
            return;
        }

        TaskResult result = new TaskResult(task.toTask());
        result.setStatus(TaskResult.Status.COMPLETED);
        result.getOutputData().put("payload", payload);
        workflowExecutor.updateTask(result);
        webhookTaskDAO.remove(hash, taskId);

        LOGGER.debug(
                "Completed WAIT_FOR_WEBHOOK task {} in workflow {}",
                taskId,
                task.getWorkflowInstanceId());
    }

    /**
     * Starts any workflows declared in {@link WebhookConfig#getWorkflowsToStart()}.
     *
     * <p>The map format mirrors Orkes's convention: {@code workflowName → version} for regular
     * entries, with two optional special keys extracted and then discarded before iterating:
     *
     * <ul>
     *   <li>{@code idempotencyKey} — idempotency key string (ignored in OSS; no OSS support yet)
     *   <li>{@code idempotencyStrategy} — deduplication strategy (ignored in OSS)
     * </ul>
     *
     * <p>Each workflow is started with the inbound payload as its input and the webhook name +
     * event id as its triggering event. Failures to start one workflow do not prevent others from
     * starting.
     */
    @SuppressWarnings("unchecked")
    private void startWorkflowsToStart(
            WebhookConfig config, Map<String, Object> payload, String eventId) {
        Map<String, Object> workflowsToStart = config.getWorkflowsToStart();
        if (workflowsToStart == null || workflowsToStart.isEmpty()) {
            return;
        }

        // Work on a copy so we can pop reserved keys without mutating the stored config
        Map<String, Object> wfsToStart = new HashMap<>(workflowsToStart);
        wfsToStart.remove("idempotencyKey");
        wfsToStart.remove("idempotencyStrategy");

        String eventName = config.getName() + ":" + eventId;

        for (Map.Entry<String, Object> entry : wfsToStart.entrySet()) {
            String workflowName = entry.getKey();
            Object versionObj = entry.getValue();
            if (!(versionObj instanceof Integer)) {
                LOGGER.warn(
                        "workflowsToStart entry '{}' has non-integer version '{}' — skipping",
                        workflowName,
                        versionObj);
                continue;
            }
            int version = (Integer) versionObj;
            try {
                StartWorkflowInput input = new StartWorkflowInput();
                input.setName(workflowName);
                input.setVersion(version);
                input.setWorkflowInput(payload);
                input.setEvent(eventName);
                String workflowId = workflowExecutor.startWorkflow(input);
                LOGGER.debug(
                        "Started workflow '{}' v{} id={} from webhook '{}'",
                        workflowName,
                        version,
                        workflowId,
                        config.getName());
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to start workflow '{}' v{} from webhook '{}': {}",
                        workflowName,
                        version,
                        config.getName(),
                        e.getMessage(),
                        e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String body, Map<String, Object> requestParams) {
        Map<String, Object> payload = new HashMap<>();
        try {
            Object parsed = objectMapper.readValue(body, new TypeReference<Object>() {});
            if (parsed instanceof Map) {
                payload.putAll((Map<String, Object>) parsed);
            } else {
                payload.put("request", parsed);
            }
        } catch (JsonProcessingException e) {
            LOGGER.warn("Could not parse webhook body as JSON — storing raw body");
            payload.put("body", body);
        }
        if (requestParams != null) {
            payload.putAll(requestParams);
        }
        return payload;
    }

    /** Returns the set of registered verifier types. Useful for diagnostics. */
    public Set<String> getRegisteredVerifierTypes() {
        return Collections.unmodifiableSet(verifiersByType.keySet());
    }
}
