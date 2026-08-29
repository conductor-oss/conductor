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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvocationInputMember;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bedrock cannot be driven end to end here — {@code InvokeAgent} is an AWS event-stream API, and
 * faking its binary framing would test the fake rather than the client. These cover what does not
 * need AWS: that the SDK client is shared rather than created per execution (the leak), that config
 * is validated up front, and that nothing is remembered between calls.
 */
class BedrockAgentClientTest {

    private static final String CREDENTIAL_REF = "AWS_CRED";

    private Map<String, String> credentials;
    private BedrockAgentClient client;

    @BeforeEach
    void setUp() {
        // Handed values, as Conductor substitutes them before the task runs.
        credentials = Map.of("accessKeyId", "AKIA_TEST", "secretAccessKey", "secret");
        client = new BedrockAgentClient();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void reportsItsOwnAgentType() {
        assertThat(client.agentType()).isEqualTo(A2AService.AGENT_TYPE_BEDROCK);
    }

    @Test
    void oneSdkClientIsSharedAcrossInvocationsOfTheSameCredentialAndRegion() {
        client.warmRuntimeClient(credentials, rawConfig("us-east-1"));
        client.warmRuntimeClient(credentials, rawConfig("us-east-1"));
        client.warmRuntimeClient(credentials, rawConfig("us-east-1"));

        // The leak this replaces: a client per execution, each owning Netty event loops and a
        // connection pool, with nothing ever removing a finished one.
        assertThat(client.openRuntimeClients()).isEqualTo(1);
    }

    @Test
    void aDifferentRegionGetsItsOwnSdkClient() {
        client.warmRuntimeClient(credentials, rawConfig("us-east-1"));
        client.warmRuntimeClient(credentials, rawConfig("eu-west-1"));

        assertThat(client.openRuntimeClients()).isEqualTo(2);
    }

    @Test
    void closeReleasesEverySdkClient() {
        client.warmRuntimeClient(credentials, rawConfig("us-east-1"));
        client.warmRuntimeClient(credentials, rawConfig("eu-west-1"));

        client.close();

        assertThat(client.openRuntimeClients()).isZero();
    }

    @Test
    void missingAgentIdentifiersFailAsABadRequest() {
        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .prompt("hello")
                        .credentials(credentials)
                        .rawConfig(Map.of("agentId", "agent-1"))
                        .build();

        assertThatThrownBy(() -> client.startAgent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentAliasId");
    }

    @Test
    void respondWithoutThePendingToolSaysSo() {
        ConductorAgentRespondRequest request =
                ConductorAgentRespondRequest.builder()
                        .executionId("session-1")
                        .body(Map.of("result", "tool output"))
                        .credentials(credentials)
                        .rawConfig(rawConfig("us-east-1"))
                        .build();

        // Bedrock needs the action group name to shape its reply, and it is carried on the request
        // rather than remembered here — so its absence is a bad request, not a silent wrong answer.
        assertThatThrownBy(() -> client.respondWithStatus(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending tool");
    }

    @Test
    void statusIsTerminalBecauseBedrockCannotBePolled() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentials(credentials);
        request.setRawConfig(rawConfig("us-east-1"));

        ConductorAgentStatusResponse response = client.getAgentStatus("session-1", request);

        // A turn is over before the call that started it returns, and its result is already in the
        // task output. Reporting terminal is what stops the delegate polling for an answer that no
        // API can give it.
        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(response.isComplete()).isTrue();
    }

    @Test
    void cancelIsANoOpBecauseBedrockHasNoCancelApi() {
        client.cancelAgent(
                ConductorAgentCancelRequest.builder()
                        .executionId("session-1")
                        .reason("cancelled by parent")
                        .credentials(credentials)
                        .rawConfig(rawConfig("us-east-1"))
                        .build());

        // Nothing to clean up, and nothing thrown — there is no state and no API to call.
        assertThat(client.openRuntimeClients()).isZero();
    }

    // --- parallel tool calls ------------------------------------------------------------------

    @Test
    void everyReturnControlInputIsReportedNotJustTheFirst() {
        ReturnControlPayload payload =
                ReturnControlPayload.builder()
                        .invocationInputs(apiInput("revenue_group"), apiInput("headcount_group"))
                        .build();

        BedrockAgentClient.Turn turn = BedrockAgentClient.toolTurn(payload);

        // A model may ask for several independent tools in one turn; reporting one leaves the rest
        // unrunnable by the workflow.
        assertThat(turn.state()).isEqualTo(ConductorAgentState.WAITING);
        assertThat(turn.pendingTools()).hasSize(2);
        assertThat(turn.pendingTools())
                .extracting(tool -> tool.get("tool_name"))
                .containsExactly("revenue_group", "headcount_group");
        // Bedrock names no call id, so a stable per-position id is derived.
        assertThat(turn.pendingTools())
                .extracting(tool -> tool.get("tool_call_id"))
                .containsExactly("revenue_group#0", "headcount_group#1");
        // The first stays on pendingTool for callers handling one tool per turn.
        assertThat(turn.pendingTool()).containsEntry("tool_name", "revenue_group");
        assertThat(turn.pendingToolName()).isEqualTo("revenue_group");
    }

    @Test
    void aReturnControlWithNoInputsStillReportsSomethingActionable() {
        BedrockAgentClient.Turn turn =
                BedrockAgentClient.toolTurn(ReturnControlPayload.builder().build());

        assertThat(turn.state()).isEqualTo(ConductorAgentState.WAITING);
        assertThat(turn.pendingTools()).hasSize(1);
        assertThat(turn.pendingTool()).containsEntry("tool_name", "unknown");
    }

    private static InvocationInputMember apiInput(String actionGroup) {
        return InvocationInputMember.builder()
                .apiInvocationInput(builder -> builder.actionGroup(actionGroup).apiPath("/invoke"))
                .build();
    }

    /**
     * The point of the bearer wiring: a Bedrock API key must actually reach the wire as an
     * Authorization header, and SigV4 must not sign over it. Asserted against a real request rather
     * than by inspecting the builder, since only the sent request proves the signer stood down.
     */
    @Test
    void anApiKeyReachesTheWireAsABearerToken() throws Exception {
        MockWebServer bedrock = new MockWebServer();
        bedrock.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
        bedrock.start();
        try (BedrockAgentRuntimeAsyncClient runtime =
                BedrockAgentClient.bearerAuth(
                                BedrockAgentRuntimeAsyncClient.builder()
                                        .region(Region.of("us-east-1"))
                                        .endpointOverride(bedrock.url("/").uri()),
                                "bedrock-api-key")
                        .build()) {

            // The call fails — the mock is not Bedrock — but the request still went out.
            assertThatThrownBy(
                            () ->
                                    runtime.invokeAgent(
                                                    InvokeAgentRequest.builder()
                                                            .agentId("agent-1")
                                                            .agentAliasId("alias-1")
                                                            .sessionId("session-1")
                                                            .inputText("hello")
                                                            .build(),
                                                    InvokeAgentResponseHandler.builder()
                                                            .onResponse(r -> {})
                                                            .subscriber(
                                                                    InvokeAgentResponseHandler
                                                                            .Visitor.builder()
                                                                            .build())
                                                            .build())
                                            .join())
                    .isInstanceOf(Exception.class);

            RecordedRequest sent = bedrock.takeRequest(10, TimeUnit.SECONDS);
            assertThat(sent).isNotNull();
            assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer bedrock-api-key");
            // SigV4 would have replaced it with an AWS4-HMAC-SHA256 credential scope.
            assertThat(sent.getHeader("Authorization")).doesNotContain("AWS4-HMAC-SHA256");
        } finally {
            bedrock.shutdown();
        }
    }

    @Test
    void eitherApiKeySpellingSelectsBearerAuth() {
        // Whichever spelling the provider's own docs use.
        assertThat(AgentCredentials.apiKey(Map.of("apiKey", "k1"))).isEqualTo("k1");
        assertThat(AgentCredentials.apiKey(Map.of("api_key", "k2"))).isEqualTo("k2");
    }

    @Test
    void staticKeysAndAssumeRoleStillResolve() {
        assertThat(
                        client.credentialsFor(
                                Map.of("accessKeyId", "AKIA", "secretAccessKey", "secret"),
                                "us-east-1"))
                .isNotNull();
        // No credentials at all is a supported mode: the host's own AWS chain.
        assertThat(client.credentialsFor(Map.of(), "us-east-1")).isNotNull();
    }

    private static Map<String, Object> rawConfig(String region) {
        return Map.of("agentId", "agent-1", "agentAliasId", "alias-1", "region", region);
    }
}
