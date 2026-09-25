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
package org.conductoross.conductor.ai.agentspan.runtime.jev;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.jupiter.api.Assertions.*;

class HttpJevClientTest {
    private MockWebServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private JevConfiguration config;
    private JevClient client;
    private static final String RESPONSE =
            """
        {"model":"typesafe/jev-test", "id":"decision-1",
         "answers":{"team":{"type":"choice","choice":"billing","confidence":0.9}},
         "usage":{"input_tokens":40,"output_tokens":10,"cost":0.00001}}
        """;

    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        config = new JevConfiguration();
        config.setApiKey("test-key");
        config.setEndpoint(server.url("/v1/systemone").toString());
        client = new HttpJevClient(config, mapper, new OkHttpClient());
    }

    @AfterEach
    void close() throws Exception {
        server.shutdown();
    }

    private JevRequest request() {
        return new JevRequest(
                "jev-1.13",
                "Duplicate charge",
                Map.of(
                        "team",
                        new JevQuestion(
                                JevQuestion.Type.CHOICE,
                                "Choose team",
                                Map.of("billing", "Payments", "technical", "Software"),
                                null)));
    }

    private void response(String body) {
        server.enqueue(
                new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    @Test
    void clientMapsRequestAndReportsValidatedUsage() throws Exception {
        response(RESPONSE);
        JevResult result = client.decide(request());
        var sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals("Bearer test-key", sent.getHeader("Authorization"));
        var payload = mapper.readTree(sent.getBody().readUtf8());
        assertEquals("jev-1.13", payload.path("model").asText());
        assertEquals("Payments", payload.at("/questions/team/criteria/billing").asText());
        assertFalse(payload.has("provider"));
        assertEquals("billing", result.answers().get("team").choice());
        assertEquals("typesafe/jev-test", result.model());
        assertEquals(40L, result.usage().inputTokens());
        assertEquals("0.00001", result.usage().cost().stripTrailingZeros().toPlainString());
        assertEquals("USD", result.usage().currency());
        assertFalse(mapper.writeValueAsString(result).contains("test-key"));
    }

    @Test
    void mapsBooleanAndScoreAnswers() throws Exception {
        JevRequest input =
                new JevRequest(
                        "jev-1.13",
                        "State",
                        Map.of(
                                "urgent",
                                        new JevQuestion(
                                                JevQuestion.Type.BOOLEAN, "Urgent?", null, null),
                                "severity",
                                        new JevQuestion(
                                                JevQuestion.Type.SCORE,
                                                "Rate",
                                                null,
                                                List.of("Low", "High"))));
        response(
                """
            {"model":"jev-test","answers":{"urgent":{"type":"noul","noul":0.8},
            "severity":{"type":"score","score":0.4}}}
            """);
        JevResult result = client.decide(input);
        assertEquals(0.8, result.answers().get("urgent").probability());
        assertEquals(0.4, result.answers().get("severity").score());
        assertNull(result.usage().cost());
        assertEquals(
                "noul",
                mapper.readTree(server.takeRequest().getBody().readUtf8())
                        .at("/questions/urgent/type")
                        .asText());
    }

    @Test
    void invalidInputDoesNotSendRequests() {
        assertThrows(
                NonRetryableException.class,
                () -> client.decide(new JevRequest("m", "", Map.of())));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void rejectsInvalidAnswersAndMalformedJson() {
        for (String body :
                List.of(
                        RESPONSE.replace("\"choice\":\"billing\"", "\"choice\":\"unknown\""),
                        RESPONSE.replace("0.9", "1.5"),
                        RESPONSE.replace("\"team\":", "\"other\":"),
                        "{broken")) {
            response(body);
            assertThrows(NonRetryableException.class, () -> client.decide(request()));
        }
    }

    @Test
    void redactsHttpErrorsAndDoesNotFollowRedirectsOrRetry() {
        for (int code : List.of(302, 401, 429, 500)) {
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(code)
                            .setHeader("Location", server.url("/redirect"))
                            .setBody("private-provider-error test-key"));
            RuntimeException error =
                    assertThrows(RuntimeException.class, () -> client.decide(request()));
            assertEquals("Jev HTTP status " + code, error.getMessage());
            assertNull(error.getCause());
        }
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void concurrentDecisionsDoNotShareAnswers() throws Exception {
        response(RESPONSE);
        response(RESPONSE);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> client.decide(request()));
            var second = executor.submit(() -> client.decide(request()));
            assertEquals("billing", first.get(5, TimeUnit.SECONDS).answers().get("team").choice());
            assertEquals("billing", second.get(5, TimeUnit.SECONDS).answers().get("team").choice());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void genericJsonRoundTrip() throws Exception {
        assertEquals(
                request(), mapper.readValue(mapper.writeValueAsBytes(request()), JevRequest.class));
    }
}
