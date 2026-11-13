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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.conductoross.conductor.config.McpServerEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/**
 * HTTP entry point of the MCP server: {@code POST /api/mcp} for JSON-RPC, {@code GET} answered with
 * 405 (no server-to-client stream). Responses are always {@code application/json}, also when the
 * client accepts {@code text/event-stream}. All protocol logic is in {@link McpServerProtocol}.
 */
@RestController
@Conditional(McpServerEnabledCondition.class)
public class McpServerResource {

    static final String PATH = "/api/mcp";

    private final McpServerProtocol protocol;

    public McpServerResource(McpServerProtocol protocol) {
        this.protocol = protocol;
    }

    @PostMapping(value = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> post(
            @RequestBody(required = false) byte[] body, HttpServletRequest request) {
        // JSON-RPC messages are UTF-8, whatever Content-Type the client sends (it is not checked).
        String text = body == null ? null : new String(body, StandardCharsets.UTF_8);
        return protocol.handle(headers(request), text)
                .map(
                        response ->
                                response.body() == null
                                        ? ResponseEntity.status(response.status()).<JsonNode>build()
                                        : ResponseEntity.status(response.status())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(response.body()));
    }

    @GetMapping(PATH)
    public ResponseEntity<Void> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "POST")
                .build();
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
