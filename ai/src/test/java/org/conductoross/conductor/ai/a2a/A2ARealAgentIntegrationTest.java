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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.tasks.worker.A2AWorkers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import okhttp3.OkHttpClient;

import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.invoke;
import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.unusedAgentClient;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in integration test against a REAL, externally running A2A agent (e.g. an agent from {@code
 * a2aproject/a2a-samples}). Skipped unless {@code A2A_AGENT_URL} is set.
 *
 * <p>Example (run the helloworld sample, then):
 *
 * <pre>
 *   A2A_AGENT_URL=http://localhost:9999 \
 *   ./gradlew :conductor-ai:test --tests '*A2ARealAgentIntegrationTest'
 * </pre>
 *
 * Optional: {@code A2A_AGENT_PROMPT} (default "hello") and {@code A2A_AGENT_TOKEN} (sent as a
 * Bearer Authorization header). See {@code ai/src/test/resources/a2a/README.md}.
 */
@EnabledIfEnvironmentVariable(named = "A2A_AGENT_URL", matches = ".+")
class A2ARealAgentIntegrationTest {

    private A2AService service() {
        OkHttpClient client =
                new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .build();
        return new A2AService(client);
    }

    private Map<String, String> headers() {
        String token = System.getenv("A2A_AGENT_TOKEN");
        return token == null || token.isBlank() ? null : Map.of("Authorization", "Bearer " + token);
    }

    @Test
    void discoversRealAgentCard() {
        String url = System.getenv("A2A_AGENT_URL");
        AgentCard card = service().getAgentCard(url, headers());
        assertNotNull(card.getName(), "agent card should expose a name");
        System.out.println("A2A agent: " + card.getName() + " — skills=" + card.getSkills());
    }

    @Test
    void callsRealAgentToTerminalState() {
        String url = System.getenv("A2A_AGENT_URL");
        String prompt = System.getenv().getOrDefault("A2A_AGENT_PROMPT", "hello");

        A2AWorkers workers = new A2AWorkers(service(), List.of(unusedAgentClient()));
        Task task = new Task();
        task.setTaskId("it-task-1");
        task.setStatus(Task.Status.SCHEDULED);
        task.setOutputData(new java.util.HashMap<>());
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("agentUrl", url);
        input.put("text", prompt);
        if (headers() != null) {
            input.put("headers", headers());
        }
        task.setInputData(input);

        TaskResult result = invoke(workers, task);

        int guard = 0;
        while (result.getStatus() == TaskResult.Status.IN_PROGRESS && guard++ < 60) {
            result = invoke(workers, task);
        }

        assertTrue(
                result.getStatus() != TaskResult.Status.IN_PROGRESS,
                "expected a terminal status, got " + result.getStatus());
        System.out.println(
                "A2A call result: status="
                        + result.getStatus()
                        + " output="
                        + result.getOutputData());
    }
}
