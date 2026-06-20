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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.EmbeddedA2AAgent.SendMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.service.TaskService;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the push-notification receiver end-to-end against a real embedded agent: on a valid push it
 * fetches authoritative state via {@code tasks/get} and completes the waiting task through {@link
 * TaskService} (which is mocked, standing in for the engine).
 *
 * <p>SSRF validation is stubbed out because the embedded agent uses loopback (127.0.0.1); this is
 * intentional and correct — production deployments must never disable it.
 */
class A2ACallbackResourceTest {

    private EmbeddedA2AAgent agent;
    private TaskService taskService;
    private A2ACallbackResource resource;

    @BeforeEach
    void setUp() throws Exception {
        agent = new EmbeddedA2AAgent();
        OkHttpClient client =
                new OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
        // Spy to bypass SSRF check for loopback — the embedded agent uses 127.0.0.1.
        A2AService service = spy(new A2AService(client));
        doNothing().when(service).validateAgentUrl(anyString());
        taskService = mock(TaskService.class);
        resource = new A2ACallbackResource(taskService, service);
    }

    @AfterEach
    void tearDown() {
        agent.close();
    }

    private Task waitingTask(String token) {
        Task task = new Task();
        task.setTaskId("conductor-task-1");
        task.setTaskType(CallAgentTask.TASK_TYPE);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setWorkflowInstanceId("wf-1");
        task.setReferenceTaskName("agentRef");
        Map<String, Object> input = new HashMap<>();
        input.put("agentUrl", agent.url());
        task.setInputData(input);
        Map<String, Object> output = new HashMap<>();
        output.put(A2AResults.KEY_PUSH_TOKEN, token);
        output.put(A2AResults.KEY_TASK_ID, EmbeddedA2AAgent.AGENT_TASK_ID);
        task.setOutputData(output);
        return task;
    }

    @Test
    void push_validToken_completesTask() {
        agent.sendMode(SendMode.TASK_COMPLETED).text("pushed");
        when(taskService.getTask("conductor-task-1")).thenReturn(waitingTask("tok-123"));

        // Send token via Bearer Authorization header (preferred path)
        ResponseEntity<Void> response =
                resource.onPushNotification("conductor-task-1", "Bearer tok-123", null, null, null);

        assertEquals(200, response.getStatusCode().value());
        verify(taskService)
                .updateTask(
                        eq("wf-1"),
                        eq("agentRef"),
                        eq(TaskResult.Status.COMPLETED),
                        eq("a2a-callback"),
                        anyMap());
    }

    @Test
    void push_customHeader_completesTask() {
        agent.sendMode(SendMode.TASK_COMPLETED).text("pushed");
        when(taskService.getTask("conductor-task-1")).thenReturn(waitingTask("tok-123"));

        // Send token via custom header (fallback path)
        ResponseEntity<Void> response =
                resource.onPushNotification("conductor-task-1", null, "tok-123", null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void push_queryParam_completesTask() {
        agent.sendMode(SendMode.TASK_COMPLETED).text("pushed");
        when(taskService.getTask("conductor-task-1")).thenReturn(waitingTask("tok-123"));

        // Send token via query param (deprecated, but backward-compatible)
        ResponseEntity<Void> response =
                resource.onPushNotification("conductor-task-1", null, null, "tok-123", null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void push_tokenMismatch_isForbidden() {
        when(taskService.getTask("conductor-task-1")).thenReturn(waitingTask("tok-123"));

        ResponseEntity<Void> response =
                resource.onPushNotification(
                        "conductor-task-1", "Bearer wrong-token", null, null, null);

        assertEquals(403, response.getStatusCode().value());
        verify(taskService, never()).updateTask(any(), any(), any(), any(), anyMap());
    }

    @Test
    void push_expiredToken_isForbidden() {
        // Token with an expiry in the past
        String expiredToken =
                "550e8400-dead-41d4-a716-446655440000:" + (System.currentTimeMillis() - 1000);
        when(taskService.getTask("conductor-task-1")).thenReturn(waitingTask(expiredToken));

        ResponseEntity<Void> response =
                resource.onPushNotification(
                        "conductor-task-1", "Bearer " + expiredToken, null, null, null);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void push_unknownTask_isNotFound() {
        when(taskService.getTask("missing")).thenReturn(null);

        ResponseEntity<Void> response =
                resource.onPushNotification("missing", "Bearer tok", null, null, null);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void push_remoteStillWorking_acksWithoutCompleting() {
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(5);
        when(taskService.getTask("conductor-task-1")).thenReturn(waitingTask("tok-123"));

        ResponseEntity<Void> response =
                resource.onPushNotification("conductor-task-1", "Bearer tok-123", null, null, null);

        assertEquals(200, response.getStatusCode().value());
        verify(taskService, never()).updateTask(any(), any(), any(), any(), anyMap());
    }
}
