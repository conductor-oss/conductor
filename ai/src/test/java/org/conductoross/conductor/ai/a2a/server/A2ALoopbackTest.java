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
package org.conductoross.conductor.ai.a2a.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.AgentTask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The capstone: <b>Conductor calling Conductor over A2A.</b> The real {@link AgentTask} client
 * drives the real {@link A2AServerResource} over real HTTP (random port) through the full A2A
 * round-trip — discovery, message/send → start workflow, tasks/get → poll to completion — with a
 * stateful fake engine standing in for the persistence layer. Also proves the client's
 * deterministic {@code messageId} arrives as the server's workflow idempotency key.
 */
@SpringBootTest(
        classes = A2ALoopbackTest.LoopbackApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "conductor.a2a.server.enabled=true",
            "conductor.a2a.server.exposed-workflows=order_pizza"
        })
class A2ALoopbackTest {

    @LocalServerPort private int port;
    @Autowired private WorkflowService workflowService; // the fake bean below

    @SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
    static class LoopbackApp {

        @Bean
        WorkflowService workflowService() {
            WorkflowService service = mock(WorkflowService.class);
            when(service.startWorkflow(any(StartWorkflowRequest.class))).thenReturn("wf-loop-1");
            AtomicInteger polls = new AtomicInteger();
            when(service.getExecutionStatus(eq("wf-loop-1"), anyBoolean()))
                    .thenAnswer(
                            inv -> {
                                Workflow wf = new Workflow();
                                wf.setWorkflowId("wf-loop-1");
                                wf.setCorrelationId("ctx-loop");
                                wf.setWorkflowDefinition(def());
                                // First poll RUNNING, then COMPLETED — simulates progress.
                                if (polls.getAndIncrement() == 0) {
                                    wf.setStatus(WorkflowStatus.RUNNING);
                                } else {
                                    wf.setStatus(WorkflowStatus.COMPLETED);
                                    wf.setOutput(Map.of("orderId", "ORD-99"));
                                }
                                return wf;
                            });
            return service;
        }

        @Bean
        TaskService taskService() {
            return mock(TaskService.class);
        }

        @Bean
        MetadataService metadataService() {
            MetadataService service = mock(MetadataService.class);
            when(service.getWorkflowDef(eq("order_pizza"), any())).thenReturn(def());
            when(service.getWorkflowDefsLatestVersions()).thenReturn(List.of(def()));
            return service;
        }

        private static WorkflowDef def() {
            WorkflowDef def = new WorkflowDef();
            def.setName("order_pizza");
            def.setVersion(1);
            def.setDescription("Order a pizza");
            return def;
        }
    }

    private A2AService clientService() {
        A2AService service =
                spy(
                        new A2AService(
                                new OkHttpClient.Builder()
                                        .connectTimeout(3, TimeUnit.SECONDS)
                                        .readTimeout(5, TimeUnit.SECONDS)
                                        .build()));
        doNothing().when(service).validateAgentUrl(anyString()); // loopback is fine in tests
        return service;
    }

    private String agentUrl() {
        return "http://localhost:" + port + "/a2a/order_pizza";
    }

    @Test
    void discovery_clientResolvesServerAgentCard() {
        AgentCard card = clientService().getAgentCard(agentUrl(), null);

        assertEquals("order_pizza", card.getName());
        assertEquals(1, card.getSkills().size());
        assertEquals("order_pizza", card.getSkills().get(0).getId());
        assertEquals(agentUrl(), card.getUrl());
    }

    @Test
    void fullRoundTrip_clientTaskDrivesServerWorkflowToCompletion() {
        A2AService service = clientService();
        AgentTask client = new AgentTask(service, mock(Environment.class));

        TaskModel model = new TaskModel();
        model.setTaskId("client-task-1");
        model.setWorkflowInstanceId("client-wf");
        model.setReferenceTaskName("callPizza");
        model.setInputData(Map.of("agentUrl", agentUrl(), "text", "one large pepperoni"));

        // message/send → server starts the workflow → RUNNING → client task IN_PROGRESS.
        client.start(null, model, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());
        assertEquals("wf-loop-1", model.getOutputData().get("taskId"));

        // tasks/get poll loop → server flips to COMPLETED → client task COMPLETED.
        int guard = 0;
        while (model.getStatus() == TaskModel.Status.IN_PROGRESS && guard++ < 20) {
            client.execute(null, model, null);
        }
        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("completed", model.getOutputData().get("state"));
        assertNotNull(model.getOutputData().get("artifacts"));

        // The client's deterministic messageId crossed the wire and became the server's
        // idempotency key (server-side effectively-once).
        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(workflowService).startWorkflow(captor.capture());
        // Client's deterministic messageId, namespaced by the server with the workflow name.
        assertEquals(
                "order_pizza:a2a-client-wf:callPizza:0", captor.getValue().getIdempotencyKey());
    }
}
