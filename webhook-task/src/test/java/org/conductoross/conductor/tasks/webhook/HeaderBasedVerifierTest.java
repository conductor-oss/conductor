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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.webhook.WebhookConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

class HeaderBasedVerifierTest {

    private HeaderBasedVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new HeaderBasedVerifier();
    }

    private WebhookConfig configWithHeaders(Map<String, String> headers) {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        config.setHeaders(headers);
        return config;
    }

    private IncomingWebhookEvent eventWithHeaders(Map<String, List<String>> headers) {
        HttpHeaders h = new HttpHeaders();
        headers.forEach(h::addAll);
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setHeaders(h);
        return event;
    }

    @Test
    void getType_returnsHeaderBased() {
        assertEquals("HEADER_BASED", verifier.getType());
    }

    @Test
    void verify_passes_whenAllHeadersMatch() {
        WebhookConfig config = configWithHeaders(Map.of("X-Secret", "my-secret"));
        IncomingWebhookEvent event = eventWithHeaders(Map.of("X-Secret", List.of("my-secret")));

        List<String> errors = verifier.verify(config, event);

        assertTrue(errors.isEmpty());
    }

    @Test
    void verify_fails_whenHeaderMissing() {
        WebhookConfig config = configWithHeaders(Map.of("X-Secret", "my-secret"));
        IncomingWebhookEvent event = eventWithHeaders(Map.of("X-Other", List.of("value")));

        List<String> errors = verifier.verify(config, event);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("X-Secret"));
    }

    @Test
    void verify_fails_whenHeaderValueMismatch() {
        WebhookConfig config = configWithHeaders(Map.of("X-Secret", "correct"));
        IncomingWebhookEvent event = eventWithHeaders(Map.of("X-Secret", List.of("wrong")));

        List<String> errors = verifier.verify(config, event);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("X-Secret"));
    }

    @Test
    void verify_fails_whenNullHeaders() {
        WebhookConfig config = configWithHeaders(Map.of("X-Secret", "my-secret"));
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setHeaders(null);

        List<String> errors = verifier.verify(config, event);

        assertFalse(errors.isEmpty());
    }

    @Test
    void verify_fails_whenMultipleValuesForHeader() {
        WebhookConfig config = configWithHeaders(Map.of("X-Secret", "my-secret"));
        IncomingWebhookEvent event =
                eventWithHeaders(Map.of("X-Secret", List.of("my-secret", "extra")));

        List<String> errors = verifier.verify(config, event);

        // Value matches but multiple values is still an error
        assertTrue(errors.stream().anyMatch(e -> e.contains("Multiple values")));
    }

    @Test
    void verify_passes_withMultipleHeaders() {
        WebhookConfig config =
                configWithHeaders(
                        Map.of(
                                "X-Secret", "abc123",
                                "X-Source", "github"));
        IncomingWebhookEvent event =
                eventWithHeaders(
                        Map.of(
                                "X-Secret", List.of("abc123"),
                                "X-Source", List.of("github")));

        List<String> errors = verifier.verify(config, event);

        assertTrue(errors.isEmpty());
    }

    @Test
    void verify_fails_whenOneOfMultipleHeadersMismatch() {
        WebhookConfig config =
                configWithHeaders(
                        Map.of(
                                "X-Secret", "abc123",
                                "X-Source", "github"));
        IncomingWebhookEvent event =
                eventWithHeaders(
                        Map.of(
                                "X-Secret", List.of("abc123"),
                                "X-Source", List.of("NOT-github")));

        List<String> errors = verifier.verify(config, event);

        assertEquals(1, errors.size());
    }

    @Test
    void verify_fails_whenConfigHasNoHeaders() {
        WebhookConfig config = new WebhookConfig();
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        // no headers set
        IncomingWebhookEvent event = eventWithHeaders(Map.of("X-Secret", List.of("abc")));

        List<String> errors = verifier.verify(config, event);

        assertFalse(errors.isEmpty());
    }

    @Test
    void extractChallenge_returnsNull() {
        assertNull(verifier.extractChallenge(new IncomingWebhookEvent(), new WebhookConfig()));
    }

    @Test
    void handlePing_returnsNull() {
        assertNull(verifier.handlePing(new WebhookConfig(), Map.of()));
    }
}
