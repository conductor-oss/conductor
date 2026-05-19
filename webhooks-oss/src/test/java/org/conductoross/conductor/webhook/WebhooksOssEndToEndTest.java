/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.conductoross.conductor.webhook;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.conductoross.conductor.webhook.dao.memory.InMemoryWebhookDAO;
import org.conductoross.conductor.webhook.dao.memory.InMemoryWebhookTaskService;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.rest.IncomingWebhookResource;
import org.conductoross.conductor.webhook.service.IncomingWebhookService;
import org.conductoross.conductor.webhook.service.WebhookConfigService;
import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.webhook.verifier.WebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real bean graph for the webhooks-oss module end-to-end: register a config →
 * receive an HTTP event → store + enqueue → worker dispatches → workflow start / waiting task
 * completion. Only the workflow executor, queue DAO, and metadata DAO are mocked.
 */
@ExtendWith(MockitoExtension.class)
class WebhooksOssEndToEndTest {

    @Mock private QueueDAO queueDAO;
    @Mock private WorkflowExecutor workflowExecutor;
    @Mock private ExecutionDAOFacade executionDAOFacade;
    @Mock private MetadataDAO metadataDAO;
    @Mock private ParametersUtils parametersUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IDGenerator idGenerator = new IDGenerator();

    private InMemoryWebhookDAO webhookDAO;
    private InMemoryWebhookTaskService webhookTaskService;
    private TargetWorkflowCollector targetWorkflowCollector;
    private WebhookConfigService configService;
    private IncomingWebhookService incomingService;
    private IncomingWebhookResource resource;
    private WebhookHashingService hashingService;
    private WebhookWorker worker;
    private Webhook systemTask;

    @BeforeEach
    void wire() {
        webhookDAO = new InMemoryWebhookDAO(metadataDAO);
        webhookTaskService = new InMemoryWebhookTaskService();
        targetWorkflowCollector = new TargetWorkflowCollector(Map.<String, Evaluator>of(), parametersUtils);
        configService = new WebhookConfigService(webhookDAO, targetWorkflowCollector, idGenerator);
        hashingService = new WebhookHashingService();
        Set<WebhookVerifier> verifiers = Set.of(new StubVerifier());
        incomingService = new IncomingWebhookService(
                webhookDAO, verifiers, queueDAO, idGenerator, executionDAOFacade);
        resource = new IncomingWebhookResource(incomingService);
        WebhookWorkerProperties properties = new WebhookWorkerProperties();
        properties.setThreadCount(0); // disable polling thread
        worker = new WebhookWorker(
                objectMapper, queueDAO, webhookDAO, properties,
                hashingService, workflowExecutor, webhookTaskService,
                executionDAOFacade, targetWorkflowCollector);
        systemTask = new Webhook(webhookTaskService);
    }

    @Test
    void register_then_receive_storesEventAndPushesQueue() {
        WebhookConfig config = newConfigForWorkflowStart();
        configService.createWebhook(config);
        String webhookId = config.getId();
        assertThat(webhookId).isNotBlank();
        assertThat(webhookDAO.getWebhook(webhookId)).isSameAs(config);

        String response = resource.handleWebhook(
                webhookId, "{\"event\":\"push\"}", Map.of(), new HttpHeaders());

        assertThat(response).isNull();
        verify(queueDAO).push(eq(WebhookWorkerProperties.WEBHOOK_QUEUE), any(String.class), anyLong());
    }

    @Test
    void receive_unknownWebhookId_returnsNullAndNoEnqueue() {
        String response = resource.handleWebhook(
                "missing-id", "{}", Map.of(), new HttpHeaders());

        assertThat(response).isNull();
        verify(queueDAO, never()).push(any(), any(String.class), anyLong());
    }

