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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"conductor.security.enabled=false"})
public class HMACVerifierTest {

    private final HMACVerifier hmacVerifier = new HMACVerifier();
    private final String SECURITY_TOKEN_RAW = "my-teams-security-token";

    @BeforeEach
    public void setup() {}

    @Test
    @DisplayName("is should return verified for proper signature based verifier")
    public void testProperHMACSignatureBasedVerifier() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                webhookConfig.getHeaders().keySet().iterator().next(),
                List.of("HMAC " + calculateSignature(webhookConfig.getSecretValue())));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertTrue(hmacVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for wrong sha value")
    public void testNotProperHMACSignatureBasedVerifier() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                webhookConfig.getHeaders().keySet().iterator().next(),
                List.of("HMAC " + calculateSignature(base64EncodedString("SECRET"))));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertFalse(hmacVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for header missing")
    public void testNotProperHMACSignatureBasedVerifierMissingKey() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                "WRONG_HEADER",
                List.of("HMAC " + calculateSignature(webhookConfig.getSecretValue())));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertFalse(hmacVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for multiple header value")
    public void testNotProperHMACSignatureBasedVerifierMultipleHeaders() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                webhookConfig.getHeaders().keySet().iterator().next(),
                Arrays.asList(
                        "HMAC " + calculateSignature(webhookConfig.getSecretValue()),
                        "HMAC " + calculateSignature(webhookConfig.getSecretValue())));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertFalse(hmacVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    private WebhookConfig createWebhook() {
        WebhookConfig webhookConfig = new WebhookConfig();
        webhookConfig.setName("Teams");
        webhookConfig.setUrlVerified(false);
        webhookConfig.setSourcePlatform("MICROSOFT_TEAMS");
        webhookConfig.setHeaders(
                Map.of("Authorization", "HMAC 8gBSqHFqsfn9A8SuMFBV1PVQl5AIU97NWMpj49RlN7E="));
        webhookConfig.setSecretKey("SECURITY_TOKEN");
        webhookConfig.setSecretValue(base64EncodedString(SECURITY_TOKEN_RAW));
        webhookConfig.setHeaderKey("Authorization");
        webhookConfig.setReceiverWorkflowNamesToVersions(Map.of("test", 1));
        return webhookConfig;
    }

    private String base64EncodedString(String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        return HMACVerifier.encodeToString(bytes);
    }

    private String calculateSignature(String securityToken) {
        byte[] keyBytes = HMACVerifier.decodeFromString(securityToken);
        String algorithm = HMACVerifier.ALGORITHM;
        try {
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, algorithm);
            mac.init(secretKeySpec);
            byte[] calculatedHMACValue =
                    mac.doFinal("information".getBytes(StandardCharsets.UTF_8));
            return new String(HMACVerifier.encode(calculatedHMACValue));
        } catch (Exception e) {
            return "";
        }
    }
}
