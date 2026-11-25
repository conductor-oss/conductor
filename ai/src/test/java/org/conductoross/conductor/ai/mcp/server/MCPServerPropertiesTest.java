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

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MCPServerPropertiesTest {

    private static void assertRejected(Consumer<MCPServerProperties> setting, String message) {
        MCPServerProperties properties = new MCPServerProperties();
        setting.accept(properties);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @Test
    void defaults_areValid() {
        assertThatCode(() -> new MCPServerProperties().validate()).doesNotThrowAnyException();
    }

    @Test
    void nonPositiveTaskTtl_isRejected() {
        assertRejected(
                p -> p.setTaskTtl(Duration.ZERO),
                "conductor.mcp.server.taskTtl must be positive, but is PT0S");
        assertRejected(
                p -> p.setTaskTtl(Duration.ofSeconds(-1)),
                "conductor.mcp.server.taskTtl must be positive, but is PT-1S");
        assertRejected(
                p -> p.setTaskTtl(null),
                "conductor.mcp.server.taskTtl must be positive, but is null");
    }

    @Test
    void nonPositivePollInterval_isRejected() {
        assertRejected(
                p -> p.setPollIntervalMs(0),
                "conductor.mcp.server.pollIntervalMs must be positive, but is 0");
        assertRejected(
                p -> p.setPollIntervalMs(-1),
                "conductor.mcp.server.pollIntervalMs must be positive, but is -1");
    }

    @Test
    void negativeWaitSettings_areRejected() {
        assertRejected(
                p -> p.setSyncWaitSeconds(-1),
                "conductor.mcp.server.syncWaitSeconds must not be negative, but is -1");
        assertRejected(
                p -> p.setMaxSyncWaitSeconds(-1),
                "conductor.mcp.server.maxSyncWaitSeconds must not be negative, but is -1");
        assertRejected(
                p -> p.setBlockingCapSeconds(-1),
                "conductor.mcp.server.blockingCapSeconds must not be negative, but is -1");
    }

    @Test
    void zeroWaits_areAllowed() {
        MCPServerProperties properties = new MCPServerProperties();
        properties.setSyncWaitSeconds(0);
        properties.setMaxSyncWaitSeconds(0);
        properties.setBlockingCapSeconds(0);
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
