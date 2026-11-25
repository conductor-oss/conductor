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

import static org.assertj.core.api.Assertions.assertThat;

class MCPProtocolVersionTest {

    @Test
    void latest_isTheStatelessVersion() {
        assertThat(MCPProtocolVersion.latest()).isEqualTo(MCPProtocolVersion.V2026_07_28);
        assertThat(MCPProtocolVersion.latest().isLegacy()).isFalse();
        assertThat(MCPProtocolHeaders.PROTOCOL_VERSION).isEqualTo("2026-07-28");
    }

    @Test
    void legacyVersions_newestFirst() {
        assertThat(MCPProtocolVersion.legacy())
                .containsExactly(
                        MCPProtocolVersion.V2025_11_25,
                        MCPProtocolVersion.V2025_06_18,
                        MCPProtocolVersion.V2025_03_26);
        assertThat(MCPProtocolVersion.latestLegacy()).isEqualTo(MCPProtocolVersion.V2025_11_25);
    }

    @Test
    void aLegacyRequestWithoutAVersionHeader_is2025_03_26_withoutStructuredContent() {
        MCPProtocolVersion version = MCPProtocolVersion.legacyWithoutVersionHeader();
        assertThat(version).isEqualTo(MCPProtocolVersion.V2025_03_26);
        assertThat(version.hasStructuredContent()).isFalse();
        assertThat(MCPProtocolVersion.V2025_06_18.hasStructuredContent()).isTrue();
    }

    @Test
    void stateless_isEveryVersionNotReachedWithInitialize() {
        assertThat(MCPProtocolVersion.stateless()).containsExactly(MCPProtocolVersion.V2026_07_28);
        assertThat(MCPProtocolVersion.stateless("2026-07-28"))
                .contains(MCPProtocolVersion.V2026_07_28);
        assertThat(MCPProtocolVersion.stateless("2025-11-25")).isEmpty();
        assertThat(MCPProtocolVersion.stateless(null)).isEmpty();
    }

    @Test
    void of_findsOnlyKnownVersions() {
        assertThat(MCPProtocolVersion.of("2025-06-18")).contains(MCPProtocolVersion.V2025_06_18);
        assertThat(MCPProtocolVersion.of("2024-11-05")).isEmpty();
        assertThat(MCPProtocolVersion.of(null)).isEmpty();
    }

    @Test
    void newestFirst_listsEveryVersion() {
        assertThat(MCPProtocolVersion.newestFirst())
                .extracting(MCPProtocolVersion::value)
                .containsExactly("2026-07-28", "2025-11-25", "2025-06-18", "2025-03-26");
    }
}
