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

import org.conductoross.conductor.ai.mcp.MCPMethod;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.metrics.Monitors;

import reactor.core.publisher.Mono;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MCPServerMetricsTest {

    private static double requests(MCPMethod method, String outcome) {
        return Monitors.getCounter(
                        MCPServerMetrics.REQUESTS, "method", method.value(), "outcome", outcome)
                .count();
    }

    private static long timed(MCPMethod method) {
        return Monitors.getTimer(MCPServerMetrics.REQUEST_TIME, "method", method.value()).count();
    }

    @Test
    void success_isCountedOkAndTimed() {
        double before = requests(MCPMethod.TOOLS_LIST, MCPServerMetrics.OK);
        long timesBefore = timed(MCPMethod.TOOLS_LIST);

        MCPServerMetrics.timed(
                        MCPMethod.TOOLS_LIST,
                        call -> Mono.just(JsonNodeFactory.instance.objectNode()))
                .handle(null)
                .block();

        assertThat(requests(MCPMethod.TOOLS_LIST, MCPServerMetrics.OK)).isEqualTo(before + 1);
        assertThat(timed(MCPMethod.TOOLS_LIST)).isEqualTo(timesBefore + 1);
    }

    @Test
    void protocolError_isCountedByItsCode() {
        double before = requests(MCPMethod.TASKS_GET, "-32602");

        assertThatThrownBy(
                        () ->
                                MCPServerMetrics.timed(
                                                MCPMethod.TASKS_GET,
                                                call ->
                                                        Mono.error(
                                                                MCPServerException.invalidParams(
                                                                        "Unknown task: t-1")))
                                        .handle(null)
                                        .block())
                .isInstanceOf(MCPServerException.class);

        assertThat(requests(MCPMethod.TASKS_GET, "-32602")).isEqualTo(before + 1);
    }

    @Test
    void handlerThatThrows_isCountedAsInternalError_andStillFailsTheRequest() {
        double before = requests(MCPMethod.TOOLS_CALL, MCPServerMetrics.INTERNAL_ERROR);

        assertThatThrownBy(
                        () ->
                                MCPServerMetrics.timed(
                                                MCPMethod.TOOLS_CALL,
                                                call -> {
                                                    throw new IllegalStateException("store down");
                                                })
                                        .handle(null)
                                        .block())
                .isInstanceOf(IllegalStateException.class);

        assertThat(requests(MCPMethod.TOOLS_CALL, MCPServerMetrics.INTERNAL_ERROR))
                .isEqualTo(before + 1);
    }

    @Test
    void cancelledRequest_isCountedCancelledAndTimed() {
        double before = requests(MCPMethod.TOOLS_CALL, MCPServerMetrics.CANCELLED);
        long timesBefore = timed(MCPMethod.TOOLS_CALL);

        MCPServerMetrics.timed(MCPMethod.TOOLS_CALL, call -> Mono.never())
                .handle(null)
                .subscribe()
                .dispose();

        assertThat(requests(MCPMethod.TOOLS_CALL, MCPServerMetrics.CANCELLED))
                .isEqualTo(before + 1);
        assertThat(timed(MCPMethod.TOOLS_CALL)).isEqualTo(timesBefore + 1);
    }
}
