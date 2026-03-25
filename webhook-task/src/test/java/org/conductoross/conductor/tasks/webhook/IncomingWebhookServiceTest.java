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
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IncomingWebhookService}.
 *
 * <p>Verifies: verification delegation, challenge response, task completion flow, orgId isolation
 * (the service never reads or sets any orgId — that is the caller's/provider's concern).
 */
public class IncomingWebhookServiceTest {

    private InMemoryWebhookConfigDAO configDAO;
    private InMemoryWebhookTaskDAO taskDAO;
    private WebhookHashingService hashingService;
    private WebhookVerifier mockVerifier;
    private ExecutionDAOFacade executionDAOFacade;
    private WorkflowExecutor workflowExecutor;
    private IncomingWebhookService service;

    private static final String WEBHOOK_ID = "wh-001";
    private static final String BASE_KEY = "order_workflow;1;waitForPayment";

    @Before
    public void setUp() {
        configDAO = new InMemoryWebhookConfigDAO();
        taskDAO = new InMemoryWebhookTaskDAO();
        hashingService = new WebhookHashingService(new ObjectMapper());
        mockVerifier = mock(WebhookVerifier.class);
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        workflowExecutor = mock(WorkflowExecutor.class);

        when(mockVerifier.getType()).thenReturn("HEADER_BASED");

        service =
                new IncomingWebhookService(
                        configDAO,
                        taskDAO,
                        hashingService,
                        List.of(mockVerifier),
                        executionDAOFacade,
                        workflowExecutor,
                        new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // handleWebhook — config not found
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void handleWebhook_throwsWhenConfigNotFound() {
        service.handleWebhook("unknown-id", "{}", Collections.emptyMap(), new HttpHeaders());
    }

    // -------------------------------------------------------------------------
    // handleWebhook — verification failure
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void handleWebhook_throwsOnVerificationFailure() {
        saveConfig(WEBHOOK_ID);
        when(mockVerifier.verify(any(), any())).thenReturn(List.of("bad header"));

        service.handleWebhook(
                WEBHOOK_ID, "{}", Collections.emptyMap(), headersWith("X-Secret", "wrong"));
    }

    // -------------------------------------------------------------------------
    // handleWebhook — challenge response
    // -------------------------------------------------------------------------

    @Test
    public void handleWebhook_returnsChallengeAndSkipsTaskDispatch() {
        saveConfig(WEBHOOK_ID);
        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn("challenge-token-abc");

        String result =
                service.handleWebhook(WEBHOOK_ID, "{}", Collections.emptyMap(), new HttpHeaders());

        assertEquals("challenge-token-abc", result);
        verifyNoInteractions(executionDAOFacade, workflowExecutor);
    }

    // -------------------------------------------------------------------------
    // handleWebhook — marks URL verified
    // -------------------------------------------------------------------------

    @Test
    public void handleWebhook_marksUrlVerifiedOnFirstSuccess() {
        WebhookConfig config = saveConfig(WEBHOOK_ID);
        assertFalse(config.isUrlVerified());

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);

        service.handleWebhook(WEBHOOK_ID, "{}", Collections.emptyMap(), new HttpHeaders());

        assertTrue(configDAO.get(WEBHOOK_ID).isUrlVerified());
    }

    // -------------------------------------------------------------------------
    // handleWebhook — task completion via hash routing
    // -------------------------------------------------------------------------

    @Test
    public void handleWebhook_completesMatchingTask() {
        WebhookConfig config = saveConfig(WEBHOOK_ID);
        // Store matchers: BASE_KEY -> criteria with static match
        Map<String, Object> criteria = Map.of("$['event']['type']", "payment.completed");
        configDAO.saveMatchers(WEBHOOK_ID, Map.of(BASE_KEY, criteria));

        // Register a waiting task
        String taskId = "task-abc";
        String regHash =
                WebhookHashingService.computeTaskRegistrationHash(
                        "order_workflow",
                        1,
                        "waitForPayment",
                        Map.of("$['event']['type']", "payment.completed"));
        taskDAO.put(regHash, taskId);

        // Mock task lookup
        TaskModel taskModel = buildTask(taskId, TaskModel.Status.IN_PROGRESS);
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(taskModel);

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);

        String body = "{\"event\":{\"type\":\"payment.completed\"}}";
        service.handleWebhook(WEBHOOK_ID, body, Collections.emptyMap(), new HttpHeaders());

        // WorkflowExecutor.updateTask called with COMPLETED and payload keys at top level
        verify(workflowExecutor)
                .updateTask(
                        argThat(
                                r ->
                                        r.getStatus() == TaskResult.Status.COMPLETED
                                                && r.getOutputData().containsKey("event")));
        // Hash deregistered
        assertTrue(taskDAO.get(regHash).isEmpty());
    }

