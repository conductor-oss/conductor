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
package org.conductoross.conductor.ai.a2a.server;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
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

class A2AServerResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private A2AWorkflowAgent agent;
    private A2AServerProperties properties;
    private A2AServerResource resource;

    @BeforeEach
    void setUp() {
        agent = mock(A2AWorkflowAgent.class);
        properties = new A2AServerProperties();
        resource = new A2AServerResource(agent, properties);
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
            String body =
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\""
                            + method
                            + "\",\"params\":"
                            + paramsJson
                            + "}";
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * jsonRpc returns Object (SSE emitter or ResponseEntity); the non-stream paths are the latter.
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<JsonNode> call(String workflow, JsonNode request) {
        return (ResponseEntity<JsonNode>) resource.jsonRpc(workflow, request);
    }

    @Test
    void messageSend_dispatchesAndReturnsResult() {
        when(agent.sendMessage(eq("order_pizza"), any(A2AMessage.class)))
                .thenReturn(task("wf-1", TaskState.WORKING));

        JsonNode request =
                rpc(
                        "message/send",
                        "{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}");
        ResponseEntity<JsonNode> response = call("order_pizza", request);

        JsonNode body = response.getBody();
        assertEquals(1, body.get("id").asInt());
        assertEquals("wf-1", body.get("result").get("id").asText());
        verify(agent).sendMessage(eq("order_pizza"), any(A2AMessage.class));
    }

    @Test
    void tasksGet_dispatches() {
        when(agent.getTask("order_pizza", "wf-1")).thenReturn(task("wf-1", TaskState.COMPLETED));

        ResponseEntity<JsonNode> response =
                call("order_pizza", rpc("tasks/get", "{\"id\":\"wf-1\"}"));

        assertEquals(
                TaskState.COMPLETED,
                response.getBody().get("result").get("status").get("state").asText());
    }

    @Test
    void tasksCancel_dispatches() {
        when(agent.cancelTask("order_pizza", "wf-1")).thenReturn(task("wf-1", TaskState.CANCELED));

        call("order_pizza", rpc("tasks/cancel", "{\"id\":\"wf-1\"}"));

        verify(agent).cancelTask("order_pizza", "wf-1");
    }

    @Test
    void unknownMethod_returnsMethodNotFound() {
        ResponseEntity<JsonNode> response = call("order_pizza", rpc("foo/bar", "{}"));
        assertEquals(-32601, response.getBody().get("error").get("code").asInt());
    }

    @Test
    void missingMethod_returnsInvalidRequest() {
        JsonNode request;
        try {
            request = objectMapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ResponseEntity<JsonNode> response = call("order_pizza", request);
        assertEquals(-32600, response.getBody().get("error").get("code").asInt());
    }

    @Test
    void serverException_mapsToJsonRpcError() {
        when(agent.getTask("order_pizza", "missing"))
                .thenThrow(A2AServerException.notFound("not found"));

        ResponseEntity<JsonNode> response =
                call("order_pizza", rpc("tasks/get", "{\"id\":\"missing\"}"));

        assertEquals(-32001, response.getBody().get("error").get("code").asInt());
    }

    @Test
    void agentCard_servedFromRequest() {
        AgentCard card = new AgentCard();
        card.setName("order_pizza");
        when(agent.agentCard(eq("order_pizza"), any())).thenReturn(card);

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRequestURL())
                .thenReturn(
                        new StringBuffer(
                                "http://host:8080/a2a/order_pizza/.well-known/agent-card.json"));

        ResponseEntity<?> response = resource.agentCard("order_pizza", httpRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("order_pizza", ((AgentCard) response.getBody()).getName());
    }

    // ---- A2A v0.2 backward-compat alias tests ------------------------------------------------

    @Test
    void tasksSend_alias_dispatchesToSendMessage() {
        when(agent.sendMessage(eq("order_pizza"), any(A2AMessage.class)))
                .thenReturn(task("wf-1", TaskState.WORKING));

        JsonNode request =
                rpc(
                        "tasks/send",
                        "{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}");
        ResponseEntity<JsonNode> response = call("order_pizza", request);

        assertEquals("wf-1", response.getBody().get("result").get("id").asText());
        verify(agent).sendMessage(eq("order_pizza"), any(A2AMessage.class));
    }

    @Test
    void tasksSend_alias_metricRecordedAsMessageSend() {
        when(agent.sendMessage(eq("order_pizza"), any(A2AMessage.class)))
                .thenReturn(task("wf-1", TaskState.WORKING));

        double beforeMessageSend = counterCount("message/send");
        double beforeTasksSend = counterCount("tasks/send");

        call(
                "order_pizza",
                rpc(
                        "tasks/send",
                        "{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}"));

        assertEquals(beforeMessageSend + 1, counterCount("message/send"), 0.01);
        assertEquals(beforeTasksSend, counterCount("tasks/send"), 0.01);
    }

    @Test
    void tasksSendSubscribe_alias_returnsSseEmitter() {
        when(agent.isExposed("order_pizza")).thenReturn(true);

        JsonNode request =
                rpc(
                        "tasks/sendSubscribe",
                        "{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\",\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}");
        // tasks/sendSubscribe routes to the streaming handler, which returns an SseEmitter
        // directly rather than a ResponseEntity, so the call(...) cast helper is not used here.
        Object result = resource.jsonRpc("order_pizza", request);

        assertInstanceOf(SseEmitter.class, result);
    }

    @Test
    void rpcPathSuffix_routesToJsonRpcHandler() throws Exception {
        when(agent.sendMessage(eq("order_pizza"), any(A2AMessage.class)))
                .thenReturn(task("wf-1", TaskState.WORKING));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(resource).build();
        mvc.perform(
                        post("/a2a/order_pizza/rpc")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\","
                                                + "\"params\":{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\","
                                                + "\"parts\":[{\"kind\":\"text\",\"text\":\"hi\"}]}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("wf-1"));
    }

    private double counterCount(String methodTag) {
        Counter counter =
                Monitors.getRegistry()
                        .find("a2a_server_requests")
                        .tag("method", methodTag)
                        .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
