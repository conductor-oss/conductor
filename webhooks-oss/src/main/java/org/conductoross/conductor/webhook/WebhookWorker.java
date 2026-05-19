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
package org.conductoross.conductor.webhook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.model.WebhookExecutionHistory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.conductoross.conductor.webhook.WebhookWorkerProperties.WEBHOOK_QUEUE;

/**
 * Polls {@link WebhookWorkerProperties#WEBHOOK_QUEUE} for stored {@link IncomingWebhookEvent}
 * messages and dispatches each: completes any waiting {@code WAIT_FOR_WEBHOOK} tasks that match the
 * configured matchers, then starts any workflows declared in {@link
 * WebhookConfig#getWorkflowsToStart()}.
 *
 * <p>OSS port of the Orkes Enterprise {@code WebhookWorker}: same polling/dispatch flow, with
 * multi-tenant orgId handling, audit logging, and {@code ExtendedEventExecution} bookkeeping
 * removed. Failures are logged rather than persisted as {@code EventExecution}s.
 */
@Component
@Slf4j
public class WebhookWorker {

    private final QueueDAO queueDAO;
    private final ObjectMapper objectMapper;
    private final WebhookHashingService webhookHashingService;
    private final WebhookTaskService webhookTaskService;
    private final WorkflowExecutor workflowExecutor;
    private final WebhookDAO webhookDAO;
    private final ExecutionDAOFacade executionDAOFacade;
    private final TargetWorkflowCollector targetWorkflowCollector;

    private final int threadCount;
    private final int pollingIntervalMs;
    private final int pollBatchSize;
    private final int lastRunWorkflowIdSize;

    private ScheduledExecutorService executorService;

    public WebhookWorker(
            ObjectMapper objectMapper,
            QueueDAO queueDAO,
            WebhookDAO webhookDAO,
            WebhookWorkerProperties properties,
            WebhookHashingService webhookHashingService,
            WorkflowExecutor workflowExecutor,
            WebhookTaskService webhookTaskService,
            ExecutionDAOFacade executionDAOFacade,
            TargetWorkflowCollector targetWorkflowCollector) {
        this.objectMapper = objectMapper;
        this.queueDAO = queueDAO;
        this.webhookDAO = webhookDAO;
        this.webhookHashingService = webhookHashingService;
        this.workflowExecutor = workflowExecutor;
        this.webhookTaskService = webhookTaskService;
        this.executionDAOFacade = executionDAOFacade;
        this.targetWorkflowCollector = targetWorkflowCollector;
        this.threadCount = properties.getThreadCount();
        this.pollingIntervalMs = properties.getPollingInterval();
        this.pollBatchSize = properties.getPollBatchSize();
        this.lastRunWorkflowIdSize = properties.getLastRunWorkflowIdSize();
    }

    @PostConstruct
    void start() {
        if (threadCount <= 0) {
            log.info("WebhookWorker disabled (threadCount={})", threadCount);
            return;
        }
        executorService =
                Executors.newScheduledThreadPool(
                        threadCount,
                        r -> {
                            Thread t = new Thread(r);
                            t.setName("webhookWorker-" + t.getId());
                            t.setDaemon(true);
                            return t;
                        });
        for (int i = 0; i < threadCount; i++) {
            executorService.scheduleWithFixedDelay(
                    this::pollAndExecuteSafely, 10, pollingIntervalMs, TimeUnit.MILLISECONDS);
        }
        log.info(
                "WebhookWorker started: threadCount={}, pollingIntervalMs={}, pollBatchSize={}",
                threadCount,
                pollingIntervalMs,
                pollBatchSize);
    }

    @PreDestroy
    void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void pollAndExecuteSafely() {
        try {
            pollAndExecute();
        } catch (Throwable t) {
            log.error("webhook poll failed: {}", t.getMessage(), t);
        }
    }

    void pollAndExecute() {
        List<String> messages = queueDAO.pop(WEBHOOK_QUEUE, pollBatchSize, 1000);
        for (String id : messages) {
            try {
                handleMessage(id);
                queueDAO.ack(WEBHOOK_QUEUE, id);
            } catch (Throwable t) {
                // Do NOT ack — let the queue's unack timeout redeliver this
                // message. Poison messages will be retried until the underlying
                // QueueDAO impl's retry policy gives up (e.g. moves to a
                // dead-letter table). Acking-on-failure here would silently drop
                // webhook events on any transient failure (DB blip, OOM, etc.).
                log.error(
                        "webhook message {} processing failed; will be redelivered: {}",
                        id,
                        t.getMessage(),
                        t);
            }
        }
    }

