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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.EmbeddedA2AAgent.SendMode;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.models.A2AAgentCardRequest;
import org.conductoross.conductor.ai.tasks.worker.A2AWorkers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.netflix.conductor.model.TaskModel;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * End-to-end tests against a real, embedded A2A agent ({@link EmbeddedA2AAgent}) over loopback HTTP
 * — exercising discovery, send, polling, streaming, and cancellation through the actual wire
 * protocol and the real {@link A2AService}/{@link AgentTask} logic (no mocks on the A2A path).
 */
class A2AEndToEndTest {

    private EmbeddedA2AAgent agent;
    private A2AService service;
    private AgentTask callAgentTask;

    @BeforeEach
    void setUp() throws Exception {
        agent = new EmbeddedA2AAgent();
        OkHttpClient client =
                new OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(3, TimeUnit.SECONDS)
                        .build();
        // Spy to bypass SSRF check for loopback — embedded agent uses 127.0.0.1.
        service = spy(new A2AService(client));
        doNothing().when(service).validateAgentUrl(anyString());
        Environment environment = mock(Environment.class);
        callAgentTask = new AgentTask(service, environment);
    }

    @AfterEach
    void tearDown() {
        agent.close();
    }

    private TaskModel taskModel(Map<String, Object> input) {
        TaskModel model = new TaskModel();
        model.setInputData(input);
        model.setTaskId("conductor-task-1");
        return model;
    }

    @Test
    void discovery_resolvesRealAgentCard() {
        AgentCard card = service.getAgentCard(agent.url(), null);
        assertEquals("Embedded Agent", card.getName());
        assertTrue(card.getCapabilities().isStreaming());
        assertEquals("echo", card.getSkills().get(0).getId());
    }

    @Test
    void getAgentCardWorker_resolvesRealAgentCard() {
        A2AWorkers workers = new A2AWorkers(service);
        A2AAgentCardRequest request = new A2AAgentCardRequest();
        request.setAgentUrl(agent.url());

        AgentCard card = workers.getAgentCard(request);

        assertEquals("Embedded Agent", card.getName());
    }

    @Test
    void callAgent_immediateCompletion() {
        agent.sendMode(SendMode.TASK_COMPLETED).text("42");

        TaskModel task = taskModel(Map.of("agentUrl", agent.url(), "text", "convert"));
        callAgentTask.start(null, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("42", task.getOutputData().get("text"));
    }

    @Test
    void callAgent_directMessageReply() {
        agent.sendMode(SendMode.MESSAGE).text("hi there");

        TaskModel task = taskModel(Map.of("agentUrl", agent.url(), "text", "hello"));
        callAgentTask.start(null, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("message", task.getOutputData().get("state"));
        assertEquals("hi there", task.getOutputData().get("text"));
    }

    @Test
    void callAgent_pollsLongRunningTaskToCompletion() {
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(2).text("done");

        TaskModel task = taskModel(Map.of("agentUrl", agent.url(), "text", "convert"));
        callAgentTask.start(null, task, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());

        // Simulate the engine's poll loop.
        int guard = 0;
        while (task.getStatus() == TaskModel.Status.IN_PROGRESS && guard++ < 20) {
            callAgentTask.execute(null, task, null);
        }

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("done", task.getOutputData().get("text"));
        assertTrue(agent.getCalls() >= 2, "expected the agent to be polled");
    }

    @Test
    void callAgent_inputRequiredCompletesWithQuestion() {
        agent.sendMode(SendMode.INPUT_REQUIRED);

        TaskModel task = taskModel(Map.of("agentUrl", agent.url(), "text", "convert"));
        callAgentTask.start(null, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("input-required", task.getOutputData().get("state"));
        assertEquals(EmbeddedA2AAgent.AGENT_TASK_ID, task.getOutputData().get("taskId"));
        assertEquals("Which currency?", task.getOutputData().get("text"));
    }

    @Test
    void callAgent_streamingAggregatesChunks() {
        agent.sendMode(SendMode.STREAM);

        TaskModel task =
                taskModel(Map.of("agentUrl", agent.url(), "text", "convert", "streaming", true));
        callAgentTask.start(null, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        String text = (String) task.getOutputData().get("text");
        assertTrue(text.contains("Hello"), text);
        assertTrue(text.contains("world"), text);
    }

    @Test
    void cancel_propagatesToRealAgent() {
        agent.sendMode(SendMode.TASK_WORKING);

        TaskModel task = taskModel(Map.of("agentUrl", agent.url(), "text", "x"));
        callAgentTask.start(null, task, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());

        callAgentTask.cancel(null, task, null);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        assertEquals(1, agent.cancelCalls());
    }
}
