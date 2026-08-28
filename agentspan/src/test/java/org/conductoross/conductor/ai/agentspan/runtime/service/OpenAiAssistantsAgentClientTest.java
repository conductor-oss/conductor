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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The OpenAI client shares its protocol with Azure Foundry through {@code AssistantsRunApi}, so
 * these tests concentrate on what is specific to it: API-key auth, the {@code OpenAI-Beta} header,
 * the absence of Azure's {@code api-version}, and the base-url override — plus the same
 * statelessness property, since holding no per-run state is what makes any of these clients
 * replica-safe.
 */
class OpenAiAssistantsAgentClientTest {

    private static final String CREDENTIAL_REF = "OPENAI_KEY";

    private MockWebServer openai;
    private InMemorySecretsDAO secrets;
    private OpenAiAssistantsAgentClient client;

    private final AtomicReference<String> runStatus = new AtomicReference<>("in_progress");
    private final AtomicReference<String> latestRunId = new AtomicReference<>("run-1");

    /** One record per request, so a body can never drift out of step with its path. */
    private record Call(
            String method, String path, String body, String authorization, String beta) {}

    private final List<Call> received = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        openai = new MockWebServer();
        openai.setDispatcher(new AssistantsDispatcher());
        openai.start();

        secrets = new InMemorySecretsDAO();
        secrets.put(CREDENTIAL_REF, "sk-test-key");
        client = newClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        openai.shutdown();
    }

    private OpenAiAssistantsAgentClient newClient() {
        return new OpenAiAssistantsAgentClient(
                new CredentialResolutionService(secrets),
                new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build());
    }

    @Test
    void reportsItsOwnAgentType() {
        assertThat(client.agentType()).isEqualTo(A2AService.AGENT_TYPE_OPENAI_ASSISTANTS);
        assertThat(client.agentType()).isEqualTo("openai-assistants");
    }

    @Test
    void executionIdIsTheThreadId() {
        assertThat(start().getExecutionId()).isEqualTo("thread-1");
    }

    @Test
    void sendsTheApiKeyAsABearerTokenWithTheAssistantsV2Header() {
        start();

        Call first = received.get(0);
        assertThat(first.authorization()).isEqualTo("Bearer sk-test-key");
        assertThat(first.beta()).isEqualTo("assistants=v2");
    }

    @Test
    void doesNotSendAzuresApiVersionParameter() {
        start();
        runStatus.set("completed");
        client.getAgentStatus("thread-1", statusRequest());

        assertThat(received).allSatisfy(r -> assertThat(r.path()).doesNotContain("api-version"));
    }

    @Test
    void readsTheApiKeyFromAnApiKeySubKeyToo() {
        secrets.remove(CREDENTIAL_REF);
        secrets.put(CREDENTIAL_REF, """
                {"api_key":"sk-nested"}""");

        start();

        assertThat(received.get(0).authorization()).isEqualTo("Bearer sk-nested");
    }

    @Test
    void completedRunReportsTheLatestAssistantMessage() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");

        ConductorAgentStatusResponse response = client.getAgentStatus(executionId, statusRequest());

        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getOutput()).isEqualTo(Map.of("result", "the answer"));
    }

    @Test
    void requiresActionSurfacesThePendingTool() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");

        ConductorAgentStatusResponse response = client.getAgentStatus(executionId, statusRequest());

        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.WAITING);
        assertThat(response.isWaiting()).isTrue();
        assertThat(response.getPendingToolName()).isEqualTo("lookup");
        assertThat(response.getPendingTool()).containsEntry("tool_call_id", "call-1");
    }

    @Test
    void failedRunCarriesTheProvidersReason() {
        String executionId = start().getExecutionId();
        runStatus.set("failed");

        ConductorAgentStatusResponse response = client.getAgentStatus(executionId, statusRequest());

        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.FAILED);
        assertThat(response.getReasonForIncompletion()).isEqualTo("rate limited");
    }

    @Test
    void aSecondClientInstanceCanPollARunItDidNotStart() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");

        // Stands in for a callback routed to another replica: a fresh client holding nothing.
        ConductorAgentStatusResponse response =
                newClient().getAgentStatus(executionId, statusRequest());

        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
    }

    @Test
    void respondSubmitsToolOutputsWhenTheRunIsBlocked() {
        String executionId = start().getExecutionId();
        runStatus.set("requires_action");
        received.clear();

        client.respond(respondRequest(executionId, Map.of("result", "tool output")));

        assertThat(pathsOf()).anyMatch(p -> p.contains("/submit_tool_outputs"));
        assertThat(lastBodyContaining("submit_tool_outputs")).contains("call-1", "tool output");
    }

    @Test
    void respondContinuesTheThreadWhenTheRunIsDone() {
        String executionId = start().getExecutionId();
        runStatus.set("completed");
        received.clear();

        client.respond(respondRequest(executionId, Map.of("result", "a follow-up question")));

        // A new run on the same thread; the caller's executionId stays valid.
        assertThat(latestRunId.get()).isEqualTo("run-2");
        assertThat(lastBodyContaining("/messages")).contains("a follow-up question");
        assertThat(lastBodyContaining("/messages")).doesNotContain("result=");
    }

    @Test
    void baseUrlCanBeOverridden() {
        // Already exercised implicitly — the mock server is a baseUrl override — so assert the
        // default is what production would use when the override is absent.
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentialRef(CREDENTIAL_REF);
        request.setRawConfig(Map.of("assistantId", "asst-1"));

        // No server at api.openai.com from this test, so the call must fail trying to reach it
        // rather than silently hitting something local.
        assertThatThrownBy(() -> client.getAgentStatus("thread-1", request))
                .hasMessageContaining("Assistants API call");
    }

    @Test
    void cancelIsBestEffortAndDoesNotThrow() {
        String executionId = start().getExecutionId();
        received.clear();

        client.cancelAgent(
                ConductorAgentCancelRequest.builder()
                        .executionId(executionId)
                        .reason("cancelled by parent")
                        .credentialRef(CREDENTIAL_REF)
                        .rawConfig(rawConfig())
                        .build());

        assertThat(pathsOf()).anyMatch(p -> p.contains("/cancel"));
    }

    @Test
    void missingAssistantIdFailsAsABadRequest() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentialRef(CREDENTIAL_REF);
        request.setRawConfig(Map.of("baseUrl", baseUrl()));

        assertThatThrownBy(() -> client.getAgentStatus("thread-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawConfig.assistantId is required");
    }

    @Test
    void missingCredentialRefFailsAsABadRequest() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setRawConfig(rawConfig());

        assertThatThrownBy(() -> client.getAgentStatus("thread-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialRef is required");
    }

    @Test
    void anUnresolvableCredentialSaysWhatIsMissing() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentialRef("NOT_STORED");
        request.setRawConfig(rawConfig());

        assertThatThrownBy(() -> client.getAgentStatus("thread-1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must hold the API key");
    }

    // --- helpers ---------------------------------------------------------------------------

    private org.conductoross.conductor.ai.agent.ConductorAgentStartResponse start() {
        return client.startAgent(
                ConductorAgentStartRequest.builder()
                        .prompt("what is the answer?")
                        .credentialRef(CREDENTIAL_REF)
                        .rawConfig(rawConfig())
                        .build());
    }

    private String baseUrl() {
        return openai.url("").toString().replaceAll("/$", "");
    }

    private Map<String, Object> rawConfig() {
        return Map.of("assistantId", "asst-1", "baseUrl", baseUrl());
    }

    private ConductorAgentRequest statusRequest() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentialRef(CREDENTIAL_REF);
        request.setRawConfig(rawConfig());
        return request;
    }

    private ConductorAgentRespondRequest respondRequest(
            String executionId, Map<String, Object> body) {
        return ConductorAgentRespondRequest.builder()
                .executionId(executionId)
                .body(body)
                .credentialRef(CREDENTIAL_REF)
                .rawConfig(rawConfig())
                .build();
    }

    private List<String> pathsOf() {
        synchronized (received) {
            return received.stream().map(Call::path).toList();
        }
    }

    private String lastBodyContaining(String pathFragment) {
        synchronized (received) {
            for (int i = received.size() - 1; i >= 0; i--) {
                if (received.get(i).path().contains(pathFragment)) {
                    return received.get(i).body();
                }
            }
        }
        throw new AssertionError("no request matching " + pathFragment + " in " + pathsOf());
    }

    private final class AssistantsDispatcher extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath() == null ? "" : request.getPath();
            synchronized (received) {
                received.add(
                        new Call(
                                request.getMethod(),
                                path,
                                request.getBody().readUtf8(),
                                request.getHeader("Authorization"),
                                request.getHeader("OpenAI-Beta")));
            }
            if (path.startsWith("/threads") && !path.contains("/threads/")) {
                return json("{\"id\":\"thread-1\"}");
            }
            if (path.contains("/cancel")) {
                return json("{\"id\":\"" + latestRunId.get() + "\",\"status\":\"cancelled\"}");
            }
            if (path.contains("/submit_tool_outputs")) {
                return json("{\"id\":\"" + latestRunId.get() + "\",\"status\":\"queued\"}");
            }
            if (path.contains("/runs")) {
                if ("POST".equals(request.getMethod())) {
                    latestRunId.set("run-2");
                    return json("{\"id\":\"run-2\",\"status\":\"queued\"}");
                }
                return json(
                        "{\"data\":[{\"id\":\""
                                + latestRunId.get()
                                + "\",\"status\":\""
                                + runStatus.get()
                                + "\",\"last_error\":{\"message\":\"rate limited\"}"
                                + ",\"required_action\":{\"submit_tool_outputs\":{\"tool_calls\":"
                                + "[{\"id\":\"call-1\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{}\"}}]}}}]}");
            }
            if (path.contains("/messages")) {
                return "POST".equals(request.getMethod())
                        ? json("{\"id\":\"msg-1\"}")
                        : json(
                                """
                                {"data":[{"role":"assistant","content":[{"type":"text","text":{"value":"the answer"}}]},
                                         {"role":"user","content":[{"type":"text","text":{"value":"what is the answer?"}}]}]}""");
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
