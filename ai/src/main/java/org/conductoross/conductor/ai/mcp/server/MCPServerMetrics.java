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

import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.mcp.MCPMethod;

import com.netflix.conductor.metrics.Monitors;

import reactor.core.publisher.Mono;

/**
 * Metrics of the MCP server's method handlers, as {@code A2AMetrics} records the A2A server's:
 *
 * <ul>
 *   <li>{@code mcp_server_requests} (counter), tagged {@code method} and {@code outcome}: {@code
 *       ok}, the JSON-RPC error code of a protocol error, {@code internal_error}, or {@code
 *       cancelled} when the request ends before the answer (the async request timeout, or a client
 *       that disconnects). A tool call whose result has {@code isError} is {@code ok}: the request
 *       itself succeeded.
 *   <li>{@code mcp_server_request_time} (timer), tagged {@code method}. For {@code tools/call} it
 *       includes the wait for the execution.
 * </ul>
 */
public final class MCPServerMetrics {

    static final String REQUESTS = "mcp_server_requests";
    static final String REQUEST_TIME = "mcp_server_request_time";
    static final String OK = "ok";
    static final String INTERNAL_ERROR = "internal_error";
    static final String CANCELLED = "cancelled";

    private MCPServerMetrics() {}

    /** The handler, recording one count and one time per request. */
    static MCPServerProtocol.MethodHandler timed(
            MCPMethod method, MCPServerProtocol.MethodHandler handler) {
        return call -> {
            long start = System.nanoTime();
            return Mono.defer(() -> handler.handle(call))
                    .doOnSuccess(result -> record(method, OK, start))
                    .doOnError(e -> record(method, outcome(e), start))
                    .doOnCancel(() -> record(method, CANCELLED, start));
        };
    }

    private static String outcome(Throwable e) {
        return e instanceof MCPServerException mcp ? String.valueOf(mcp.getCode()) : INTERNAL_ERROR;
    }

    private static void record(MCPMethod method, String outcome, long start) {
        Monitors.getCounter(REQUESTS, "method", method.value(), "outcome", outcome).increment();
        Monitors.getTimer(REQUEST_TIME, "method", method.value())
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
}
