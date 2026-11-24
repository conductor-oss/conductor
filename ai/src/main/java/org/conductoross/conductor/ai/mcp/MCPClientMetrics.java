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
package org.conductoross.conductor.ai.mcp;

import java.util.Locale;

import com.netflix.conductor.metrics.Monitors;

/**
 * Metrics of the MCP client ({@code CALL_MCP_TOOL}), the same set {@code A2AMetrics} records for
 * the A2A client.
 */
public final class MCPClientMetrics {

    static final String CALLS = "mcp_client_calls";
    static final String POLL_FAILURES = "mcp_client_poll_failures";
    static final String RPC_ERRORS = "mcp_rpc_errors";

    private MCPClientMetrics() {}

    /** A {@code CALL_MCP_TOOL} task ended with this Conductor status, such as {@code completed}. */
    public static void clientCall(String result) {
        Monitors.getCounter(CALLS, "result", result.toLowerCase(Locale.ROOT)).increment();
    }

    /**
     * One transient {@code tasks/get} failure: a network error, a timeout, JSON-RPC {@code -32603},
     * or HTTP 408, 429 or 5xx.
     */
    public static void clientPollFailure() {
        Monitors.getCounter(POLL_FAILURES).increment();
    }

    /**
     * An MCP server answered a request with an HTTP or JSON-RPC error. It is terminal unless the
     * client retries it: JSON-RPC {@code -32603}, or HTTP 408, 429 or 5xx without a JSON-RPC code
     * of defined meaning.
     */
    static void rpcError(MCPRequestException error) {
        boolean transientError =
                error.isInternalError()
                        || (error.isTransientHttpError() && !error.hasDefinedMeaning());
        Monitors.getCounter(
                        RPC_ERRORS,
                        "method",
                        error.getMethod().value(),
                        "terminal",
                        String.valueOf(!transientError))
                .increment();
    }
}
