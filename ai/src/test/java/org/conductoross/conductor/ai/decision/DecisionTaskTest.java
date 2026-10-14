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
package org.conductoross.conductor.ai.decision;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.tasks.http.HttpTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.*;

class DecisionTaskTest {
    private static final String RESPONSE =
            """
        {"model":"typesafe/jev-test", "id":"decision-1",
         "answers":{"team":{"type":"choice","choice":"billing","confidence":0.9}},
         "usage":{"input_tokens":40,"output_tokens":10,"cost":0.00001}}
        """;

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
    private MockWebServer server;
    private DecisionConfiguration config;
    private DecisionTask decisionTask;

    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        config = new DecisionConfiguration();
        config.setApiKey("test-key");
        config.setEndpoint(server.url("/v1/systemone").toString());
        decisionTask = task(List.of(new SystemOneDecisionApiAdapter()));
    }

    @AfterEach
    void close() throws Exception {
        server.shutdown();
    }

    private DecisionTask task(List<DecisionApiAdapter> adapters) {
        return new DecisionTask(mapper, config, adapters);
    }

    private static DecisionRequest request(String provider) {
        return new DecisionRequest(
                provider,
                "jev-1.13",
                "Duplicate charge",
                Map.of(
                        "team",
                        new DecisionQuestion(
                                DecisionQuestion.Type.CHOICE,
                                "Choose team",
                                Map.of("billing", "Payments", "technical", "Software"),
                                null)));
    }

    private TaskModel run(DecisionRequest request, String question) {
        Map<String, Object> input = new HashMap<>(mapper.convertValue(request, Map.class));
        if (question != null) {
            input.put(DecisionTask.QUESTION, question);
        }
        TaskModel task = new TaskModel();
        task.setInputData(input);
        task.setStatus(TaskModel.Status.SCHEDULED);
        decisionTask.start(null, task, null);
        return task;
    }

    private void respond(String body) {
        server.enqueue(
                new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    @Test
    void isAsyncSoItNeverRunsInTheDecider() {
        assertTrue(decisionTask.isAsync());
        assertEquals("AI_DECISION", decisionTask.getTaskType());
    }

    @Test
    void completesWithValidatedAnswersAndSelectedCase() throws Exception {
        respond(RESPONSE);

        TaskModel task = run(request(null), "team");

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        var sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals("POST", sent.getMethod());
        assertEquals("Bearer test-key", sent.getHeader("Authorization"));
        JsonNode payload = mapper.readTree(sent.getBody().readUtf8());
        assertEquals("jev-1.13", payload.path("model").asText());
        assertEquals("Payments", payload.at("/questions/team/criteria/billing").asText());
        assertFalse(payload.has("provider"));

        Map<String, Object> output = task.getOutputData();
        assertEquals("billing", output.get(DecisionTask.SELECTED_CASE));
        assertEquals("openrouter", output.get("provider"));
        assertEquals("typesafe/jev-test", output.get("model"));
        assertEquals("decision-1", output.get("requestId"));
        JsonNode tree = mapper.valueToTree(output);
        assertEquals("billing", tree.at("/answers/team/choice").asText());
        assertEquals(40, tree.at("/usage/inputTokens").asInt());
        assertEquals("USD", tree.at("/usage/currency").asText());
        assertFalse(mapper.writeValueAsString(output).contains("test-key"));
    }

    @Test
    void withoutQuestionCompletesWithoutSelectedCase() {
        respond(RESPONSE);

        TaskModel task = run(request(null), null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        assertFalse(task.getOutputData().containsKey(DecisionTask.SELECTED_CASE));
    }

    @Test
    void mapsBooleanAndScoreAnswers() throws Exception {
        DecisionRequest request =
                new DecisionRequest(
                        null,
                        "jev-1.13",
                        "State",
                        Map.of(
                                "urgent",
                                new DecisionQuestion(
                                        DecisionQuestion.Type.BOOLEAN, "Urgent?", null, null),
                                "severity",
                                new DecisionQuestion(
                                        DecisionQuestion.Type.SCORE,
                                        "Rate",
                                        null,
                                        List.of("Low", "High"))));
        respond(
                """
            {"model":"decision-test","answers":{"urgent":{"type":"noul","noul":0.8},
            "severity":{"type":"score","score":0.4}}}
            """);

        TaskModel task = run(request, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        JsonNode tree = mapper.valueToTree(task.getOutputData());
        assertEquals(0.8, tree.at("/answers/urgent/probability").asDouble());
        assertEquals(0.4, tree.at("/answers/severity/score").asDouble());
        assertEquals(
                "noul",
                mapper.readTree(server.takeRequest().getBody().readUtf8())
                        .at("/questions/urgent/type")
                        .asText());
    }

    @Test
    void invalidAnswersAndMalformedJsonFailTerminally() {
        for (String body :
                List.of(
                        RESPONSE.replace("\"choice\":\"billing\"", "\"choice\":\"unknown\""),
                        RESPONSE.replace("0.9", "1.5"),
                        RESPONSE.replace("\"team\":", "\"other\":"),
                        "{broken")) {
            respond(body);
            TaskModel task = run(request(null), "team");
            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus(), body);
        }
    }

    /** Answers every request with {@code code}, so retries made by the HTTP client are counted. */
    private void alwaysRespond(int code) {
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return new MockResponse()
                                .setResponseCode(code)
                                .setHeader("Location", server.url("/redirect"))
                                .setBody("private-provider-error test-key");
                    }
                });
    }

    @Test
    void rateLimitsAndServerErrorsAreRetryableOthersAreTerminal() {
        Map<Integer, TaskModel.Status> expected =
                Map.of(
                        400, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                        401, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                        429, TaskModel.Status.FAILED,
                        500, TaskModel.Status.FAILED,
                        503, TaskModel.Status.FAILED);
        expected.forEach(
                (code, status) -> {
                    alwaysRespond(code);
                    TaskModel task = run(request(null), "team");
                    assertEquals(status, task.getStatus(), "status " + code);
                    assertEquals("Decision HTTP status " + code, task.getReasonForIncompletion());
                });
    }

    /** Retries belong to the task definition, so the HTTP client sends each attempt once. */
    @Test
    void eachAttemptSendsOneRequest() {
        for (int code : List.of(401, 429, 500, 503)) {
            alwaysRespond(code);
            int before = server.getRequestCount();
            run(request(null), "team");
            assertEquals(1, server.getRequestCount() - before, "requests for " + code);
        }
    }

    @Test
    void oversizedResponseIsTerminal() {
        server.enqueue(
                new MockResponse()
                        .setBody(
                                "{\"pad\":\"" + "x".repeat(DecisionTask.MAX_RESPONSE_BYTES) + "\"}")
                        .addHeader("Content-Type", "application/json"));

        TaskModel task = run(request(null), "team");

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("Decision response too large", task.getReasonForIncompletion());
    }

    @Test
    void redirectsAreNotFollowedSoTheCredentialStaysWithTheProvider() throws Exception {
        for (int code : List.of(301, 302, 307, 308)) {
            alwaysRespond(code);
            int before = server.getRequestCount();

            TaskModel task = run(request(null), "team");

            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
            assertEquals("Decision HTTP status " + code, task.getReasonForIncompletion());
            assertEquals(1, server.getRequestCount() - before, "requests for " + code);
        }
    }

    @Test
    void appliesTheConfiguredTimeout() {
        config.setTimeout(Duration.ofMillis(200));
        decisionTask = task(List.of(new SystemOneDecisionApiAdapter()));
        server.enqueue(
                new MockResponse()
                        .setBody(RESPONSE)
                        .addHeader("Content-Type", "application/json")
                        .setBodyDelay(2, TimeUnit.SECONDS));

        TaskModel task = run(request(null), "team");

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("Decision transport failed", task.getReasonForIncompletion());
    }

    @Test
    void transportFailureIsRetryable() throws Exception {
        server.shutdown();

        TaskModel task = run(request(null), "team");

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("Decision transport failed", task.getReasonForIncompletion());
    }

    @Test
    void missingCredentialFailsBeforeHttp() {
        config.setApiKey(null);

        TaskModel task = run(request(null), "team");

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "Decision: Decision API key is not configured", task.getReasonForIncompletion());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void defaultCredentialIsNotSharedWithOtherProviders() {
        DecisionConfiguration.Provider other = new DecisionConfiguration.Provider();
        other.setEndpoint(server.url("/other").toString());
        config.getProviders().put("other", other);

        TaskModel task = run(request("other"), "team");

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void unknownApiShapeFailsBeforeHttp() {
        config.setApiShape("unknown");

        TaskModel task = run(request(null), "team");

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void invalidInputFailsBeforeHttp() {
        TaskModel missingQuestions =
                run(new DecisionRequest(null, "jev-1.13", "State", Map.of()), null);
        TaskModel unknownRoutingQuestion = run(request(null), "missing");

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, missingQuestions.getStatus());
        assertEquals(
                TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, unknownRoutingQuestion.getStatus());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void selectsApiShapeByProviderAndModel() throws Exception {
        DecisionConfiguration.Provider provider = new DecisionConfiguration.Provider();
        provider.setApiKey("provider-key");
        provider.setEndpoint(server.url("/provider").toString());
        provider.setApiShape("system-one");
        DecisionConfiguration.Model model = new DecisionConfiguration.Model();
        model.setEndpoint(server.url("/model").toString());
        model.setApiShape("alternate");
        provider.getModels().put("jev-1.13", model);
        config.getProviders().put("custom", provider);
        DecisionApiAdapter alternate =
                new DecisionApiAdapter() {
                    public String name() {
                        return "alternate";
                    }

                    public Map<String, Object> encode(DecisionRequest input) {
                        return Map.of("selectedModel", input.model(), "observation", input.state());
                    }

                    public HttpTask.Input createRequest(
                            DecisionRequest input, DecisionConfiguration.Route route) {
                        HttpTask.Input http = new HttpTask.Input();
                        http.setUri(route.endpoint());
                        http.setMethod("PUT");
                        http.setHeaders(new HashMap<>(Map.of("X-Decision-Key", route.apiKey())));
                        http.setBody(encode(input));
                        return http;
                    }

                    public DecisionResult decode(
                            JsonNode data, long latencyMs, String selectedProvider) {
                        assertEquals("custom", selectedProvider);
                        return new DecisionResult(
                                selectedProvider,
                                "jev-1.13",
                                Map.of(
                                        "team",
                                        new DecisionResult.Answer(
                                                DecisionQuestion.Type.CHOICE,
                                                data.path("selected").asText(),
                                                null,
                                                null,
                                                null)),
                                null,
                                latencyMs,
                                null);
                    }
                };
        decisionTask = task(List.of(new SystemOneDecisionApiAdapter(), alternate));
        respond("{\"selected\":\"billing\"}");

        TaskModel task = run(request("custom"), "team");

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForIncompletion());
        var sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/model", sent.getPath());
        assertEquals("PUT", sent.getMethod());
        assertEquals("provider-key", sent.getHeader("X-Decision-Key"));
        assertNull(sent.getHeader("Authorization"));
        assertEquals(
                "Duplicate charge",
                mapper.readTree(sent.getBody().readUtf8()).path("observation").asText());
        assertEquals("billing", task.getOutputData().get(DecisionTask.SELECTED_CASE));
        assertEquals("system-one", config.resolve("custom", "another-model").apiShape());
        assertEquals(
                server.url("/provider").toString(),
                config.resolve("custom", "another-model").endpoint());
        provider.setApiShape("alternate");
        assertEquals("alternate", config.resolve("custom", "another-model").apiShape());
    }

    @Test
    void genericJsonRoundTrip() throws Exception {
        assertEquals(
                request(null),
                mapper.readValue(mapper.writeValueAsBytes(request(null)), DecisionRequest.class));
    }
}
