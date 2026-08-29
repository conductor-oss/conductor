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
    void aProjectAgentRunsThroughTheResponsesApiWithItsOwnToolsAndInstructions() {
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
        // The agent's definition was read so its tools and instructions could be forwarded.
        assertThat(paths).anyMatch(p -> p.startsWith("/api/projects/p1/agents/analyst"));
        assertThat(paths).anyMatch(p -> p.startsWith("/api/projects/p1/openai/responses"));
    }

    @Test
    void codeInterpreterIsWrappedForTheResponsesApi() throws Exception {
        var tools =
                MAPPER.readTree(
                        "[{\"type\":\"code_interpreter\"},{\"type\":\"file_search\",\"x\":1}]");

        var adapted = AzureFoundryAgentClient.toResponsesApiTools(tools);

        // The Responses API rejects a bare code_interpreter; other tools pass through untouched.
        assertThat(adapted.get(0).path("container").path("type").asText()).isEqualTo("auto");
        assertThat(adapted.get(1).path("type").asText()).isEqualTo("file_search");
        assertThat(adapted.get(1).path("x").asInt()).isEqualTo(1);
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

        // Nothing to poll, and the result is already in the task output.
        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(status.isComplete()).isTrue();
        assertThat(paths).isEmpty();
    }

    @Test
    void aSynchronousSurfaceCannotBeResumed() {
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
                                        "responses"))
                        .build();

        // Better than quietly issuing thread operations against an endpoint that has no threads.
        assertThatThrownBy(() -> client.respond(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no conversation to continue");
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
            }
            if (path.startsWith("/models/chat/completions")) {
                return json(
                        "{\"id\":\"chatcmpl-1\",\"choices\":[{\"message\":{\"content\":\"hello there\"}}]}");
            }
            if (path.startsWith("/api/projects/p1/openai/responses")) {
                return json(
                        """
                        {"id":"resp-1","output":[
                           {"content":[{"type":"output_text","text":"first part"},
                                       {"type":"reasoning","text":"ignored"}]},
                           {"content":[{"type":"output_text","text":"second part"}]}]}""");
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
