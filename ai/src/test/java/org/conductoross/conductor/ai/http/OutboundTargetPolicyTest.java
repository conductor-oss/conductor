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
package org.conductoross.conductor.ai.http;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundTargetPolicyTest {

    @Test
    void defaultsToDenyAndRejectsUnsafeTargetForms() {
        OutboundTargetPolicy policy = new OutboundTargetPolicy();

        assertThatThrownBy(() -> policy.validate("http://example.com/mcp"))
                .hasMessageContaining("not allow-listed");
        assertThatThrownBy(() -> policy.validate("file:///etc/passwd"))
                .hasMessageContaining("Only http and https");
        assertThatThrownBy(() -> policy.validate("http://user:password@example.com/mcp"))
                .hasMessageContaining("user-info");
    }

    @Test
    void requiresExactOriginAndBlocksPrivateAddressesUnlessExplicitlyEnabled() {
        OutboundTargetPolicy policy = new OutboundTargetPolicy();
        policy.setAllowedOrigins(List.of("http://127.0.0.1:8080"));

        assertThatThrownBy(() -> policy.validate("http://127.0.0.1:8080/mcp"))
                .hasMessageContaining("private or local");
        assertThatThrownBy(() -> policy.validate("http://127.0.0.1:8081/mcp"))
                .hasMessageContaining("not allow-listed");

        policy.setAllowPrivateNetworks(true);
        policy.validate("http://127.0.0.1:8080/mcp");
    }
}
