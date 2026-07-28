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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.a2a.A2ALogging;
import org.conductoross.conductor.ai.a2a.A2AMetrics;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.server.A2AServerException;
import org.conductoross.conductor.ai.a2a.server.A2AServerProperties;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A2A server endpoints for native Conductor agents — exposes agentspan agents as A2A agents.
 *
 * <ul>
 *   <li>{@code GET /api/a2a/agent} — list all exposed native agents (non-spec convenience).
 *   <li>{@code GET /api/a2a/agent/{name}/.well-known/agent-card.json} — agent card discovery.
 *   <li>{@code POST /api/a2a/agent/{name}} — JSON-RPC 2.0: {@code message/send}, {@code tasks/get},
 *       {@code tasks/cancel}, {@code message/stream}.
 * </ul>
 *
 * <p>Gated on both {@code conductor.a2a.server.enabled=true} and {@code agentspan.embedded=true}.
 * Paths use {@code conductor.a2a.server.agentBasePath} (default {@code /api/a2a/agent}).
 */
@RestController
@ConditionalOnProperty(
        name = {"conductor.a2a.server.enabled", "agentspan.embedded"},
        havingValue = "true")
public class A2AAgentServerResource {

    private static final Logger log = LoggerFactory.getLogger(A2AAgentServerResource.class);

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final A2ANativeAgentFacade facade;
    private final A2AServerProperties properties;

    private final ExecutorService streamExecutor =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "a2a-agent-stream");
                        t.setDaemon(true);
                        return t;
                    });

    public A2AAgentServerResource(A2ANativeAgentFacade facade, A2AServerProperties properties) {
        this.facade = facade;
        this.properties = properties;
    }

    @GetMapping(
            value = "${conductor.a2a.server.agentBasePath:/api/a2a/agent}",
            produces = "application/json")
    public ResponseEntity<?> listAgents(HttpServletRequest httpRequest) {
        String base = requestBaseUrl(httpRequest);
        List<?> agents =
                facade.exposedAgents().stream()
                        .map(
                                (AgentSummary s) ->
                                        java.util.Map.of(
                                                "name", s.getName(),
                                                "url", facade.agentUrl(s.getName(), base),
                                                "agentCard",
                                                        facade.agentCardUrl(s.getName(), base)))
                        .collect(Collectors.toList());
        return ResponseEntity.ok(agents);
    }

    @GetMapping(
            value = {
                "${conductor.a2a.server.agentBasePath:/api/a2a/agent}/{agent}/.well-known/agent-card.json",
                "${conductor.a2a.server.agentBasePath:/api/a2a/agent}/{agent}/.well-known/agent.json"
            },
            produces = "application/json")
    public ResponseEntity<?> agentCard(
            @PathVariable("agent") String agentName, HttpServletRequest httpRequest) {
        try {
            AgentCard card = facade.agentCard(agentName, requestBaseUrl(httpRequest));
            return ResponseEntity.ok(card);
        } catch (A2AServerException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping(
            value = {
                "${conductor.a2a.server.agentBasePath:/api/a2a/agent}/{agent}",
                "${conductor.a2a.server.agentBasePath:/api/a2a/agent}/{agent}/rpc"
            },
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object jsonRpc(
            @PathVariable("agent") String agentName,
            @RequestBody(required = false) JsonNode request) {
        JsonNode id = request == null ? null : request.get("id");
        try (A2ALogging.Scope scope = A2ALogging.of(A2ALogging.AGENT, agentName)) {
            if (request == null || !request.hasNonNull("method")) {
                return ResponseEntity.ok(error(id, -32600, "Invalid Request: missing 'method'"));
            }
            String method = request.get("method").asText();
            scope.add(A2ALogging.METHOD, method);
            JsonNode params = request.get("params");
            if ("message/stream".equals(method) || "tasks/sendSubscribe".equals(method)) {
                return streamResponse(agentName, params, id);
            }
            Object result;
            switch (method) {
                case "message/send":
                case "tasks/send":
                    {
                        A2AMetrics.serverRequest("message/send");
                        A2AMessage message = parseMessage(params);
                        scope.add(A2ALogging.MESSAGE_ID, message.getMessageId())
                                .add(A2ALogging.CONTEXT_ID, message.getContextId())
                                .add(A2ALogging.REMOTE_TASK_ID, message.getTaskId());
                        result = facade.sendMessage(agentName, message);
                        break;
                    }
                case "tasks/get":
                    {
                        A2AMetrics.serverRequest(method);
                        String getId = taskId(params);
                        scope.add(A2ALogging.REMOTE_TASK_ID, getId);
                        result = facade.getTask(agentName, getId);
                        break;
                    }
                case "tasks/cancel":
                    {
                        A2AMetrics.serverRequest(method);
                        String cancelId = taskId(params);
                        scope.add(A2ALogging.REMOTE_TASK_ID, cancelId);
                        result = facade.cancelTask(agentName, cancelId);
                        break;
                    }
                default:
                    return ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
            }
            return ResponseEntity.ok(success(id, result));
        } catch (A2AServerException e) {
            return ResponseEntity.ok(error(id, e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.warn("A2A agent server error for {}: {}", agentName, e.getMessage());
            return ResponseEntity.ok(error(id, -32603, "Internal error: " + e.getMessage()));
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Object streamResponse(String agentName, JsonNode params, JsonNode id) {
        if (!facade.isExposed(agentName)) {
            return ResponseEntity.ok(error(id, -32001, "agent not found: " + agentName));
        }
        A2AMessage message;
        try {
            message = parseMessage(params);
        } catch (A2AServerException e) {
            return ResponseEntity.ok(error(id, e.getCode(), e.getMessage()));
        }
        A2AMetrics.serverRequest("message/stream");
        SseEmitter emitter =
                new SseEmitter(properties.getStreamMaxDurationSeconds() * 1000L + 5000L);
        streamExecutor.submit(
                () -> {
                    try {
                        facade.streamMessage(agentName, message, id, emitter::send);
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn(
                                "A2A message/stream for agent {} ended with error: {}",
                                agentName,
                                e.getMessage());
                        emitter.completeWithError(e);
                    }
                });
        return emitter;
    }

    private A2AMessage parseMessage(JsonNode params) {
        if (params == null || !params.has("message")) {
            throw A2AServerException.invalidParams("message/send requires params.message");
        }
        return objectMapper.convertValue(params.get("message"), A2AMessage.class);
    }

    private String taskId(JsonNode params) {
        if (params == null || !params.hasNonNull("id")) {
            throw A2AServerException.invalidParams("requires params.id (the task/execution id)");
        }
        return params.get("id").asText();
    }

    private String requestBaseUrl(HttpServletRequest request) {
        if (properties.getPublicUrl() != null && !properties.getPublicUrl().isBlank()) {
            return properties.getPublicUrl();
        }
        return UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString())
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    private JsonNode success(JsonNode id, Object result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", objectMapper.valueToTree(result));
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode err = objectMapper.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        response.set("error", err);
        return response;
    }
}
