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
package org.conductoross.conductor.webhook;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookHashingServiceTest {

    private final WebhookHashingService service = new WebhookHashingService();

    // --- happy path ---

    @Test
    void computeJsonHash_mapBody_matchingValue_returnsHash() {
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook;1;ref"),
                        Map.of("$.action", "push"),
                        "{\"action\":\"push\"}",
                        Map.of());
        assertThat(hash).isNotNull().contains("push");
    }

    @Test
    void computeJsonHash_mapBody_valueNotMatching_returnsNull() {
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook;1;ref"),
                        Map.of("$.action", "push"),
                        "{\"action\":\"create\"}",
                        Map.of());
        assertThat(hash).isNull();
    }

    @Test
    void computeJsonHash_wildcardExpectedValue_anyValueAccepted() {
        // Expected value starts with "$" — treated as wildcard; actual value is accepted.
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook"),
                        Map.of("$.action", "$ignored"),
                        "{\"action\":\"whatever\"}",
                        Map.of());
        assertThat(hash).isNotNull().contains("whatever");
    }

    @Test
    void computeJsonHash_pathNotPresentInBody_returnsNull() {
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook"),
                        Map.of("$.missing", "value"),
                        "{\"action\":\"push\"}",
                        Map.of());
        assertThat(hash).isNull();
    }

    @Test
    void computeJsonHash_parametersMerged_matchableViaJsonPath() {
        // Parameters are merged into the body map before JsonPath evaluation.
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook"),
                        Map.of("$.token", "abc"),
                        "{}",
                        Map.of("token", "abc"));
        assertThat(hash).isNotNull().contains("abc");
    }

    // --- edge cases ---

    @Test
    void computeJsonHash_nonMapBody_wrappedUnderRequestKey_matchableViaPath() {
        // Non-Object JSON bodies (e.g. arrays) are wrapped as {"request": <body>}.
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook"),
                        Map.of("$.request[0]", "1"),
                        "[1,2,3]",
                        Map.of());
        assertThat(hash).isNotNull().contains("1");
    }

    @Test
    void computeJsonHash_emptyJsonArray_returnsNull() {
        // A JSONPath that resolves to an empty JSONArray is treated as no-match.
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook"),
                        Map.of("$.items", "anything"),
                        "{\"items\":[]}",
                        Map.of());
        assertThat(hash).isNull();
    }

    @Test
    void computeJsonHash_malformedJson_returnsNull() {
        // Unparseable body falls back to empty map; matcher path finds nothing.
        String hash =
                service.computeJsonHash(
                        new StringBuilder("hook"),
                        Map.of("$.action", "push"),
                        "not-valid-json",
                        Map.of());
        assertThat(hash).isNull();
    }
}
