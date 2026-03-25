/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class WebhookHashingServiceTest {

    private WebhookHashingService service;

    @BeforeEach
    void setUp() {
        service = new WebhookHashingService(new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // computeTaskRegistrationHash
    // -------------------------------------------------------------------------

    @Test
    void registrationHash_basicFormat() {
        Map<String, Object> matches = new LinkedHashMap<>();
        matches.put("$['event']['type']", "payment.completed");
        matches.put("$['data']['orderId']", "12345");

        String hash =
                WebhookHashingService.computeTaskRegistrationHash(
                        "order_workflow", 1, "waitForPayment", matches);

        // Values in sorted-path order: $['data']['orderId'] < $['event']['type']
        assertEquals("order_workflow;1;waitForPayment;12345;payment.completed", hash);
    }

    @Test
    void registrationHash_sortedByPath() {
        Map<String, Object> matches = new LinkedHashMap<>();
        // Deliberately insert in reverse sort order
        matches.put("$['z']", "zzz");
        matches.put("$['a']", "aaa");

        String hash = WebhookHashingService.computeTaskRegistrationHash("wf", 2, "task1", matches);

        // $['a'] < $['z']
        assertEquals("wf;2;task1;aaa;zzz", hash);
    }

    @Test
    void registrationHash_stripsDoWhileIterationSuffix() {
        Map<String, Object> matches = Map.of("$['type']", "foo");

        String withSuffix =
                WebhookHashingService.computeTaskRegistrationHash("wf", 1, "myTask__3", matches);
        String withoutSuffix =
                WebhookHashingService.computeTaskRegistrationHash("wf", 1, "myTask", matches);

        assertEquals(withoutSuffix, withSuffix);
    }

    @Test
    void registrationHash_doesNotStripNonNumericDoubleSuffix() {
        Map<String, Object> matches = Map.of("$['type']", "foo");

        String hash =
                WebhookHashingService.computeTaskRegistrationHash("wf", 1, "myTask__abc", matches);

        assertTrue(hash.contains("myTask__abc"), "Non-numeric __ suffix should be preserved");
    }

    // -------------------------------------------------------------------------
    // computeInboundHash — static value match
    // -------------------------------------------------------------------------

    @Test
    void inboundHash_matchesStaticValues() {
        Map<String, Object> criteria =
                Map.of(
                        "$['event']['type']", "payment.completed",
                        "$['data']['orderId']", "12345");
        String body =
                "{\"event\":{\"type\":\"payment.completed\"},\"data\":{\"orderId\":\"12345\"}}";

        String hash =
                service.computeInboundHash(
                        "order_workflow;1;waitForPayment", criteria, body, Collections.emptyMap());

        assertNotNull(hash);
        // Values in sorted path order
        assertTrue(hash.startsWith("order_workflow;1;waitForPayment;"));
        assertTrue(hash.contains("12345"));
        assertTrue(hash.contains("payment.completed"));
    }

    @Test
    void inboundHash_returnsNullOnValueMismatch() {
        Map<String, Object> criteria = Map.of("$['event']['type']", "payment.completed");
        String body = "{\"event\":{\"type\":\"refund.issued\"}}";

        String hash = service.computeInboundHash("base", criteria, body, Collections.emptyMap());

        assertNull(hash);
    }

    @Test
    void inboundHash_returnsNullWhenPathMissing() {
        Map<String, Object> criteria = Map.of("$['event']['type']", "payment.completed");
        String body = "{\"something\":\"else\"}";

        String hash = service.computeInboundHash("base", criteria, body, Collections.emptyMap());

        assertNull(hash);
    }

    // -------------------------------------------------------------------------
    // computeInboundHash — wildcard ($) match
    // -------------------------------------------------------------------------

    @Test
    void inboundHash_wildcardMatchAcceptsAnyValue() {
        // Expected value starts with "$" → wildcard
        Map<String, Object> criteria = Map.of("$['data']['orderId']", "${workflow.input.orderId}");
        String body = "{\"data\":{\"orderId\":\"99999\"}}";

        String hash = service.computeInboundHash("base", criteria, body, Collections.emptyMap());

        assertNotNull(hash);
        assertTrue(hash.contains("99999"), "Wildcard hash should contain extracted value");
    }

    @Test
    void inboundHash_wildcardAndStaticCombined() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("$['event']['type']", "payment.completed"); // static
        criteria.put("$['data']['orderId']", "${workflow.input.orderId}"); // wildcard

        String body = "{\"event\":{\"type\":\"payment.completed\"},\"data\":{\"orderId\":\"777\"}}";
        String hash = service.computeInboundHash("base", criteria, body, Collections.emptyMap());

        assertNotNull(hash);
        // Static value must match; wildcard uses extracted value
        assertTrue(hash.contains("payment.completed"));
        assertTrue(hash.contains("777"));
    }

    @Test
    void inboundHash_wildcardReturnsNullWhenPathMissing() {
        Map<String, Object> criteria = Map.of("$['missing']", "${workflow.input.x}");
        String body = "{\"something\":\"else\"}";

        String hash = service.computeInboundHash("base", criteria, body, Collections.emptyMap());

        assertNull(hash);
    }

    // -------------------------------------------------------------------------
    // Registration hash and inbound hash produce the same result (end-to-end)
    // -------------------------------------------------------------------------

    @Test
    void registrationAndInboundHashesMatch_staticValues() {
        Map<String, Object> resolvedMatches =
                Map.of(
                        "$['event']['type']", "payment.completed",
                        "$['data']['orderId']", "12345");

        String regHash =
                WebhookHashingService.computeTaskRegistrationHash(
                        "order_workflow", 1, "waitForPayment", resolvedMatches);

        String body =
                "{\"event\":{\"type\":\"payment.completed\"},\"data\":{\"orderId\":\"12345\"}}";
        String inboundHash =
                service.computeInboundHash(
                        "order_workflow;1;waitForPayment",
                        resolvedMatches,
                        body,
                        Collections.emptyMap());

        assertEquals(
                regHash, inboundHash, "Registration and inbound hashes must match for same values");
    }

    @Test
    void registrationAndInboundHashesMatch_wildcardValue() {
        // Workflow task definition has ${ } reference (wildcard in matcher criteria)
        Map<String, Object> matchCriteria =
                Map.of("$['data']['orderId']", "${workflow.input.orderId}");

        // Task resolved the variable to "ACTUAL-ORDER"
        Map<String, Object> resolvedMatches = Map.of("$['data']['orderId']", "ACTUAL-ORDER");

        String regHash =
                WebhookHashingService.computeTaskRegistrationHash(
                        "wf", 1, "task1", resolvedMatches);

        // Inbound payload contains "ACTUAL-ORDER" — wildcard criteria matches
        String body = "{\"data\":{\"orderId\":\"ACTUAL-ORDER\"}}";
        String inboundHash =
                service.computeInboundHash(
                        "wf;1;task1", matchCriteria, body, Collections.emptyMap());

        assertEquals(regHash, inboundHash);
    }

    // -------------------------------------------------------------------------
    // stripIterationSuffix (package-private, tested via static accessor)
    // -------------------------------------------------------------------------

    @Test
    void stripIterationSuffix_stripsNumericSuffix() {
        assertEquals("myTask", WebhookHashingService.stripIterationSuffix("myTask__1"));
        assertEquals("myTask", WebhookHashingService.stripIterationSuffix("myTask__99"));
    }

    @Test
    void stripIterationSuffix_noSuffix() {
        assertEquals("myTask", WebhookHashingService.stripIterationSuffix("myTask"));
    }

    @Test
    void stripIterationSuffix_nonNumericSuffixPreserved() {
        assertEquals("myTask__abc", WebhookHashingService.stripIterationSuffix("myTask__abc"));
    }
}
