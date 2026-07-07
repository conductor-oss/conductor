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
package org.conductoross.conductor.ai.a2a;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.A2AService.SendResult;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

class A2AServiceTest {

    private MockWebServer server;
    private A2AService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client =
                new OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .writeTimeout(2, TimeUnit.SECONDS)
                        .build();
        // MockWebServer binds to loopback (127.0.0.1); bypass SSRF for unit tests.
        service = spy(new A2AService(client));
        doNothing().when(service).validateAgentUrl(anyString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private String endpoint() {
        return server.url("/").toString();
    }

    private MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private A2AMessage userText(String text) {
        A2AMessage message = new A2AMessage();
        Part part = new Part();
        part.setKind("text");
        part.setText(text);
        message.setParts(List.of(part));
        message.setRole("user");
        message.setMessageId("m1");
        message.setKind("message");
        return message;
    }

    @Test
    void getAgentCard_parsesCardFromWellKnownPath() throws Exception {
        server.enqueue(
                json(
                        """
                        {
                          "name": "Currency Agent",
                          "description": "Converts currency",
                          "url": "http://agent",
                          "version": "1.0.0",
                          "capabilities": { "streaming": true },
                          "skills": [ { "id": "convert", "name": "Convert" } ]
                        }
                        """));

        AgentCard card = service.getAgentCard(endpoint(), null);

        assertEquals("Currency Agent", card.getName());
        assertTrue(card.getCapabilities().isStreaming());
        assertEquals(1, card.getSkills().size());
        assertEquals("convert", card.getSkills().get(0).getId());

        RecordedRequest request = server.takeRequest();
        assertEquals("/.well-known/agent-card.json", request.getPath());
    }

    @Test
    void getAgentCard_fallsBackToLegacyAgentJson() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(json("{ \"name\": \"Legacy Agent\" }"));

        AgentCard card = service.getAgentCard(endpoint(), null);

        assertEquals("Legacy Agent", card.getName());
        assertEquals("/.well-known/agent-card.json", server.takeRequest().getPath());
        assertEquals("/.well-known/agent.json", server.takeRequest().getPath());
    }

