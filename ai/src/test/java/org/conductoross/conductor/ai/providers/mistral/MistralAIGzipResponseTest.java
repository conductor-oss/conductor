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
package org.conductoross.conductor.ai.providers.mistral;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the Mistral provider response decoding.
 *
 * <p>The client must let OkHttp manage content compression. When the {@code Accept-Encoding} header
 * was set manually, OkHttp stopped decompressing the gzip response transparently, so the raw gzip
 * bytes were handed to Jackson and parsing failed with {@code Illegal character ((CTRL-CHAR, code
 * 31))} (0x1F is the first byte of the gzip magic number). This test serves a gzip-encoded chat
 * completion and asserts it is decoded correctly.
 */
class MistralAIGzipResponseTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void chatCompletionDecodesGzipEncodedResponse() throws Exception {
        String json =
                "{\"id\":\"cmpl-1\",\"object\":\"chat.completion\",\"created\":1,"
                        + "\"model\":\"mistral-small-latest\","
                        + "\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"Hallo\"},"
                        + "\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";

        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(gzipped)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        }
        Buffer body = new Buffer();
        body.write(gzipped.toByteArray());

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Content-Encoding", "gzip")
                        .setBody(body));

        MistralAIConfiguration config = new MistralAIConfiguration();
        config.setApiKey("test-api-key");
        config.setBaseURL(server.url("/").toString().replaceAll("/$", ""));
        MistralAI mistralAI = new MistralAI(config);

        ChatCompletion input = new ChatCompletion();
        input.setModel("mistral-small-latest");
        input.setMaxTokens(50);

        ChatModel chatModel = mistralAI.getChatModel();
        ChatResponse response =
                chatModel.call(
                        new Prompt(
                                List.of(new UserMessage("hi")), mistralAI.getChatOptions(input)));

        assertEquals("Hallo", response.getResult().getOutput().getText());
    }
}