    void handleMessage(String messageId) {
        IncomingWebhookEvent event =
                objectMapper.convertValue(
                        webhookDAO.getWebhookEvent(messageId), IncomingWebhookEvent.class);
        if (event == null) {
            log.warn("webhook event {} not found, skipping", messageId);
            return;
        }
        WebhookConfig webhookConfig = webhookDAO.getWebhook(event.getWebhookId());
        if (webhookConfig == null) {
            log.warn(
                    "webhook {} not found for event {}, dropping", event.getWebhookId(), messageId);
            return;
        }

        Set<String> matchedWorkflowIds = new HashSet<>();
        Map<String, Map<String, Object>> matchers = webhookDAO.getMatchers(event.getWebhookId());
        for (Map.Entry<String, Map<String, Object>> entry : matchers.entrySet()) {
            Map<String, Object> value = entry.getValue();
            if (value == null) {
                log.debug(
                        "misconfigured matcher entry for webhook {}: {}",
                        event.getWebhookId(),
                        entry);
                continue;
            }
            String hash =
                    webhookHashingService.computeJsonHash(
                            new StringBuilder(entry.getKey()),
                            value,
                            event.getBody(),
                            event.getRequestParams());
            if (hash == null) {
                log.debug(
                        "no matching hash for webhook {} matcher {}", event.getWebhookId(), entry);
                continue;
            }
            completeTasksFor(hash, getPayload(event), matchedWorkflowIds, webhookConfig, event);
        }

        webhookConfig.accept(targetWorkflowCollector);
        recordHistory(matchedWorkflowIds, webhookConfig, event);

        Map<String, Object> requestBody = getPayload(event);
        String idempotencyKey = targetWorkflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy idempotencyStrategy = targetWorkflowCollector.popIdempotencyStrategy();

        Map<String, Object> workflowsToStart = targetWorkflowCollector.getWorkflowsToStart();
        if (workflowsToStart != null) {
            String eventName = webhookConfig.getName() + ":" + event.getId();
            workflowsToStart.forEach(
                    (workflowName, versionObj) -> {
                        if (!(versionObj instanceof Integer version)) {
                            log.warn(
                                    "workflowToStart {} for webhook {} has non-integer version: {}",
                                    workflowName,
                                    webhookConfig.getId(),
                                    versionObj);
                            return;
                        }
                        try {
                            String workflowId =
                                    doStartWorkflow(
                                            eventName,
                                            buildStartRequest(
                                                    workflowName,
                                                    version,
                                                    requestBody,
                                                    idempotencyKey,
                                                    idempotencyStrategy,
                                                    webhookConfig.getCreatedBy()));
                            log.debug(
                                    "started workflow {} (id={}) for webhook {} event {}",
                                    workflowName,
                                    workflowId,
                                    webhookConfig.getId(),
                                    event.getId());
                        } catch (Throwable t) {
                            log.error(
                                    "failed to start workflow {} for webhook {} event {}: {}",
                                    workflowName,
                                    webhookConfig.getId(),
                                    event.getId(),
                                    t.getMessage(),
                                    t);
                        }
                    });
        }
        webhookDAO.removeWebhookEvent(event.getId());
    }

    private void completeTasksFor(
            String hash,
            Map<String, Object> payload,
            Set<String> matchedWorkflowIds,
            WebhookConfig webhookConfig,
            IncomingWebhookEvent event) {
        for (String taskId : webhookTaskService.get(hash)) {
            completeTask(taskId, hash, payload, matchedWorkflowIds, webhookConfig, event);
        }
    }

    private void completeTask(
            String taskId,
            String hash,
            Map<String, Object> payload,
            Set<String> matchedWorkflowIds,
            WebhookConfig webhookConfig,
            IncomingWebhookEvent event) {
        TaskModel taskModel = executionDAOFacade.getTaskModel(taskId);
        if (taskModel == null) {
            log.debug(
                    "task {} not found for webhook {} event {}",
                    taskId,
                    webhookConfig.getId(),
                    event.getId());
            return;
        }
        if (taskModel.getStatus().isTerminal()) {
            log.debug("task {} is already terminal, skipping", taskId);
            return;
        }
        TaskResult taskResult = new TaskResult(taskModel.toTask());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.getOutputData().putAll(payload);
        workflowExecutor.updateTask(taskResult);
        matchedWorkflowIds.add(taskModel.getWorkflowInstanceId());
        webhookTaskService.remove(hash, taskId);
    }

    @SneakyThrows
    private Map<String, Object> getPayload(IncomingWebhookEvent event) {
        Object obj = objectMapper.readValue(event.getBody(), Object.class);
        Map<String, Object> payload = new HashMap<>();
        if (obj instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            payload.putAll(cast);
        } else {
            payload.put("request", obj);
        }
        if (event.getRequestParams() != null) {
            payload.putAll(event.getRequestParams());
        }
        return payload;
    }

    private String doStartWorkflow(String eventName, StartWorkflowRequest request) {
        StartWorkflowInput input = new StartWorkflowInput(request);
        input.setEvent(eventName);
        return workflowExecutor.startWorkflow(input);
    }

    private StartWorkflowRequest buildStartRequest(
            String workflowName,
            Integer version,
            Map<String, Object> input,
            String idempotencyKey,
            IdempotencyStrategy idempotencyStrategy,
            String createdBy) {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(workflowName);
        request.setVersion(version);
        request.setInput(input);
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            request.setIdempotencyKey(idempotencyKey);
            request.setIdempotencyStrategy(idempotencyStrategy);
        }
        request.setCreatedBy(createdBy);
        request.setPriority(0);
        return request;
    }

    @SneakyThrows
    private void recordHistory(
            Set<String> matchedWorkflowIds,
            WebhookConfig webhookConfig,
            IncomingWebhookEvent event) {
        String payload = objectMapper.writeValueAsString(event);
        boolean matched = !matchedWorkflowIds.isEmpty();
        WebhookExecutionHistory history =
                new WebhookExecutionHistory(
                        event.getId(), matched, matchedWorkflowIds, payload, event.getTimeStamp());

        List<WebhookExecutionHistory> hist = webhookConfig.getWebhookExecutionHistory();
        if (hist == null) {
            webhookConfig.setWebhookExecutionHistory(new java.util.ArrayList<>(List.of(history)));
        } else {
            if (hist.size() >= lastRunWorkflowIdSize) {
                hist.remove(hist.size() - 1);
            }
            hist.add(0, history);
        }
        if (matched) {
            webhookConfig.setUrlVerified(true);
        }
        webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
    }
}
