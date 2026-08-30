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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.conductoross.conductor.ai.agent.ConductorAgentDelegate;
import org.conductoross.conductor.ai.agent.tools.AgentToolDispatch;
import org.conductoross.conductor.ai.agent.tools.AgentToolDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A function tool, all the way round: Foundry asks for one, Conductor schedules it, the result goes
 * back, and the agent finishes.
 *
 * <p>Every other test covers one side of that. The client's tests use a fake server and never build
 * a task; the delegate's tests use a fake client and never build a request. Both passed while the
 * two halves disagreed about how an agent is located and what a function call even is, so the loop
 * is asserted here through the real client and the real delegate together.
 */
class AzureFoundryToolLoopTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer azure;
    private AzureFoundryAgentClient client;
    private final List<String> bodies = new ArrayList<>();
    private final AtomicBoolean toolsAnswered = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws Exception {
        azure = new MockWebServer();
        azure.setDispatcher(new FoundryDispatcher());
        azure.start();
        client =
                new AzureFoundryAgentClient(
                        new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                        Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws Exception {
        azure.shutdown();
    }

    @Test
    void afunctionToolIsScheduledAsATaskAndItsResultFinishesTheAgent() throws Exception {
        FakeDispatcher dispatcher = new FakeDispatcher();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);
        Task task = agentTask();

        // Turn one: Foundry asks for get_revenue, so the agent task stays in progress while
        // Conductor runs it. This is the step that makes a tool call a task at all.
        TaskResult first = delegate.execute(task);

        assertThat(first.getStatus()).isEqualTo(TaskResult.Status.IN_PROGRESS);
        assertThat(dispatcher.dispatched).isNotNull();
        assertThat(dispatcher.dispatched.toolCalls()).hasSize(1);
        assertThat(dispatcher.dispatched.toolCalls().get(0))
                .containsEntry("tool_name", "get_revenue")
                .containsEntry("tool_call_id", "call_1");
        assertThat(first.getOutputData()).containsKey("toolDispatchId");

        // Turn two: the tool has run. Its output has to reach Foundry keyed by the call it answers.
        dispatcher.state =
                AgentToolDispatch.completed(
                        dispatcher.dispatched.parentWorkflowId(),
                        Map.of("call_1", Map.of("revenue", "4.2M")));
        toolsAnswered.set(true);
        task.setOutputData(first.getOutputData());

        TaskResult second = delegate.execute(task);

        assertThat(second.getStatus()).isEqualTo(TaskResult.Status.COMPLETED);
        assertThat(second.getOutputData().get("text")).isEqualTo("Revenue was 4.2M.");
        // The batch is finished, so nothing still advertises outstanding work.
        assertThat(second.getOutputData()).doesNotContainKey("toolDispatchId");
        assertThat(second.getOutputData()).doesNotContainKey("pendingTools");

        JsonNode submitted = MAPPER.readTree(bodies.get(bodies.size() - 1));
        JsonNode item = submitted.path("input").get(0);
        assertThat(item.path("type").asText()).isEqualTo("function_call_output");
        assertThat(item.path("call_id").asText()).isEqualTo("call_1");
        assertThat(item.path("output").asText()).contains("4.2M");
        // Chained off the turn that asked, so Foundry knows which question is being answered.
        assertThat(submitted.path("previous_response_id").asText()).isEqualTo("resp-1");
    }

    @Test
    void withoutAutoRunToolsTheCallIsHandedBackInsteadOfScheduled() {
        FakeDispatcher dispatcher = new FakeDispatcher();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);
        Task task = agentTask();
        task.getInputData().put("autoRunTools", false);

        TaskResult result = delegate.execute(task);

        // The workflow runs the tool itself, so the task finishes and says what is outstanding.
        assertThat(result.getStatus()).isEqualTo(TaskResult.Status.COMPLETED);
        assertThat(result.getOutputData().get("waiting")).isEqualTo(true);
        assertThat(result.getOutputData()).containsKey("pendingTools");
        assertThat(dispatcher.dispatched).isNull();
    }

    private Task agentTask() {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setWorkflowInstanceId("wf-1");
        task.setReferenceTaskName("agent_ref");
        task.setStatus(Task.Status.IN_PROGRESS);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("agentType", "microsoft-foundry");
        input.put("prompt", "what was Q3 revenue?");
        input.put("autoRunTools", true);
        // Named the way a workflow written against the docs names it: the agent's URL, nothing
        // else.
        input.put("agentUrl", azure.url("/api/projects/p1/agents/analyst").toString());
        input.put("rawConfig", Map.of("surface", "responses"));
        input.put("credentials", Map.of("apiKey", "azure-api-key"));
        task.setInputData(input);
        task.setOutputData(new LinkedHashMap<>());
        return task;
    }

    private static final class FakeDispatcher implements AgentToolDispatcher {
        private AgentToolDispatcher.Request dispatched;
        private AgentToolDispatch state;

        @Override
        public AgentToolDispatch dispatch(AgentToolDispatcher.Request request) {
            dispatched = request;
            state = AgentToolDispatch.running(request.parentWorkflowId());
            return state;
        }

        @Override
        public AgentToolDispatch status(String dispatchId) {
            return state;
        }

        @Override
        public void cancel(String dispatchId) {}
    }

    private final class FoundryDispatcher extends Dispatcher {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            bodies.add(request.getBody().readUtf8());
            if (toolsAnswered.get()) {
                return json(
                        """
                        {"id":"resp-2","status":"completed","output":[
                           {"type":"message","content":[
                              {"type":"output_text","text":"Revenue was 4.2M."}]}]}""");
            }
            return json(
                    """
                    {"id":"resp-1","status":"completed","output":[
                       {"type":"function_call","id":"fc_1","call_id":"call_1",
                        "name":"get_revenue","arguments":"{\\"quarter\\":\\"Q3\\"}"}]}""");
        }

        private MockResponse json(String body) {
            return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
        }
    }
}
