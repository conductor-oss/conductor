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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.a2a.A2ALogging;
import org.conductoross.conductor.ai.a2a.A2AMetrics;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.config.A2AServerEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A2A server endpoints — exposes Conductor workflows as A2A agents (one agent per workflow).
 *
 * <ul>
 *   <li>{@code GET {basePath}/{workflow}/.well-known/agent-card.json} (and {@code /agent.json}) —
 *       discovery.
 *   <li>{@code POST {basePath}/{workflow}} — JSON-RPC 2.0: {@code message/send}, {@code tasks/get},
 *       {@code tasks/cancel}.
 *   <li>{@code GET {basePath}} — convenience listing of exposed agents (non-spec).
 * </ul>
 *
 * <p>Lives in the {@code ai} module (component-scanned by the server), gated by {@code
 * conductor.a2a.server.enabled=true}. Paths use the configured {@code basePath} via {@code
 * ${conductor.a2a.server.basePath:/a2a}} so the routes match the property. Optional shared-secret
 * auth via {@code conductor.a2a.server.api-key}.
 */
@RestController
@Conditional(A2AServerEnabledCondition.class)
public class A2AServerResource {

    private static final Logger log = LoggerFactory.getLogger(A2AServerResource.class);

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final A2AWorkflowAgent agent;
    private final A2AServerProperties properties;

    public A2AServerResource(A2AWorkflowAgent agent, A2AServerProperties properties) {
        this.agent = agent;
        this.properties = properties;
    }

    @GetMapping(
            value = {
                "${conductor.a2a.server.basePath:/a2a}/{workflow}/.well-known/agent-card.json",
                "${conductor.a2a.server.basePath:/a2a}/{workflow}/.well-known/agent.json"
            },
            produces = "application/json")
    public ResponseEntity<?> agentCard(
            @PathVariable("workflow") String workflow,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-A2A-Server-Key", required = false) String keyHeader,
            HttpServletRequest httpRequest) {
        if (!authorized(authHeader, keyHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            AgentCard card = agent.agentCard(workflow, requestBaseUrl(httpRequest));
            return ResponseEntity.ok(card);
        } catch (A2AServerException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping(
            value = "${conductor.a2a.server.basePath:/a2a}/{workflow}",
            produces = "application/json")
    public ResponseEntity<JsonNode> jsonRpc(
            @PathVariable("workflow") String workflow,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-A2A-Server-Key", required = false) String keyHeader,
            @RequestBody(required = false) JsonNode request) {
        if (!authorized(authHeader, keyHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode id = request == null ? null : request.get("id");
        try (A2ALogging.Scope scope = A2ALogging.of(A2ALogging.AGENT, workflow)) {
            if (request == null || !request.hasNonNull("method")) {
                return ResponseEntity.ok(error(id, -32600, "Invalid Request: missing 'method'"));
            }
            String method = request.get("method").asText();
            scope.add(A2ALogging.METHOD, method);
            JsonNode params = request.get("params");
            Object result;
            switch (method) {
                case "message/send":
                    {
                        // Counter is emitted only for recognized methods — 'method' is
                        // client-controlled, so it must never become an unbounded metric tag.
                        A2AMetrics.serverRequest(method);
                        A2AMessage message = parseMessage(params);
                        scope.add(A2ALogging.MESSAGE_ID, message.getMessageId())
                                .add(A2ALogging.CONTEXT_ID, message.getContextId())
                                .add(A2ALogging.REMOTE_TASK_ID, message.getTaskId());
                        result = agent.sendMessage(workflow, message);
                        break;
                    }
                case "tasks/get":
                    {
                        A2AMetrics.serverRequest(method);
                        String getId = taskId(params);
                        scope.add(A2ALogging.REMOTE_TASK_ID, getId);
                        result = agent.getTask(workflow, getId);
                        break;
                    }
                case "tasks/cancel":
                    {
                        A2AMetrics.serverRequest(method);
                        String cancelId = taskId(params);
                        scope.add(A2ALogging.REMOTE_TASK_ID, cancelId);
                        result = agent.cancelTask(workflow, cancelId);
                        break;
                    }
                default:
                    return ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
            }
            return ResponseEntity.ok(success(id, result));
        } catch (A2AServerException e) {
            return ResponseEntity.ok(error(id, e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.warn("A2A server error handling request for {}: {}", workflow, e.getMessage());
            return ResponseEntity.ok(error(id, -32603, "Internal error: " + e.getMessage()));
        }
    }

    @GetMapping(value = "${conductor.a2a.server.basePath:/a2a}", produces = "application/json")
    public ResponseEntity<?> listAgents(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-A2A-Server-Key", required = false) String keyHeader,
            HttpServletRequest httpRequest) {
        if (!authorized(authHeader, keyHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String base = requestBaseUrl(httpRequest);
        List<?> agents =
                agent.exposedWorkflows().stream()
                        .map(
                                (WorkflowDef def) ->
                                        java.util.Map.of(
                                                "name", def.getName(),
                                                "url", base + basePath() + "/" + def.getName(),
                                                "agentCard",
                                                        base
                                                                + basePath()
                                                                + "/"
                                                                + def.getName()
                                                                + "/.well-known/agent-card.json"))
                        .collect(Collectors.toList());
        return ResponseEntity.ok(agents);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private A2AMessage parseMessage(JsonNode params) {
        if (params == null || !params.has("message")) {
            throw A2AServerException.invalidParams("message/send requires params.message");
        }
        return objectMapper.convertValue(params.get("message"), A2AMessage.class);
    }

    private String taskId(JsonNode params) {
        if (params == null || !params.hasNonNull("id")) {
            throw A2AServerException.invalidParams("requires params.id (the task/workflow id)");
        }
        return params.get("id").asText();
    }

    private boolean authorized(String authHeader, String keyHeader) {
        String configured = properties.getApiKey();
        if (configured == null || configured.isBlank()) {
            return true; // open by default, matching OSS Conductor REST
        }
        String presented = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            presented = authHeader.substring(7).trim();
        } else if (keyHeader != null && !keyHeader.isBlank()) {
            presented = keyHeader.trim();
        }
        return presented != null
                && MessageDigest.isEqual(
                        configured.getBytes(StandardCharsets.UTF_8),
                        presented.getBytes(StandardCharsets.UTF_8));
    }

    private String basePath() {
        String path = properties.getBasePath();
        if (path == null || path.isBlank()) {
            return "/a2a";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
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
        response.set("id", id == null ? null : id);
        response.set("result", objectMapper.valueToTree(result));
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? null : id);
        ObjectNode err = objectMapper.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        response.set("error", err);
        return response;
    }
}
