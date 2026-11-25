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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.metrics.Monitors;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MCPClientMetricsTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private static double rpcErrors(String method, boolean terminal) {
        return Monitors.getCounter(
                        MCPClientMetrics.RPC_ERRORS,
                        "method",
                        method,
                        "terminal",
                        String.valueOf(terminal))
                .count();
    }

    private static MCPRequestException rpcError(int httpStatus, int code) {
        return MCPRequestException.fromRpcError(
                MCPMethod.TASKS_GET,
                httpStatus,
                MAPPER.readTree("{\"code\":" + code + ",\"message\":\"m\"}"));
    }

    @Test
    void internalError_isNotTerminal() {
        double before = rpcErrors("tasks/get", false);
        MCPClientMetrics.rpcError(rpcError(200, -32603));
        assertThat(rpcErrors("tasks/get", false)).isEqualTo(before + 1);
    }

    @Test
    void definedErrorOnA5xx_isTerminal() {
        double before = rpcErrors("tasks/get", true);
        MCPClientMetrics.rpcError(rpcError(503, -32602));
        assertThat(rpcErrors("tasks/get", true)).isEqualTo(before + 1);
    }

    @Test
    void httpErrorsAreTerminalExceptRetryableStatuses() {
        double transientBefore = rpcErrors("tools/call", false);
        double terminalBefore = rpcErrors("tools/call", true);

        MCPClientMetrics.rpcError(
                MCPRequestException.fromHttpError(MCPMethod.TOOLS_CALL, 429, "slow down", MAPPER));
        MCPClientMetrics.rpcError(
                MCPRequestException.fromHttpError(MCPMethod.TOOLS_CALL, 403, "no", MAPPER));

        assertThat(rpcErrors("tools/call", false)).isEqualTo(transientBefore + 1);
        assertThat(rpcErrors("tools/call", true)).isEqualTo(terminalBefore + 1);
    }

    @Test
    void callResult_isTheLowerCaseStatus() {
        double before =
                Monitors.getCounter(MCPClientMetrics.CALLS, "result", "failed_with_terminal_error")
                        .count();
        MCPClientMetrics.clientCall("FAILED_WITH_TERMINAL_ERROR");
        assertThat(
                        Monitors.getCounter(
                                        MCPClientMetrics.CALLS,
                                        "result",
                                        "failed_with_terminal_error")
                                .count())
                .isEqualTo(before + 1);
    }
}
