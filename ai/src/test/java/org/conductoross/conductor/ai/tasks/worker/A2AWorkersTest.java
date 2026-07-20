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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.A2AService.SendResult;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.model.A2AAgentCardRequest;
import org.conductoross.conductor.ai.model.A2AAgentCardResult;
import org.conductoross.conductor.ai.model.A2ACallRequest;
import org.conductoross.conductor.ai.model.A2ACallResult;
import org.conductoross.conductor.ai.model.A2ACancelRequest;
import org.conductoross.conductor.ai.model.A2ACancelResult;
import org.conductoross.conductor.core.execution.tasks.annotated.AnnotatedWorkflowSystemTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.unusedAgentClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class A2AWorkersTest {

    private A2AService a2aService;
    private A2AWorkers workers;

    @BeforeEach
    void setUp() {
        a2aService = mock(A2AService.class);
        workers = new A2AWorkers(a2aService, unusedAgentClient());
    }

    @Test
    void getAgentCard_returnsCard() {
        AgentCard card = new AgentCard();
        card.setName("Currency Agent");
        when(a2aService.getAgentCard(eq("http://agent"), any())).thenReturn(card);

        A2AAgentCardRequest request = new A2AAgentCardRequest();
        request.setAgentUrl("http://agent");

        A2AAgentCardResult result = workers.getAgentCard(request);

        assertEquals("Currency Agent", result.getAgentCard().getName());
    }

    @Test
    void getAgentCard_missingAgentUrl_isNonRetryable() {
        assertThrows(
                NonRetryableException.class, () -> workers.getAgentCard(new A2AAgentCardRequest()));
    }

    @Test
    void lazyAgentClientInjectionBreaksSpringDependencyCycle() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "a2a-workers-test",
                                    Map.of("conductor.integrations.ai.enabled", "true")));
            context.register(A2AWorkers.class, CircularAgentClientConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(A2AWorkers.class));
            assertNotNull(context.getBean(ConductorAgentClient.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CircularAgentClientConfiguration {

        @Bean
        A2AService a2aService() {
            return mock(A2AService.class);
        }

        @Bean
        ConductorAgentClient agentClient(A2AWorkers workers) {
            return unusedAgentClient();
        }
    }

    @Test
    void annotatedWorkerMethodsUseTypedInputAndOutputContracts() throws Exception {
        Method getAgentCard = A2AWorkers.class.getMethod("getAgentCard", A2AAgentCardRequest.class);
        Method agent = A2AWorkers.class.getMethod("agent", A2ACallRequest.class);
        Method cancelAgent = A2AWorkers.class.getMethod("cancelAgent", A2ACancelRequest.class);

        assertEquals(A2AAgentCardResult.class, getAgentCard.getReturnType());
        assertEquals(A2ACallResult.class, agent.getReturnType());
        assertEquals(A2ACancelResult.class, cancelAgent.getReturnType());
        assertFalse(TaskResult.class.isAssignableFrom(A2AAgentCardResult.class));
        assertFalse(TaskResult.class.isAssignableFrom(A2ACallResult.class));
        assertFalse(TaskResult.class.isAssignableFrom(A2ACancelResult.class));
    }

    @Test
    void annotatedAgentHonorsConfiguredPollInterval() throws Exception {
        A2ATask remoteTask = new A2ATask();
        remoteTask.setId("remote-task-1");
        TaskStatus status = new TaskStatus();
        status.setState(TaskState.WORKING);
        remoteTask.setStatus(status);
        when(a2aService.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(remoteTask));

        Method method = A2AWorkers.class.getMethod("agent", A2ACallRequest.class);
        WorkerTask annotation = method.getAnnotation(WorkerTask.class);
        AnnotatedWorkflowSystemTask systemTask =
                new AnnotatedWorkflowSystemTask(A2AWorkers.AGENT, method, workers, annotation);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setWorkflowInstanceId("workflow-1");
        task.setReferenceTaskName("agent_ref");
        task.setTaskType(A2AWorkers.AGENT);
        task.setInputData(
                new HashMap<>(
                        Map.of(
                                "agentUrl",
                                "http://agent",
                                "text",
                                "hello",
                                "pollIntervalSeconds",
                                17)));
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        systemTask.start(workflow, task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(17, task.getCallbackAfterSeconds());
        assertEquals(17L, systemTask.getEvaluationOffset(task, 60).orElseThrow());
    }
}
