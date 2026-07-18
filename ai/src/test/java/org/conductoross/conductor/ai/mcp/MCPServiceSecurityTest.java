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
package org.conductoross.conductor.ai.mcp;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.http.OutboundTargetPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies MCP transport boundaries against an actual HTTP listener. */
class MCPServiceSecurityTest {

    private MockWebServer server;
    private MCPService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OutboundTargetPolicy policy = new OutboundTargetPolicy();
        policy.setAllowedOrigins(List.of(server.url("/").toString().replaceAll("/$", "")));
        policy.setAllowPrivateNetworks(true);
        service = new MCPService(new OkHttpClient(), policy);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void rejectsOversizedMcpResponsesBeforeNormalizingToolOutput() {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("x".repeat(1024 * 1024 + 1)));

        assertThatThrownBy(
                        () ->
                                service.callTool(
                                        server.url("/mcp").toString(),
                                        "echo_large",
                                        Map.of(),
                                        Map.of()))
                .hasMessageContaining("1 MiB payload limit");
    }

    @Test
    void refusesAnUnconfiguredTargetBeforeSendingTheRequest() {
        assertThatThrownBy(
                        () ->
                                service.callTool(
                                        "http://example.invalid/mcp", "echo", Map.of(), Map.of()))
                .hasMessageContaining("not allow-listed");

        assertThat(server.getRequestCount()).isZero();
    }
}
