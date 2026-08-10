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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.*;

class GeminiApiTest {

    private MockWebServer server;
    private GeminiApi api;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build();
        // Use the server's host/port as customBase so requests go to MockWebServer
        String base = server.url("").toString().replaceAll("/$", "");
        api = GeminiApi.forApiKey(client, "test-key", base);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void generateContentReturnsText() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(
                                """
                    {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]},
                    "finishReason":"STOP"}],
                    "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":3}}""")
                        .addHeader("Content-Type", "application/json"));

        var contents =
                List.of(new GeminiApi.Content("user", List.of(GeminiApi.Part.text("Say hi"))));
        GeminiApi.GenerateContentResponse resp =
                api.generateContent("gemini-2.5-flash", contents, null);

        assertEquals("Hello", resp.text());
        assertEquals("STOP", resp.finishReason());
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("gemini-2.5-flash:generateContent"));
        assertTrue(req.getPath().contains("key=test-key"));
    }

    @Test
    void embedContentReturnsValues() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody("""
                    {"embedding":{"values":[0.1,0.2,0.3]}}""")
                        .addHeader("Content-Type", "application/json"));

        GeminiApi.EmbedContentResponse resp =
                api.embedContent("text-embedding-004", "hello world", null);

        assertNotNull(resp.embedding());
        assertEquals(3, resp.embedding().values().size());
        assertEquals(0.1f, resp.embedding().values().get(0), 0.001f);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("text-embedding-004:embedContent"));
    }

    @Test
    void generateContentThrowsOnError() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(400)
                        .setBody("{\"error\":{\"message\":\"Invalid model\"}}"));
        var contents = List.of(new GeminiApi.Content("user", List.of(GeminiApi.Part.text("test"))));
        assertThrows(
                java.io.IOException.class, () -> api.generateContent("bad-model", contents, null));
    }
}