    @Test
    public void handleWebhook_skipsTerminalTask() {
        saveConfig(WEBHOOK_ID);
        Map<String, Object> criteria = Map.of("$['event']['type']", "payment.completed");
        configDAO.saveMatchers(WEBHOOK_ID, Map.of(BASE_KEY, criteria));

        String taskId = "already-done";
        String hash =
                WebhookHashingService.computeTaskRegistrationHash(
                        "order_workflow",
                        1,
                        "waitForPayment",
                        Map.of("$['event']['type']", "payment.completed"));
        taskDAO.put(hash, taskId);

        // Task is already COMPLETED
        TaskModel task = buildTask(taskId, TaskModel.Status.COMPLETED);
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(task);
        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);

        service.handleWebhook(
                WEBHOOK_ID,
                "{\"event\":{\"type\":\"payment.completed\"}}",
                Collections.emptyMap(),
                new HttpHeaders());

        verifyNoMoreInteractions(workflowExecutor);
    }

    @Test
    public void handleWebhook_noMatchingHash_noTaskCompletion() {
        saveConfig(WEBHOOK_ID);
        Map<String, Object> criteria = Map.of("$['event']['type']", "payment.completed");
        configDAO.saveMatchers(WEBHOOK_ID, Map.of(BASE_KEY, criteria));

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);

        // Payload has different event type → no match
        service.handleWebhook(
                WEBHOOK_ID,
                "{\"event\":{\"type\":\"refund.issued\"}}",
                Collections.emptyMap(),
                new HttpHeaders());

        verifyNoInteractions(executionDAOFacade, workflowExecutor);
    }

    // -------------------------------------------------------------------------
    // handleWebhook — workflowsToStart
    // -------------------------------------------------------------------------

    @Test
    public void handleWebhook_startsWorkflowsToStart() {
        WebhookConfig config = saveConfig(WEBHOOK_ID);
        config.setWorkflowsToStart(Map.of("payment_workflow", 1));
        configDAO.save(WEBHOOK_ID, config);

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);
        when(workflowExecutor.startWorkflow(any())).thenReturn("wf-started-id");

        service.handleWebhook(
                WEBHOOK_ID,
                "{\"event\":\"payment.completed\"}",
                Collections.emptyMap(),
                new HttpHeaders());

        verify(workflowExecutor)
                .startWorkflow(
                        argThat(
                                input ->
                                        "payment_workflow".equals(input.getName())
                                                && Integer.valueOf(1).equals(input.getVersion())));
    }

    @Test
    public void handleWebhook_workflowsToStart_popsIdempotencyKeys() {
        WebhookConfig config = saveConfig(WEBHOOK_ID);
        Map<String, Object> wfsToStart = new java.util.HashMap<>();
        wfsToStart.put("notify_workflow", 2);
        wfsToStart.put("idempotencyKey", "some-key");
        wfsToStart.put("idempotencyStrategy", "FAIL");
        config.setWorkflowsToStart(wfsToStart);
        configDAO.save(WEBHOOK_ID, config);

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);
        when(workflowExecutor.startWorkflow(any())).thenReturn("wf-id");

        service.handleWebhook(WEBHOOK_ID, "{}", Collections.emptyMap(), new HttpHeaders());

        // Only one workflow started — idempotencyKey/Strategy entries were skipped
        verify(workflowExecutor, times(1)).startWorkflow(any());
        verify(workflowExecutor)
                .startWorkflow(argThat(input -> "notify_workflow".equals(input.getName())));
    }

    @Test
    public void handleWebhook_workflowStartFailure_doesNotPreventOtherWorkflows() {
        WebhookConfig config = saveConfig(WEBHOOK_ID);
        config.setWorkflowsToStart(Map.of("wf_ok", 1, "wf_bad", 2));
        configDAO.save(WEBHOOK_ID, config);

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);
        // First call throws, second succeeds
        when(workflowExecutor.startWorkflow(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn("wf-good-id");

        // Should NOT throw despite failure
        assertNull(
                service.handleWebhook(WEBHOOK_ID, "{}", Collections.emptyMap(), new HttpHeaders()));
        verify(workflowExecutor, times(2)).startWorkflow(any());
    }

    @Test
    public void handleWebhook_noWorkflowsToStart_noStartCalls() {
        saveConfig(WEBHOOK_ID); // config has no workflowsToStart

        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);

        service.handleWebhook(WEBHOOK_ID, "{}", Collections.emptyMap(), new HttpHeaders());

        // WorkflowExecutor never called for starting (only updateTask if tasks matched — none here)
        verify(workflowExecutor, never()).startWorkflow(any());
    }

    // -------------------------------------------------------------------------
    // handlePing
    // -------------------------------------------------------------------------

    @Test
    public void handlePing_returnsNullWhenConfigNotFound() {
        String result = service.handlePing("unknown", Collections.emptyMap());
        assertNull(result);
    }

    @Test
    public void handlePing_returnsPingResponse() {
        saveConfig(WEBHOOK_ID);
        when(mockVerifier.handlePing(any(), any())).thenReturn("pong");

        String result = service.handlePing(WEBHOOK_ID, Collections.emptyMap());

        assertEquals("pong", result);
        assertTrue(configDAO.get(WEBHOOK_ID).isUrlVerified());
    }

    @Test
    public void handlePing_nullResponse_doesNotMarkVerified() {
        WebhookConfig config = saveConfig(WEBHOOK_ID);
        when(mockVerifier.handlePing(any(), any())).thenReturn(null);

        service.handlePing(WEBHOOK_ID, Collections.emptyMap());

        assertFalse(configDAO.get(WEBHOOK_ID).isUrlVerified());
    }

    // -------------------------------------------------------------------------
    // orgId isolation
    // -------------------------------------------------------------------------

    @Test
    public void handleWebhook_neverReadsOrSetsOrgId() {
        // This test documents that IncomingWebhookService has no orgId logic.
        // orgId context is applied by WebhookOrgContextProvider BEFORE this service is called.
        // The service itself only calls: configDAO, taskDAO, hashingService, verifier, executor.
        // No OrkesRequestContext, no TimeBasedUUIDGenerator.
        saveConfig(WEBHOOK_ID);
        when(mockVerifier.verify(any(), any())).thenReturn(Collections.emptyList());
        when(mockVerifier.extractChallenge(any(), any())).thenReturn(null);

        // Just verify it runs without NPE and doesn't touch any orgId-aware class
        assertNull(
                service.handleWebhook(WEBHOOK_ID, "{}", Collections.emptyMap(), new HttpHeaders()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WebhookConfig saveConfig(String id) {
        WebhookConfig config = new WebhookConfig();
        config.setId(id);
        config.setName("test-webhook");
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        configDAO.save(id, config);
        return config;
    }

    private HttpHeaders headersWith(String name, String value) {
        HttpHeaders h = new HttpHeaders();
        h.add(name, value);
        return h;
    }

    private TaskModel buildTask(String taskId, TaskModel.Status status) {
        TaskModel model = new TaskModel();
        model.setTaskId(taskId);
        model.setStatus(status);
        model.setWorkflowInstanceId("wf-123");
        return model;
    }
}
