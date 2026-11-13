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
package org.conductoross.conductor.ai.mcp.server;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.mcp.McpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpServerResourceTest {

    private static final String DISCOVER =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{\"_meta\":"
                    + "{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                    + "\"io.modelcontextprotocol/clientCapabilities\":{}}}}";

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        McpServerProtocol protocol = new McpServerProtocol(mapper, Map.of());
        mvc = MockMvcBuilders.standaloneSetup(new McpServerResource(protocol)).build();
    }

    private MvcResult postAsync(String body, String accept) throws Exception {
        MvcResult started =
                mvc.perform(
                                post("/api/mcp")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Accept", accept)
                                        .header("MCP-Protocol-Version", "2026-07-28")
                                        .header("Mcp-Method", "server/discover")
                                        .content(body))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        return mvc.perform(asyncDispatch(started)).andReturn();
    }

    @Test
    void bodyWithoutContentType_isReadAsUtf8() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        McpServerProtocol protocol =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TOOLS_LIST,
                                call -> {
                                    seen.set(call.params().get("cursor").asString());
                                    return reactor.core.publisher.Mono.just(
                                            mapper.createObjectNode());
                                }));
        MockMvc utf8 = MockMvcBuilders.standaloneSetup(new McpServerResource(protocol)).build();
        String body =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{"
                        + "\"cursor\":\"été\",\"_meta\":"
                        + "{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                        + "\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
        MvcResult started =
                utf8.perform(
                                post("/api/mcp")
                                        .header("MCP-Protocol-Version", "2026-07-28")
                                        .header("Mcp-Method", "tools/list")
                                        .content(body.getBytes(StandardCharsets.UTF_8)))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        utf8.perform(asyncDispatch(started)).andExpect(status().isOk());
        assertEquals("été", seen.get());
    }

    @Test
    void discover_isServedAsJson() throws Exception {
        MvcResult started =
                mvc.perform(
                                post("/api/mcp")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("MCP-Protocol-Version", "2026-07-28")
                                        .header("Mcp-Method", "server/discover")
                                        .content(DISCOVER))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result.supportedVersions[0]").value("2026-07-28"));
    }

    @Test
    void clientAcceptsSse_serverAnswersJson() throws Exception {
        MvcResult result = postAsync(DISCOVER, "application/json, text/event-stream");
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(
                result.getResponse().getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE),
                result.getResponse().getContentType());
    }

    @Test
    void protocolErrorStatus_isPassedThrough() throws Exception {
        MvcResult started =
                mvc.perform(
                                post("/api/mcp")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(DISCOVER))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(-32020));
    }

    @Test
    void notification_gets202AndNoBody() throws Exception {
        String notification = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        MvcResult started =
                mvc.perform(
                                post("/api/mcp")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(notification))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    @Test
    void get_is405() throws Exception {
        mvc.perform(get("/api/mcp"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"));
    }
}
