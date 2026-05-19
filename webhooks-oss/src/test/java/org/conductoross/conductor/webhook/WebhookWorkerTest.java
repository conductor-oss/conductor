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

import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookWorkerTest {

    @Mock private QueueDAO queueDAO;
    @Mock private WebhookDAO webhookDAO;
    @Mock private WebhookHashingService webhookHashingService;
    @Mock private WebhookTaskService webhookTaskService;
    @Mock private WorkflowExecutor workflowExecutor;
    @Mock private ExecutionDAOFacade executionDAOFacade;
    @Mock private TargetWorkflowCollector targetWorkflowCollector;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebhookWorker worker;

    @BeforeEach
    void setUp() {
        WebhookWorkerProperties properties = new WebhookWorkerProperties();
        properties.setThreadCount(0); // disable polling thread
        worker =
                new WebhookWorker(
                        objectMapper,
                        queueDAO,
                        webhookDAO,
                        properties,
                        webhookHashingService,
                        workflowExecutor,
                        webhookTaskService,
                        executionDAOFacade,
                        targetWorkflowCollector);
    }

    @Test
    void handleMessage_eventNotFound_returnsEarly() {
        when(webhookDAO.getWebhookEvent("missing")).thenReturn(null);

        worker.handleMessage("missing");

        verify(webhookDAO, never()).getWebhook(anyString());
        verify(workflowExecutor, never()).startWorkflow(any(StartWorkflowInput.class));
    }

    @Test
    void handleMessage_configNotFound_returnsEarly() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-missing")
                        .body("{}")
                        .build();
        when(webhookDAO.getWebhookEvent("ev-1")).thenReturn(event);
        when(webhookDAO.getWebhook("hook-missing")).thenReturn(null);

        worker.handleMessage("ev-1");

        verify(workflowExecutor, never()).startWorkflow(any(StartWorkflowInput.class));
        verify(webhookDAO, never()).removeWebhookEvent(anyString());
    }

    @Test
    void handleMessage_workflowsToStart_invokesExecutor() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{\"foo\":\"bar\"}")
                        .requestParams(Map.of())
                        .build();
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");
        config.setCreatedBy("alice");

        when(webhookDAO.getWebhookEvent("ev-1")).thenReturn(event);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);
        when(webhookDAO.getMatchers("hook-1")).thenReturn(Map.of());
        when(targetWorkflowCollector.getWorkflowsToStart()).thenReturn(Map.of("wf-a", 1));
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("wf-id");

        worker.handleMessage("ev-1");

        ArgumentCaptor<StartWorkflowInput> captor =
                ArgumentCaptor.forClass(StartWorkflowInput.class);
        verify(workflowExecutor).startWorkflow(captor.capture());
        StartWorkflowInput input = captor.getValue();
        assertThat(input.getName()).isEqualTo("wf-a");
        assertThat(input.getVersion()).isEqualTo(1);
        assertThat(input.getWorkflowInput()).containsEntry("foo", "bar");
        assertThat(input.getEvent()).isEqualTo("my-hook:ev-1");
        verify(webhookDAO).removeWebhookEvent("ev-1");
    }

    @Test
    void handleMessage_workflowsToStart_nonIntegerVersion_skipped() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{}")
                        .requestParams(Map.of())
                        .build();
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");

        when(webhookDAO.getWebhookEvent("ev-1")).thenReturn(event);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);
        when(webhookDAO.getMatchers("hook-1")).thenReturn(Map.of());
        when(targetWorkflowCollector.getWorkflowsToStart())
                .thenReturn(Map.of("wf-a", "not-a-number"));

        worker.handleMessage("ev-1");

        verify(workflowExecutor, never()).startWorkflow(any(StartWorkflowInput.class));
    }

    @Test
    void handleMessage_matcherHit_completesWaitingTask() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{}")
                        .requestParams(Map.of())
                        .build();
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");

        when(webhookDAO.getWebhookEvent("ev-1")).thenReturn(event);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);
        when(webhookDAO.getMatchers("hook-1"))
                .thenReturn(Map.of("wf-a;1;wait_ref", Map.of("event", "push")));
        when(webhookHashingService.computeJsonHash(any(), any(), anyString(), any()))
                .thenReturn("hash-abc");
        when(webhookTaskService.get("hash-abc")).thenReturn(Set.of("task-1"));

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task-1");
        taskModel.setStatus(TaskModel.Status.IN_PROGRESS);
        taskModel.setWorkflowInstanceId("wf-instance-1");
        taskModel.setTaskType("WAIT_FOR_WEBHOOK");
        when(executionDAOFacade.getTaskModel("task-1")).thenReturn(taskModel);

        worker.handleMessage("ev-1");

        ArgumentCaptor<TaskResult> resultCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus()).isEqualTo(TaskResult.Status.COMPLETED);
        verify(webhookTaskService).remove("hash-abc", "task-1");
    }

    @Test
    void handleMessage_matcherHit_terminalTask_skipped() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{}")
                        .requestParams(Map.of())
                        .build();
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");

        when(webhookDAO.getWebhookEvent("ev-1")).thenReturn(event);
        when(webhookDAO.getWebhook("hook-1")).thenReturn(config);
        when(webhookDAO.getMatchers("hook-1")).thenReturn(Map.of("k", Map.of("event", "push")));
        when(webhookHashingService.computeJsonHash(any(), any(), anyString(), any()))
                .thenReturn("hash");
        when(webhookTaskService.get("hash")).thenReturn(Set.of("task-done"));

        TaskModel done = new TaskModel();
        done.setTaskId("task-done");
        done.setStatus(TaskModel.Status.COMPLETED);
        when(executionDAOFacade.getTaskModel("task-done")).thenReturn(done);

        worker.handleMessage("ev-1");

        verify(workflowExecutor, never()).updateTask(any(TaskResult.class));
        verify(webhookTaskService, never()).remove(anyString(), eq("task-done"));
    }
}