    @Test
    void register_workflow_completion_path_end_to_end() {
        // Workflow def with a single WAIT_FOR_WEBHOOK task expecting matches={event:push}
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setType("WAIT_FOR_WEBHOOK");
        waitTask.setTaskReferenceName("wait_ref");
        waitTask.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def = new WorkflowDef();
        def.setName("wf-a");
        def.setVersion(1);
        def.setTasks(List.of(waitTask));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def));

        // Register a webhook config whose receiver is wf-a v1
        WebhookConfig config = new WebhookConfig();
        config.setName("hook-a");
        config.setReceiverWorkflowNamesToVersions(Map.of("wf-a", 1));
        config.setVerifier(WebhookConfig.Verifier.HMAC_BASED);
        configService.createWebhook(config);
        String webhookId = config.getId();

        // Simulate the workflow having reached the WAIT_FOR_WEBHOOK task — this is what
        // the system-task path does in production via Webhook.start().
        TaskModel waitingTask = new TaskModel();
        waitingTask.setTaskId("task-1");
        waitingTask.setWorkflowType("wf-a");
        waitingTask.setReferenceTaskName("wait_ref");
        waitingTask.setStatus(TaskModel.Status.IN_PROGRESS);
        waitingTask.setWorkflowInstanceId("wf-instance-1");
        waitingTask.setInputData(Map.of("matches", Map.of("event", "push")));
        WorkflowDef wfDef = new WorkflowDef();
        wfDef.setName("wf-a");
        wfDef.setVersion(1);
        com.netflix.conductor.model.WorkflowModel workflow = new com.netflix.conductor.model.WorkflowModel();
        workflow.setWorkflowDefinition(wfDef);
        systemTask.start(workflow, waitingTask, workflowExecutor);

        when(executionDAOFacade.getTaskModel("task-1")).thenReturn(waitingTask);

        // Receive a matching webhook event
        resource.handleWebhook(webhookId, "{\"event\":\"push\"}", Map.of(), new HttpHeaders());

        // Capture the messageId that got enqueued and feed it to the worker
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueDAO).push(eq(WebhookWorkerProperties.WEBHOOK_QUEUE), messageIdCaptor.capture(), anyLong());
        worker.handleMessage(messageIdCaptor.getValue());

        // Worker should have called updateTask with COMPLETED
        ArgumentCaptor<TaskResult> resultCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus()).isEqualTo(TaskResult.Status.COMPLETED);
        assertThat(resultCaptor.getValue().getTaskId()).isEqualTo("task-1");
    }

    @Test
    void receive_failedVerification_throwsAndNoEnqueue() {
        WebhookConfig config = new WebhookConfig();
        config.setName("hook-bad");
        config.setVerifier(WebhookConfig.Verifier.HMAC_BASED);
        config.setWorkflowsToStart(Map.of("wf-x", 1));
        configService.createWebhook(config);

        // StubVerifier rejects bodies starting with "REJECT"
        assertThatThrownBy(() -> resource.handleWebhook(
                config.getId(), "REJECT", Map.of(), new HttpHeaders()))
                .isInstanceOf(com.netflix.conductor.core.exception.NonTransientException.class);
        verify(queueDAO, never()).push(any(), any(String.class), anyLong());
    }

    private WebhookConfig newConfigForWorkflowStart() {
        WebhookConfig config = new WebhookConfig();
        config.setName("hook-start");
        config.setVerifier(WebhookConfig.Verifier.HMAC_BASED);
        config.setWorkflowsToStart(Map.of("wf-start", 1));
        return config;
    }

    /** Minimal verifier that approves all bodies that do not begin with "REJECT". */
    static class StubVerifier implements WebhookVerifier {
        @Override
        public String getType() {
            return WebhookConfig.Verifier.HMAC_BASED.toString();
        }

        @Override
        public org.conductoross.conductor.common.utils.ErrorList verify(
                WebhookConfig webhookConfig,
                org.conductoross.conductor.webhook.model.IncomingWebhookEvent event) {
            org.conductoross.conductor.common.utils.ErrorList errors =
                    new org.conductoross.conductor.common.utils.ErrorList();
            if (event.getBody() != null && event.getBody().startsWith("REJECT")) {
                errors.add("stub rejected");
            }
            return errors;
        }

        @Override
        public String extractChallenge(
                org.conductoross.conductor.webhook.model.IncomingWebhookEvent event,
                WebhookConfig webhookConfig) {
            return null;
        }

        @Override
        public String handlePing(WebhookConfig webhookConfig, Map<String, Object> requestParams) {
            return null;
        }
    }
}
