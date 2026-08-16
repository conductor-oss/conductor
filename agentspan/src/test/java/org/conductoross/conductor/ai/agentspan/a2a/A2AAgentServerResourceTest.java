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
package org.conductoross.conductor.ai.agentspan.a2a;

import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.server.A2AServerException;
import org.conductoross.conductor.ai.a2a.server.A2AServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests HTTP routing and delegation for A2AAgentServerResource. Dispatch behavior (switch logic,
 * JSON-RPC error mapping, streaming) is covered in A2ANativeAgentFacadeTest.
 */
class A2AAgentServerResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private A2ANativeAgentFacade facade;
    private A2AAgentServerResource resource;

    @BeforeEach
    void setUp() {
        facade = mock(A2ANativeAgentFacade.class);
        resource = new A2AAgentServerResource(facade, new A2AServerProperties());
    }

    private JsonNode rpc(String method, String paramsJson) {
        try {
            return objectMapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\""
                            + method
                            + "\",\"params\":"
                            + paramsJson
                            + "}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- jsonRpc delegates to facade ---------------------------------------------------------

    @Test
    void jsonRpc_delegatesToFacadeDispatch() {
        JsonNode fakeResult = objectMapper.createObjectNode().put("ok", true);
        ResponseEntity<JsonNode> fakeResponse = ResponseEntity.ok(fakeResult);
        when(facade.dispatch(eq("greeter"), any())).thenReturn(fakeResponse);

        JsonNode request = rpc("message/send", "{\"message\":{\"parts\":[]}}");
        resource.jsonRpc("greeter", request);

        verify(facade).dispatch(eq("greeter"), eq(request));
    }

    @Test
    void jsonRpc_streamMethod_returnsSseEmitter() {
        SseEmitter emitter = new SseEmitter();
        when(facade.dispatch(eq("greeter"), any())).thenReturn(emitter);

        Object result =
                resource.jsonRpc(
                        "greeter",
                        rpc(
                                "message/stream",
                                "{\"message\":{\"role\":\"user\",\"kind\":\"message\","
                                        + "\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}"));

        assertInstanceOf(SseEmitter.class, result);
    }

    // ---- agent card --------------------------------------------------------------------------

    @Test
    void agentCard_servedFromRequest() {
        AgentCard card = new AgentCard();
        card.setName("greeter");
        when(facade.agentCard(eq("greeter"), any())).thenReturn(card);

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRequestURL())
                .thenReturn(
                        new StringBuffer(
                                "http://host:8080/api/a2a/agent/greeter/.well-known/agent-card.json"));

        ResponseEntity<?> response = resource.agentCard("greeter", httpRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("greeter", ((AgentCard) response.getBody()).getName());
    }

    @Test
    void agentCard_notFound_returns404() {
        when(facade.agentCard(eq("unknown"), any()))
                .thenThrow(A2AServerException.notFound("not found"));

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRequestURL())
                .thenReturn(
                        new StringBuffer(
                                "http://host:8080/api/a2a/agent/unknown/.well-known/agent-card.json"));

        ResponseEntity<?> response = resource.agentCard("unknown", httpRequest);

        assertEquals(404, response.getStatusCode().value());
    }

    // ---- URL routing -------------------------------------------------------------------------

    @Test
    void rpcPathSuffix_routesToJsonRpcHandler() throws Exception {
        JsonNode fakeResult = objectMapper.createObjectNode().put("ok", true);
        when(facade.dispatch(eq("greeter"), any())).thenReturn(ResponseEntity.ok(fakeResult));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(resource).build();
        mvc.perform(
                        post("/api/a2a/agent/greeter/rpc")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\","
                                                + "\"params\":{\"message\":{\"role\":\"user\",\"kind\":\"message\","
                                                + "\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}}"))
                .andExpect(status().isOk());
    }
}
