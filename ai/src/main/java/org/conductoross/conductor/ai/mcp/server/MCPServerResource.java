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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.conductoross.conductor.ai.mcp.MCPJsonRpc;
import org.conductoross.conductor.config.MCPServerEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/**
 * HTTP entry point of the MCP server: {@code POST /api/mcp} for JSON-RPC, {@code GET} answered with
 * 405 (no server-to-client stream). Responses are always {@code application/json}, also when the
 * client accepts {@code text/event-stream}. All protocol logic is in {@link MCPServerProtocol}.
 */
@RestController
@Conditional(MCPServerEnabledCondition.class)
public class MCPServerResource {

    static final String PATH = "/api/mcp";

    /**
     * Largest request body read, the same as Conductor's default largest workflow input ({@code
     * conductor.app.maxWorkflowInputPayloadSizeThreshold}): a call's arguments become that input.
     */
    static final int MAX_BODY_BYTES = 10 * 1024 * 1024;

    private final MCPServerProtocol protocol;

    public MCPServerResource(MCPServerProtocol protocol) {
        this.protocol = protocol;
    }

    @PostMapping(value = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> post(HttpServletRequest request) throws IOException {
        byte[] body = readBody(request);
        if (body == null) {
            return Mono.just(tooLarge());
        }
        // JSON-RPC messages are UTF-8, whatever Content-Type the client sends (it is not checked).
        String text = body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
        return protocol.handle(headers(request), text)
                .map(
                        response ->
                                response.body() == null
                                        ? ResponseEntity.status(response.status()).<JsonNode>build()
                                        : ResponseEntity.status(response.status())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(response.body()));
    }

    /** The request body, or null when it is larger than {@link #MAX_BODY_BYTES}. */
    private static byte[] readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return null;
        }
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            return body.length > MAX_BODY_BYTES ? null : body;
        }
    }

    /** HTTP 413 with a JSON-RPC error; the request was not read, so its id is unknown. */
    private ResponseEntity<JsonNode> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        protocol.errorBody(
                                MCPJsonRpc.INVALID_REQUEST,
                                "The request is larger than " + MAX_BODY_BYTES + " bytes"));
    }

    @GetMapping(PATH)
    public ResponseEntity<Void> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, HttpMethod.POST.name())
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
