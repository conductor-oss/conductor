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

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class A2AAgentServerResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private A2ANativeAgentFacade facade;
    private A2AServerProperties properties;
    private A2AAgentServerResource resource;

    @BeforeEach
    void setUp() {
        facade = mock(A2ANativeAgentFacade.class);
        properties = new A2AServerProperties();
        resource = new A2AAgentServerResource(facade, properties);
    }

    private A2ATask task(String id, String state) {
        A2ATask task = new A2ATask();
        task.setId(id);
        TaskStatus status = new TaskStatus();
        status.setState(state);
        task.setStatus(status);
        return task;
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

    @SuppressWarnings("unchecked")
    private ResponseEntity<JsonNode> call(String agentName, JsonNode request) {
        return (ResponseEntity<JsonNode>) resource.jsonRpc(agentName, request);
    }

    // ---- message/send ------------------------------------------------------------------------

    @Test
    void messageSend_dispatchesAndReturnsResult() {
        when(facade.sendMessage(eq("greeter"), any(A2AMessage.class)))
                .thenReturn(task("exec-1", TaskState.WORKING));

        JsonNode request =
                rpc(
                        "message/send",
                        "{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\","
                                + "\"parts\":[{\"kind\":\"text\",\"text\":\"hello\"}]}}");
        ResponseEntity<JsonNode> response = call("greeter", request);

        assertEquals(1, response.getBody().get("id").asInt());
        assertEquals("exec-1", response.getBody().get("result").get("id").asText());
        verify(facade).sendMessage(eq("greeter"), any(A2AMessage.class));
    }

    @Test
    void tasksSend_alias_dispatchesToSendMessage() {
        when(facade.sendMessage(eq("greeter"), any(A2AMessage.class)))
                .thenReturn(task("exec-1", TaskState.WORKING));

        ResponseEntity<JsonNode> response =
                call(
                        "greeter",
                        rpc(
                                "tasks/send",
                                "{\"message\":{\"role\":\"user\",\"kind\":\"message\","
                                        + "\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}"));

        assertEquals("exec-1", response.getBody().get("result").get("id").asText());
        verify(facade).sendMessage(eq("greeter"), any(A2AMessage.class));
    }

    // ---- tasks/get ---------------------------------------------------------------------------

    @Test
    void tasksGet_dispatches() {
        when(facade.getTask("greeter", "exec-1")).thenReturn(task("exec-1", TaskState.COMPLETED));

        ResponseEntity<JsonNode> response =
                call("greeter", rpc("tasks/get", "{\"id\":\"exec-1\"}"));

        assertEquals(
                TaskState.COMPLETED,
                response.getBody().get("result").get("status").get("state").asText());
    }

    // ---- tasks/cancel ------------------------------------------------------------------------

    @Test
    void tasksCancel_dispatches() {
        when(facade.cancelTask("greeter", "exec-1"))
                .thenReturn(task("exec-1", TaskState.CANCELED));

        call("greeter", rpc("tasks/cancel", "{\"id\":\"exec-1\"}"));

        verify(facade).cancelTask("greeter", "exec-1");
    }

    // ---- error handling ----------------------------------------------------------------------

    @Test
    void unknownMethod_returnsMethodNotFound() {
        ResponseEntity<JsonNode> response = call("greeter", rpc("foo/bar", "{}"));
        assertEquals(-32601, response.getBody().get("error").get("code").asInt());
    }

    @Test
    void missingMethod_returnsInvalidRequest() throws Exception {
        JsonNode request = objectMapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1}");
        ResponseEntity<JsonNode> response = call("greeter", request);
        assertEquals(-32600, response.getBody().get("error").get("code").asInt());
    }

    @Test
    void serverException_mapsToJsonRpcError() {
        when(facade.getTask("greeter", "missing"))
                .thenThrow(A2AServerException.notFound("not found"));

        ResponseEntity<JsonNode> response =
                call("greeter", rpc("tasks/get", "{\"id\":\"missing\"}"));

        assertEquals(-32001, response.getBody().get("error").get("code").asInt());
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
                .thenReturn(new StringBuffer("http://host:8080/api/a2a/agent/unknown/.well-known/agent-card.json"));

        ResponseEntity<?> response = resource.agentCard("unknown", httpRequest);

        assertEquals(404, response.getStatusCode().value());
    }

    // ---- streaming ---------------------------------------------------------------------------

    @Test
    void messageStream_returnsSseEmitter() {
        when(facade.isExposed("greeter")).thenReturn(true);

        Object result =
                resource.jsonRpc(
                        "greeter",
                        rpc(
                                "message/stream",
                                "{\"message\":{\"role\":\"user\",\"kind\":\"message\","
                                        + "\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}"));

        assertInstanceOf(SseEmitter.class, result);
    }

    @Test
    void tasksSendSubscribe_alias_returnsSseEmitter() {
        when(facade.isExposed("greeter")).thenReturn(true);

        Object result =
                resource.jsonRpc(
                        "greeter",
                        rpc(
                                "tasks/sendSubscribe",
                                "{\"message\":{\"role\":\"user\",\"kind\":\"message\","
                                        + "\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}"));

        assertInstanceOf(SseEmitter.class, result);
    }

    // ---- URL routing -------------------------------------------------------------------------

    @Test
    void rpcPathSuffix_routesToJsonRpcHandler() throws Exception {
        when(facade.sendMessage(eq("greeter"), any(A2AMessage.class)))
                .thenReturn(task("exec-1", TaskState.WORKING));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(resource).build();
        mvc.perform(
                        post("/api/a2a/agent/greeter/rpc")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\","
                                                + "\"params\":{\"message\":{\"role\":\"user\",\"kind\":\"message\","
                                                + "\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("exec-1"));
    }
}
