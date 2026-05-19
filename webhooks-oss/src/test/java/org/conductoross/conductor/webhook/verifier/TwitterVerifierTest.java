/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Orkes Enterprise License (the "License"); you may not use this file except in compliance with
 * the License.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.webhook.verifier;


import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.utils.HashUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@TestPropertySource(properties = {"conductor.security.enabled=false"})
public class TwitterVerifierTest {

    private final TwitterVerifier twitterVerifier = new TwitterVerifier();

    @BeforeEach
    public void setup() {}

    @Test
    @DisplayName("is should return verified for proper twitter verifier")
    public void testProperTwitterVerifier()
            throws NoSuchAlgorithmException, InvalidKeyException {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                webhookConfig.getHeaders().keySet().iterator().next(),
                Arrays.asList(
                        "sha256="
                                + HashUtils.computeBase64HmacSha256(
                                        webhookConfig.getSecretValue(), body)));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertTrue(twitterVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for wrong sha value")
    public void testNotProperTwitterVerifier()
            throws NoSuchAlgorithmException, InvalidKeyException {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                webhookConfig.getHeaders().keySet().iterator().next(),
                List.of(
                        "sha256=" + HashUtils.computeBase64HmacSha256("testing", body)));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertFalse(twitterVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for header missing")
    public void testNotProperTwitterVerifierMissingKey()
            throws NoSuchAlgorithmException, InvalidKeyException {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                "test",
                List.of(
                        "sha256="
                                + HashUtils.computeBase64HmacSha256(
                                webhookConfig.getSecretValue(), body)));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertFalse(twitterVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for multiple header value")
    public void testNotProperTwitterVerifierMultipleHeaders()
            throws NoSuchAlgorithmException, InvalidKeyException {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        String body = "information";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                "test",
                Arrays.asList(
                        "sha256="
                                + HashUtils.computeBase64HmacSha256(
                                        webhookConfig.getSecretValue(), body),
                        "sha256="
                                + HashUtils.computeBase64HmacSha256(
                                        webhookConfig.getSecretValue(), body)));
        incomingWebhookEvent.setHeaders(httpHeaders);
        incomingWebhookEvent.setBody(body);
        Assertions.assertFalse(twitterVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should handle ping event for twitter verifier")
    public void testHandlePingEvent()
            throws NoSuchAlgorithmException, InvalidKeyException {
        WebhookConfig webhookConfig = createWebhook();
        String body = "information";
        Map<String, Object>  map = Map.of("crc_token", "mytoken");
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put(
                webhookConfig.getHeaders().keySet().iterator().next(),
                List.of(
                        "sha256="
                                + HashUtils.computeBase64HmacSha256(
                                webhookConfig.getSecretValue(), body)));
        String actual = "{\"response_token\":\"sha256=" +
                HashUtils.computeBase64HmacSha256(webhookConfig.getSecretValue(), map.get("crc_token").toString()) + "\"}";
        Assertions.assertEquals(actual, twitterVerifier.handlePing(webhookConfig, map));
    }

    private WebhookConfig createWebhook() {
        WebhookConfig webhookConfig = new WebhookConfig();
        webhookConfig.setName("test");
        webhookConfig.setUrlVerified(false);
        webhookConfig.setSourcePlatform("GITHUB");
        webhookConfig.setHeaders(Map.of("X-Orkes-Signature", "test"));
        webhookConfig.setSecretKey("test");
        webhookConfig.setSecretValue("my-secret");
        webhookConfig.setHeaderKey("X-Orkes-Signature");
        webhookConfig.setReceiverWorkflowNamesToVersions(Map.of("name", 1));
        return webhookConfig;
    }
}
