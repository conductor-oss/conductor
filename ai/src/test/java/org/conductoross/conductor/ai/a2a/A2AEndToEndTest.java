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
import org.conductoross.conductor.ai.model.A2AAgentCardRequest;
import org.conductoross.conductor.ai.tasks.worker.A2AWorkers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import okhttp3.OkHttpClient;

import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.invoke;
import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

/**
 * End-to-end tests against a real, embedded A2A agent ({@link EmbeddedA2AAgent}) over loopback HTTP
 * — exercising discovery, send, polling, streaming, and cancellation through the actual wire
 * protocol and the annotation-backed {@link A2AWorkers} logic (no mocks on the A2A path).
 */
class A2AEndToEndTest {

    private EmbeddedA2AAgent agent;
    private A2AService service;
    private A2AWorkers workers;

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
        workers = new A2AWorkers(service);
    }

    @AfterEach
    void tearDown() {
        agent.close();
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
        A2AAgentCardRequest request = new A2AAgentCardRequest();
        request.setAgentUrl(agent.url());

        AgentCard card = workers.getAgentCard(request).getAgentCard();

        assertEquals("Embedded Agent", card.getName());
    }

    @Test
    void callAgent_immediateCompletion() {
        agent.sendMode(SendMode.TASK_COMPLETED).text("42");

        TaskResult result =
                invoke(workers, task(Map.of("agentUrl", agent.url(), "text", "convert")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("42", result.getOutputData().get("text"));
    }

    @Test
    void callAgent_directMessageReply() {
        agent.sendMode(SendMode.MESSAGE).text("hi there");

        TaskResult result = invoke(workers, task(Map.of("agentUrl", agent.url(), "text", "hello")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("message", result.getOutputData().get("state"));
        assertEquals("hi there", result.getOutputData().get("text"));
    }

    @Test
    void callAgent_pollsLongRunningTaskToCompletion() {
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(2).text("done");

        Task task = task(Map.of("agentUrl", agent.url(), "text", "convert"));
        TaskResult result = invoke(workers, task);
        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());

        // Simulate the engine's poll loop.
        int guard = 0;
        while (result.getStatus() == TaskResult.Status.IN_PROGRESS && guard++ < 20) {
            result = invoke(workers, task);
        }

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("done", result.getOutputData().get("text"));
        assertTrue(agent.getCalls() >= 2, "expected the agent to be polled");
    }

    @Test
    void callAgent_inputRequiredCompletesWithQuestion() {
        agent.sendMode(SendMode.INPUT_REQUIRED);

        TaskResult result =
                invoke(workers, task(Map.of("agentUrl", agent.url(), "text", "convert")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("input-required", result.getOutputData().get("state"));
        assertEquals(EmbeddedA2AAgent.AGENT_TASK_ID, result.getOutputData().get("taskId"));
        assertEquals("Which currency?", result.getOutputData().get("text"));
    }

    @Test
    void callAgent_streamingAggregatesChunks() {
        agent.sendMode(SendMode.STREAM);

        TaskResult result =
                invoke(
                        workers,
                        task(
                                Map.of(
                                        "agentUrl",
                                        agent.url(),
                                        "text",
                                        "convert",
                                        "streaming",
                                        true)));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        String text = (String) result.getOutputData().get("text");
        assertTrue(text.contains("Hello"), text);
        assertTrue(text.contains("world"), text);
    }

    @Test
    void cancel_propagatesToRealAgent() {
        agent.sendMode(SendMode.TASK_WORKING);

        Task task = task(Map.of("agentUrl", agent.url(), "text", "x"));
        TaskResult result = invoke(workers, task);
        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());

        workers.cancel(A2AWorkers.AGENT, task, "workflow canceled");

        assertEquals(1, agent.cancelCalls());
    }
}
