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
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.server.A2AServerException;
import org.conductoross.conductor.ai.a2a.server.A2AServerProperties;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A2A server endpoints for native Conductor agents. Exposes agentspan agents as A2A agents. Routes:
 * GET /api/a2a/agent (list), GET /api/a2a/agent/{name}/.well-known/agent-card.json (discovery),
 * POST /api/a2a/agent/{name} (JSON-RPC: message/send, tasks/get, tasks/cancel, message/stream).
 * Gated on conductor.a2a.server.enabled=true and agentspan.embedded=true. All dispatch logic,
 * thread-pool management, and JSON-RPC handling are delegated to A2ANativeAgentFacade.
 */
@RestController
@ConditionalOnProperty(
        name = {"conductor.a2a.server.enabled", "agentspan.embedded"},
        havingValue = "true")
public class A2AAgentServerResource {

    private final A2ANativeAgentFacade facade;
    private final A2AServerProperties properties;

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
        return facade.dispatch(agentName, request);
    }

    private String requestBaseUrl(HttpServletRequest request) {
        if (properties.getPublicUrl() != null && !properties.getPublicUrl().isBlank()) {
            return properties.getPublicUrl();
        }
        return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString();
    }
}