    @Test
    void sendMessage_returnsTask() throws Exception {
        server.enqueue(
                json(
                        """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "kind": "task",
                            "id": "t1",
                            "contextId": "c1",
                            "status": { "state": "completed" },
                            "artifacts": [ { "artifactId": "a1", "parts": [ { "kind": "text", "text": "42" } ] } ]
                          }
                        }
                        """));

        SendResult result = service.sendMessage(endpoint(), userText("convert"), null, null);

        assertTrue(result.isTask());
        assertEquals(TaskState.COMPLETED, result.getTask().getStatus().getState());

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"method\":\"message/send\""), body);
    }

    @Test
    void sendMessage_returnsDirectMessage() throws Exception {
        server.enqueue(
                json(
                        """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "kind": "message",
                            "role": "agent",
                            "messageId": "r1",
                            "parts": [ { "kind": "text", "text": "hello" } ]
                          }
                        }
                        """));

        SendResult result = service.sendMessage(endpoint(), userText("hi"), null, null);

        assertFalse(result.isTask());
        assertEquals("hello", result.getMessage().getParts().get(0).getText());
    }

    @Test
    void getTask_parsesState() throws Exception {
        server.enqueue(
                json(
                        """
                        { "jsonrpc": "2.0", "id": 1, "result": { "kind": "task", "id": "t1", "status": { "state": "working" } } }
                        """));

        A2ATask task = service.getTask(endpoint(), "t1", 5, null);

        assertEquals(TaskState.WORKING, task.getStatus().getState());
    }

    @Test
    void cancelTask_parsesCanceledState() throws Exception {
        server.enqueue(
                json(
                        """
                        { "jsonrpc": "2.0", "id": 1, "result": { "kind": "task", "id": "t1", "status": { "state": "canceled" } } }
                        """));

        A2ATask task = service.cancelTask(endpoint(), "t1", null);

        assertEquals(TaskState.CANCELED, task.getStatus().getState());
    }

    @Test
    void jsonRpcError_withTerminalCode_isNonRetryable() {
        server.enqueue(
                json(
                        "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"error\": { \"code\": -32601, \"message\": \"method not found\" } }"));

        assertThrows(
                NonRetryableException.class, () -> service.getTask(endpoint(), "t1", null, null));
    }

    @Test
    void jsonRpcError_withTransientCode_isRetryable() {
        server.enqueue(
                json(
                        "{ \"jsonrpc\": \"2.0\", \"id\": 1, \"error\": { \"code\": -32603, \"message\": \"internal\" } }"));

        assertThrows(A2AException.class, () -> service.getTask(endpoint(), "t1", null, null));
    }

    @Test
    void http500_isRetryable() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        A2AException ex =
                assertThrows(
                        A2AException.class,
                        () -> service.sendMessage(endpoint(), userText("hi"), null, null));
        assertNotNull(ex.getMessage());
    }

    @Test
    void http400_isNonRetryable() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad"));

        assertThrows(
                NonRetryableException.class,
                () -> service.sendMessage(endpoint(), userText("hi"), null, null));
    }

    // SSRF validation tests — use the real (non-spied) service so validation runs.

    @Test
    void ssrfValidation_rejectsLoopback() {
        A2AService real = new A2AService(new okhttp3.OkHttpClient());
        assertThrows(
                NonRetryableException.class,
                () -> real.validateAgentUrl("http://127.0.0.1:8080/agent"));
    }

    @Test
    void ssrfValidation_rejectsPrivateIp() {
        A2AService real = new A2AService(new okhttp3.OkHttpClient());
        // 10.0.0.1 is RFC-1918 site-local
        assertThrows(
                NonRetryableException.class, () -> real.validateAgentUrl("http://10.0.0.1/agent"));
    }

    @Test
    void ssrfValidation_rejectsNonHttp() {
        A2AService real = new A2AService(new okhttp3.OkHttpClient());
        assertThrows(
                NonRetryableException.class, () -> real.validateAgentUrl("file:///etc/passwd"));
    }

    @Test
    void ssrfValidation_blocksIpv6UniqueLocalAndMetadata() {
        A2AService real = new A2AService(new okhttp3.OkHttpClient());
        // IPv6 ULA (fc00::/7) — Java's isSiteLocalAddress() does NOT cover these.
        assertThrows(
                NonRetryableException.class,
                () -> real.validateAgentUrl("http://[fd12:3456:789a::1]/agent"));
        // AWS IPv6 metadata endpoint stays blocked even when private networks are allowed.
        A2AService permissive = new A2AService(new okhttp3.OkHttpClient(), true);
        assertThrows(
                NonRetryableException.class,
                () -> permissive.validateAgentUrl("http://[fd00:ec2::254]/latest/meta-data/"));
    }

    @Test
    void ssrfValidation_allowPrivateNetwork_permitsLoopbackButNotMetadata() {
        A2AService permissive = new A2AService(new okhttp3.OkHttpClient(), true);
        // Loopback/private now allowed (e.g. an agent on a trusted private network or localhost).
        permissive.validateAgentUrl("http://127.0.0.1:8080/agent");
        permissive.validateAgentUrl("http://10.0.0.1/agent");
        // Cloud metadata stays blocked even with the flag on.
        assertThrows(
                NonRetryableException.class,
                () -> permissive.validateAgentUrl("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void ssrfValidation_acceptsPublicUrl() {
        A2AService real = new A2AService(new okhttp3.OkHttpClient());
        // Should not throw — 93.184.216.34 is example.com (IANA), a public IP.
        // If DNS is unavailable in CI this will throw A2AException (not NonRetryableException).
        try {
            real.validateAgentUrl("https://example.com/agent");
        } catch (NonRetryableException e) {
            throw new AssertionError("Public URL should not be blocked by SSRF check", e);
        } catch (Exception ignored) {
            // DNS not reachable in this environment — acceptable.
        }
    }
}
