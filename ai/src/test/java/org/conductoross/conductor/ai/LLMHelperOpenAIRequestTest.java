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
package org.conductoross.conductor.ai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.providers.openai.OpenAI;
import org.conductoross.conductor.ai.providers.openai.OpenAIConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;

import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LLMHelperOpenAIRequestTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void providerOptionsReachResponsesApiThroughHelper(boolean jsonOutput) throws Exception {
        var mapper = new ObjectMapperProvider().getObjectMapper();
        var captured = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/responses",
                exchange -> {
                    try (exchange) {
                        captured.set(mapper.readTree(exchange.getRequestBody()));
                        byte[] response =
                                """
                        {"id":"resp_test","model":"gpt-4o","status":"completed",
                         "output":[{"type":"message","role":"assistant",
                         "content":[{"type":"output_text","text":"{\\\"answer\\\":\\\"Paris\\\"}"}]}],
                         "usage":{"input_tokens":3,"output_tokens":4,"total_tokens":7}}
                        """
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length);
                        exchange.getResponseBody().write(response);
                    }
                });
        server.start();
        try {
            var httpClient = new OkHttpClient();
            var config =
                    new OpenAIConfiguration(
                            "test-key",
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            null,
                            httpClient);
            var input = new ChatCompletion();
            input.setModel("gpt-4o");
            input.setMaxTokens(128);
            input.setTopP(0.8);
            input.setPreviousResponseId("resp_previous");
            input.setReasoningEffort("low");
            input.setReasoningSummary("auto");
            input.setJsonOutput(jsonOutput);
            input.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Capital of France?"));

            var result =
                    new LLMHelper(null, List.of(), httpClient)
                            .chatComplete(
                                    new Task(),
                                    new OpenAI(config, httpClient),
                                    input,
                                    null,
                                    usage -> {});

            JsonNode sent = captured.get();
            assertNotNull(sent);
            assertEquals("gpt-4o", sent.path("model").asText());
            assertEquals(128, sent.path("max_output_tokens").asInt());
            assertEquals(0.8, sent.path("top_p").asDouble());
            assertEquals("resp_previous", sent.path("previous_response_id").asText());
            assertEquals("low", sent.path("reasoning").path("effort").asText());
            assertEquals("auto", sent.path("reasoning").path("summary").asText());
            assertEquals("Capital of France?", sent.path("input").get(0).path("content").asText());
            assertEquals(jsonOutput, sent.has("text"));
            if (jsonOutput) {
                assertEquals("json_object", sent.path("text").path("format").path("type").asText());
            }
            assertEquals(Map.of("answer", "Paris"), result.getResult());
            assertEquals("resp_test", result.getResponseId());
            assertEquals(7, result.getTokenUsed());
        } finally {
            server.stop(0);
        }
    }
}
