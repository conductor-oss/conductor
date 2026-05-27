/*
 * Copyright 2022 Conductor Authors.
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
package org.conductoross.conductor.webhook.verifier;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestPropertySource(properties = {"conductor.security.enabled=false"})
public class SlackVerifierTest {

    private static final String SIGNING_SECRET = "8f742231b10e8888abcd99yyyzzz85a5";

    private final SlackVerifier slackVerifier = new SlackVerifier();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {}

    @Test
    @DisplayName("verifies a request signed with the configured signing secret")
    public void testValidSignature() throws Exception {
        WebhookConfig webhookConfig = createWebhook();
        long ts = System.currentTimeMillis() / 1000L;
        String body = "{\"event\":\"ping\"}";

        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody(body);
        event.setHeaders(headersFor(ts, signature(ts, body, SIGNING_SECRET)));

        Assertions.assertTrue(slackVerifier.verify(webhookConfig, event).isEmpty());
    }

    @Test
    @DisplayName("rejects a tampered body even with a valid-looking signature")
    public void testTamperedBody() throws Exception {
        WebhookConfig webhookConfig = createWebhook();
        long ts = System.currentTimeMillis() / 1000L;
        String signedBody = "{\"event\":\"ping\"}";
        String tamperedBody = "{\"event\":\"pwned\"}";

        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody(tamperedBody);
        event.setHeaders(headersFor(ts, signature(ts, signedBody, SIGNING_SECRET)));

        Assertions.assertFalse(slackVerifier.verify(webhookConfig, event).isEmpty());
    }

    @Test
    @DisplayName("rejects a request signed with the wrong secret")
    public void testWrongSecret() throws Exception {
        WebhookConfig webhookConfig = createWebhook();
        long ts = System.currentTimeMillis() / 1000L;
        String body = "{\"event\":\"ping\"}";

        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody(body);
        event.setHeaders(headersFor(ts, signature(ts, body, "wrong-secret")));

        Assertions.assertFalse(slackVerifier.verify(webhookConfig, event).isEmpty());
    }

    @Test
    @DisplayName("rejects a request whose timestamp is outside the replay window")
    public void testStaleTimestamp() throws Exception {
        WebhookConfig webhookConfig = createWebhook();
        long staleTs = (System.currentTimeMillis() / 1000L) - 3600; // 1h ago
        String body = "{\"event\":\"ping\"}";

        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody(body);
        event.setHeaders(headersFor(staleTs, signature(staleTs, body, SIGNING_SECRET)));

        Assertions.assertFalse(slackVerifier.verify(webhookConfig, event).isEmpty());
    }

    @Test
    @DisplayName("rejects a request missing the X-Slack-Signature header")
    public void testMissingSignatureHeader() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody("{}");
        HttpHeaders headers = new HttpHeaders();
        headers.put(SlackVerifier.TIMESTAMP_HEADER, List.of("0"));
        event.setHeaders(headers);

        Assertions.assertFalse(slackVerifier.verify(webhookConfig, event).isEmpty());
    }

    @Test
    @DisplayName("rejects a request missing the X-Slack-Request-Timestamp header")
    public void testMissingTimestampHeader() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setBody("{}");
        HttpHeaders headers = new HttpHeaders();
        headers.put(SlackVerifier.SIGNATURE_HEADER, List.of("v0=abc"));
        event.setHeaders(headers);

        Assertions.assertFalse(slackVerifier.verify(webhookConfig, event).isEmpty());
    }

    @Test
    @DisplayName("extracts the challenge field on URL verification handshake")
    public void testExtractChallenge() throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        map.put("challenge", "test");
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        WebhookConfig webhookConfig = new WebhookConfig();
        incomingWebhookEvent.setBody(objectMapper.writeValueAsString(map));
        Assertions.assertEquals(
                "test", slackVerifier.extractChallenge(incomingWebhookEvent, webhookConfig));
    }

    @Test
    @DisplayName("returns null when the body has no challenge field")
    public void testNotExtractChallenge() throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        map.put("t", "test");
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        WebhookConfig webhookConfig = new WebhookConfig();
        incomingWebhookEvent.setBody(objectMapper.writeValueAsString(map));
        Assertions.assertEquals(
                null, slackVerifier.extractChallenge(incomingWebhookEvent, webhookConfig));
    }

    private WebhookConfig createWebhook() {
        WebhookConfig webhookConfig = new WebhookConfig();
        webhookConfig.setName("test");
        webhookConfig.setUrlVerified(false);
        webhookConfig.setSourcePlatform("SLACK");
        webhookConfig.setHeaders(Map.of("test", "test"));
        webhookConfig.setSecretKey("test");
        webhookConfig.setSecretValue(SIGNING_SECRET);
        webhookConfig.setReceiverWorkflowNamesToVersions(Map.of("test", 1));
        return webhookConfig;
    }

    private HttpHeaders headersFor(long timestamp, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.put(SlackVerifier.SIGNATURE_HEADER, List.of(signature));
        headers.put(SlackVerifier.TIMESTAMP_HEADER, List.of(Long.toString(timestamp)));
        return headers;
    }

    private static String signature(long timestamp, String body, String secret) throws Exception {
        String basestring =
                SlackVerifier.SIGNATURE_VERSION + ":" + timestamp + ":" + body;
        Mac mac = Mac.getInstance(SlackVerifier.ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SlackVerifier.ALGORITHM));
        byte[] hmac = mac.doFinal(basestring.getBytes(StandardCharsets.UTF_8));
        return SlackVerifier.SIGNATURE_VERSION + "=" + Hex.encodeHexString(hmac);
    }
}
