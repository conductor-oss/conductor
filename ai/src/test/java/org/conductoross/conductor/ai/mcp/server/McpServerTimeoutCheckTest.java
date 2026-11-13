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
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTimeoutCheckTest {

    @Test
    void defaultsWithPropertyUnset_noWarning() {
        assertThat(McpServerConfiguration.timeoutWarnings(new McpServerProperties(), null))
                .isEmpty();
    }

    @Test
    void asyncTimeoutBelowTheCaps_warnsForBoth() {
        List<String> warnings =
                McpServerConfiguration.timeoutWarnings(
                        new McpServerProperties(), Duration.ofSeconds(20));
        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0)).contains("maxSyncWaitSeconds").contains("20");
        assertThat(warnings.get(1)).contains("blockingCapSeconds").contains("20");
    }

    @Test
    void capsAtTheTomcatDefault_warnWhenPropertyUnset() {
        McpServerProperties properties = new McpServerProperties();
        properties.setBlockingCapSeconds(30);
        assertThat(McpServerConfiguration.timeoutWarnings(properties, null))
                .singleElement()
                .asString()
                .contains("blockingCapSeconds")
                .contains("30");
    }

    @Test
    void largerAsyncTimeout_allowsLargerCaps() {
        McpServerProperties properties = new McpServerProperties();
        properties.setMaxSyncWaitSeconds(55);
        properties.setBlockingCapSeconds(55);
        assertThat(McpServerConfiguration.timeoutWarnings(properties, Duration.ofSeconds(65)))
                .isEmpty();
    }
}
