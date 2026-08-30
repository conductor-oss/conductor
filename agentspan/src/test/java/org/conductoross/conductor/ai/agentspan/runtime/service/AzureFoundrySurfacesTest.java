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

import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Microsoft Foundry is three APIs behind one agentType, and the endpoint decides which. Model
 * inference and a project's Responses API both answer inside the start call, so they report a
 * terminal state rather than being polled; only the classic Assistants surface has a run to poll.
 */
class AzureFoundrySurfacesTest {

    private static final String CREDENTIAL_REF = "AZURE_CRED";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer azure;
    private Map<String, String> credentials;
    private AzureFoundryAgentClient client;
    private final List<String> paths = new ArrayList<>();
    private final Map<String, String> bodies = new LinkedHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean functionCallTurn =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicReference<String> responseBody =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        azure = new MockWebServer();
        azure.setDispatcher(new SurfaceDispatcher());
        azure.start();

        credentials = Map.of("apiKey", "azure-api-key");
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
    void anEndpointIsClassifiedByItsShape() {
        assertThat(
                        AzureFoundryAgentClient.isInferenceEndpoint(
                                "https://x.inference.ml.azure.com/score"))
                .isTrue();
        assertThat(
                        AzureFoundryAgentClient.isInferenceEndpoint(
                                "https://p.services.ai.azure.com/models"))
                .isTrue();
        // A project endpoint is the Responses API, not inference.
        assertThat(
                        AzureFoundryAgentClient.isInferenceEndpoint(
                                "https://p.services.ai.azure.com/api/projects/p1"))
                .isFalse();
        assertThat(
                        AzureFoundryAgentClient.isFoundryProjectEndpoint(
                                "https://p.services.ai.azure.com/api/projects/p1"))
                .isTrue();
        // Classic Assistants is neither.
        assertThat(
                        AzureFoundryAgentClient.isFoundryProjectEndpoint(
                                "https://r.openai.azure.com/openai"))
                .isFalse();
        assertThat(AzureFoundryAgentClient.isInferenceEndpoint("https://r.openai.azure.com/openai"))
                .isFalse();
    }

    @Test
    void anExplicitSurfaceOverridesTheHostname() {
        // Sovereign clouds (.azure.us, .azure.cn), private endpoints and proxies do not match the
        // public hostname patterns, so the surface can be stated outright.
        assertThat(
                        AzureFoundryAgentClient.surfaceOf(
                                "https://private.example.com/openai",
                                Map.of("surface", "responses")))
                .isEqualTo(AzureFoundryAgentClient.Surface.RESPONSES);
        assertThat(
                        AzureFoundryAgentClient.surfaceOf(
                                "https://private.example.com/openai", Map.of()))
                .isEqualTo(AzureFoundryAgentClient.Surface.ASSISTANTS);
        assertThatThrownBy(
                        () ->
                                AzureFoundryAgentClient.surfaceOf(
                                        "https://x", Map.of("surface", "nonsense")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inference, responses, or assistants");
    }

    @Test
    void modelInferenceAnswersInsideTheStartCall() {
        ConductorAgentStartResponse start =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .prompt("say hello")
                                .credentials(credentials)
                                .rawConfig(
                                        Map.of(
                                                "endpoint", base() + "/models",
                                                "assistantId", "unused",
                                                "surface", "inference",
                                                "model", "gpt-4o"))
                                .build());

        // Synchronous: the answer is on the start response, so the task never polls.
        assertThat(start.getState()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(start.getOutput()).isEqualTo(Map.of("result", "hello there"));
        assertThat(paths).anyMatch(p -> p.startsWith("/models/chat/completions"));
    }

    @Test
    void aProjectAgentIsInvokedByReferenceRatherThanReplayed() throws Exception {
        ConductorAgentStartResponse start =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .prompt("analyse this")
                                .credentials(credentials)
                                .agentUrl(base() + "/api/projects/p1/agents/analyst")
                                .rawConfig(Map.of("surface", "responses"))
                                .build());

        assertThat(start.getState()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(start.getOutput()).isEqualTo(Map.of("result", "first part\nsecond part"));
        assertThat(start.getAgentName()).isEqualTo("analyst");
        String responsesPath =
                paths.stream()
                        .filter(p -> p.startsWith("/api/projects/p1/openai/v1/responses"))
                        .findFirst()
                        .orElseThrow();

        // Naming the agent is what makes this the agent's own run rather than an anonymous model
        // call that happens to behave like it. Without it Azure records nothing against the agent.
        // The property is agent_reference; the service rejects "agent" as deprecated.
        JsonNode body = MAPPER.readTree(bodies.get(responsesPath));
        assertThat(body.path("agent_reference").path("type").asText()).isEqualTo("agent_reference");
        assertThat(body.path("agent_reference").path("name").asText()).isEqualTo("analyst");
        assertThat(body.path("input").get(0).path("content").asText()).isEqualTo("analyse this");

        // The agent supplies its own model, instructions and tools; sending ours would override
        // the definition the run is supposed to be attributed to.
        assertThat(body.has("model")).isFalse();
        assertThat(body.has("instructions")).isFalse();
        assertThat(body.has("tools")).isFalse();

        // And its definition is no longer read, because nothing is replayed from it.
        assertThat(paths).noneMatch(p -> p.startsWith("/api/projects/p1/agents/analyst?"));
    }

    @Test
    void anAgentVersionIsPinnedWhenOneIsConfigured() throws Exception {
        client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("analyse this")
                        .credentials(credentials)
                        .agentUrl(base() + "/api/projects/p1/agents/analyst")
                        .rawConfig(Map.of("surface", "responses", "agentVersion", "3"))
                        .build());

        String responsesPath =
                paths.stream()
                        .filter(p -> p.startsWith("/api/projects/p1/openai/v1/responses"))
                        .findFirst()
                        .orElseThrow();
        assertThat(
                        MAPPER.readTree(bodies.get(responsesPath))
                                .path("agent_reference")
                                .path("version")
                                .asText())
                .isEqualTo("3");
    }

    @Test
    void theToolsFoundryRanItselfAreReportedWithTheirInput() {
        ConductorAgentStartResponse start =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .prompt("how did GOOGL do?")
                                .credentials(credentials)
                                .agentUrl(base() + "/api/projects/p1/agents/analyst")
                                .rawConfig(Map.of("surface", "responses"))
                                .build());

        // A built-in tool never pauses the run, so without this the execution's only record of the
        // agent's work is its final sentence.
        assertThat(start.getExecutedTools()).hasSize(2);

        Map<String, Object> search = start.getExecutedTools().get(0);
        assertThat(search).containsEntry("type", "web_search_call");
        assertThat(search).containsEntry("tool_call_id", "ws_1");
        assertThat(search).containsEntry("status", "completed");
        assertThat(search.get("action").toString()).contains("GOOGL 1 month return");

        Map<String, Object> code = start.getExecutedTools().get(1);
        assertThat(code).containsEntry("type", "code_interpreter_call");
        assertThat(code).containsEntry("code", "print(2+2)");

        // Messages and reasoning are the reply, not tool calls.
        assertThat(start.getExecutedTools()).noneMatch(call -> "message".equals(call.get("type")));
    }

    @Test
    void aSynchronousSurfaceReportsTerminalRatherThanBeingPolled() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentials(credentials);
        request.setRawConfig(
                Map.of(
                        "endpoint", base() + "/api/projects/p1",
                        "assistantId", "analyst",
                        "surface", "responses"));
        paths.clear();

        var status = client.getAgentStatus("resp-1", request);

        // Re-read rather than assumed: a Responses turn answers inside the call but can still be
        // waiting on tools, and assuming it finished would drop the tool calls on the floor.
        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(status.isComplete()).isTrue();
        assertThat(paths).anyMatch(p -> p.contains("/openai/v1/responses/resp-1"));
    }

    @Test
    void modelInferenceHasNothingToPoll() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentials(credentials);
        request.setRawConfig(
                Map.of(
                        "endpoint", base() + "/api/projects/p1",
                        "assistantId", "analyst",
                        "surface", "inference"));
        paths.clear();

        var status = client.getAgentStatus("chatcmpl-1", request);

        // Chat completions hold no run, so there is genuinely nothing to ask about.
        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(paths).isEmpty();
    }

    @Test
    void modelInferenceCannotBeResumed() {
        ConductorAgentRespondRequest request =
                ConductorAgentRespondRequest.builder()
                        .executionId("resp-1")
                        .body(Map.of("result", "more"))
                        .credentials(credentials)
                        .rawConfig(
                                Map.of(
                                        "endpoint",
                                        base() + "/api/projects/p1",
                                        "assistantId",
                                        "analyst",
                                        "surface",
                                        "inference"))
                        .build();

        // Chat completions hold no conversation at all. The Responses surface does, and is
        // continued rather than refused - see aResponsesTurnIsContinuedByChainingOffTheTurnBefore.
        assertThatThrownBy(() -> client.respond(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no conversation to continue");
    }

    @Test
    void aFunctionCallIsAToolRequestRatherThanWorkAlreadyDone() {
        functionCallTurn.set(true);

        ConductorAgentStartResponse start =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .prompt("what was Q3 revenue?")
                                .credentials(credentials)
                                .agentUrl(base() + "/api/projects/p1/agents/analyst")
                                .rawConfig(Map.of("surface", "responses"))
                                .build());

        // The agent asked for a function and stopped. Reading that as a tool the platform had
        // already run would report the request as work done and complete the task without ever
        // running it.
        assertThat(start.getState()).isEqualTo(ConductorAgentState.WAITING);
        assertThat(start.getExecutedTools()).isEmpty();
        assertThat(start.getPendingTools()).hasSize(1);
        assertThat(start.getPendingTools().get(0))
                .containsEntry("tool_name", "get_revenue")
                .containsEntry("tool_call_id", "call_1");
        assertThat(start.getPendingTools().get(0).get("arguments").toString()).contains("Q3");
        assertThat(start.getPendingTool()).isEqualTo(start.getPendingTools().get(0));
    }

    @Test
    void aConversationalResumeSendsAMessageRatherThanAToolResult() throws Exception {
        // Resuming with a new prompt is the next thing to say, not an answer to a question. Shaping
        // it as a tool result fails outright: there is no call to key it on.
        ConductorAgentRespondRequest request =
                ConductorAgentRespondRequest.builder()
                        .executionId("resp-1")
                        .body(Map.of("result", "and what about GOOG?"))
                        .credentials(credentials)
                        .rawConfig(
                                Map.of(
                                        "endpoint", base() + "/api/projects/p1",
                                        "assistantId", "analyst",
                                        "surface", "responses"))
                        .build();
        paths.clear();

        var status = client.respondWithStatus(request);

        assertThat(status).isNotNull();
        JsonNode body = MAPPER.readTree(bodies.get(responsesPath()));
        assertThat(body.path("input").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("input").get(0).path("content").asText())
                .isEqualTo("and what about GOOG?");
        assertThat(body.path("input").get(0).has("call_id")).isFalse();
    }

    @Test
    void aConfiguredConversationReplacesTheChainRatherThanJoiningIt() throws Exception {
        // The two are alternative ways to carry turn history and the API takes one of them.
        ConductorAgentRespondRequest request =
                ConductorAgentRespondRequest.builder()
                        .executionId("resp-1")
                        .toolResults(Map.of("call_1", Map.of("revenue", "4.2M")))
                        .credentials(credentials)
                        .rawConfig(
                                Map.of(
                                        "endpoint", base() + "/api/projects/p1",
                                        "assistantId", "analyst",
                                        "surface", "responses",
                                        "conversation", "conv_123"))
                        .build();
        paths.clear();

        client.respondWithStatus(request);

        JsonNode body = MAPPER.readTree(bodies.get(responsesPath()));
        assertThat(body.path("conversation").asText()).isEqualTo("conv_123");
        assertThat(body.has("previous_response_id")).isFalse();
    }

    @Test
    void aRejectedTurnFailsRatherThanCompletingWithNothingToSay() {
        // A content filter or an exhausted token budget still answers 200, with no message. Reading
        // only the output items would call that a completed agent that happened to say nothing.
        responseBody.set(
                """
                {"id":"resp-9","status":"incomplete","output":[],
                 "incomplete_details":{"reason":"content_filter"}}""");

        ConductorAgentStartResponse start =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .prompt("something disallowed")
                                .credentials(credentials)
                                .agentUrl(base() + "/api/projects/p1/agents/analyst")
                                .rawConfig(Map.of("surface", "responses"))
                                .build());

        assertThat(start.getState()).isEqualTo(ConductorAgentState.FAILED);
        assertThat(start.getReasonForIncompletion()).contains("content_filter");
    }

    @Test
    void aTurnThatSpeaksAndAsksKeepsWhatItSaid() {
        responseBody.set(
                """
                {"id":"resp-8","output":[
                   {"type":"message","content":[{"type":"output_text","text":"Let me look."}]},
                   {"type":"function_call","id":"fc_2","call_id":"call_2",
                    "name":"get_revenue","arguments":"{}"}]}""");

        ConductorAgentStartResponse start =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .prompt("revenue?")
                                .credentials(credentials)
                                .agentUrl(base() + "/api/projects/p1/agents/analyst")
                                .rawConfig(Map.of("surface", "responses"))
                                .build());

        assertThat(start.getState()).isEqualTo(ConductorAgentState.WAITING);
        assertThat(start.getOutput()).isEqualTo(Map.of("result", "Let me look."));
    }

    @Test
    void anAgentNamedOnlyByItsUrlCanStillBeContinued() throws Exception {
        // The whole tool loop runs on this: start names the agent by agentUrl, and every later call
        // has to find the same agent. rawConfig carries no endpoint here, so losing agentUrl on the
        // way to respond fails the turn outright.
        ConductorAgentRespondRequest request =
                ConductorAgentRespondRequest.builder()
                        .executionId("resp-1")
                        .agentUrl(base() + "/api/projects/p1/agents/analyst")
                        .toolResults(Map.of("call_1", Map.of("revenue", "4.2M")))
                        .credentials(credentials)
                        .rawConfig(Map.of("surface", "responses"))
                        .build();
        paths.clear();

        var status = client.respondWithStatus(request);

        assertThat(status).isNotNull();
        JsonNode body = MAPPER.readTree(bodies.get(responsesPath()));
        assertThat(body.path("agent_reference").path("name").asText()).isEqualTo("analyst");
    }

    private String responsesPath() {
        return paths.stream()
                .filter(p -> p.startsWith("/api/projects/p1/openai/v1/responses"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aResponsesTurnIsContinuedByChainingOffTheTurnBefore() throws Exception {
        ConductorAgentRespondRequest request =
                ConductorAgentRespondRequest.builder()
                        .executionId("resp-1")
                        .toolResults(Map.of("call_1", Map.of("revenue", "4.2M")))
                        .credentials(credentials)
                        .rawConfig(
                                Map.of(
                                        "endpoint", base() + "/api/projects/p1",
                                        "assistantId", "analyst",
                                        "surface", "responses"))
                        .build();
        paths.clear();

        var status = client.respondWithStatus(request);

        // Answered inside the call, like Bedrock - there is nothing left to poll.
        assertThat(status).isNotNull();
        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);

        String path =
                paths.stream()
                        .filter(p -> p.startsWith("/api/projects/p1/openai/v1/responses"))
                        .findFirst()
                        .orElseThrow();
        JsonNode body = MAPPER.readTree(bodies.get(path));
        // The turn's own id chains the next one, so no second handle has to be carried anywhere.
        assertThat(body.path("previous_response_id").asText()).isEqualTo("resp-1");
        assertThat(body.path("agent_reference").path("name").asText()).isEqualTo("analyst");
        assertThat(body.path("input").get(0).path("type").asText())
                .isEqualTo("function_call_output");
        assertThat(body.path("input").get(0).path("call_id").asText()).isEqualTo("call_1");
        assertThat(body.path("input").get(0).path("output").asText()).contains("4.2M");
    }

    private String base() {
        return azure.url("").toString().replaceAll("/$", "");
    }

    private final class SurfaceDispatcher extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath() == null ? "" : request.getPath();
            synchronized (paths) {
                paths.add(path);
                bodies.put(path, request.getBody().readUtf8());
            }
            if (path.startsWith("/models/chat/completions")) {
                return json(
                        "{\"id\":\"chatcmpl-1\",\"choices\":[{\"message\":{\"content\":\"hello there\"}}]}");
            }
            if (path.startsWith("/api/projects/p1/openai/v1/responses")) {
                if (responseBody.get() != null) {
                    return json(responseBody.get());
                }
                if (functionCallTurn.get()) {
                    return json(
                            """
                            {"id":"resp-2","output":[
                               {"type":"function_call","id":"fc_1","call_id":"call_1",
                                "name":"get_revenue","arguments":"{\\"quarter\\":\\"Q3\\"}"}]}""");
                }
                // Shaped like a real reply: one item per step, of which only some are messages.
                return json(
                        """
                        {"id":"resp-1","output":[
                           {"type":"web_search_call","id":"ws_1","status":"completed",
                            "action":{"type":"search","query":"GOOGL 1 month return"}},
                           {"type":"message","content":[{"type":"output_text","text":"first part"},
                                       {"type":"reasoning","text":"ignored"}]},
                           {"type":"code_interpreter_call","id":"ci_1","status":"completed",
                            "code":"print(2+2)"},
                           {"type":"message","content":[{"type":"output_text","text":"second part"}]}]}""");
            }
            if (path.startsWith("/api/projects/p1/agents/analyst")) {
                return json(
                        """
                        {"versions":{"latest":{"definition":{
                           "instructions":"be brief",
                           "tools":[{"type":"code_interpreter"}]}}}}""");
            }
            return new MockResponse().setResponseCode(404).setBody("{}");
        }

        private MockResponse json(String body) {
            return new MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .addHeader("Content-Type", "application/json");
        }
    }
}
