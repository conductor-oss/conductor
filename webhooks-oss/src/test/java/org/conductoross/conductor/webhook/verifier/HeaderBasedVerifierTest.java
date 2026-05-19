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

import java.util.Arrays;
import java.util.List;
import java.util.Map;


import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;

@TestPropertySource(properties = {"conductor.security.enabled=false"})
public class HeaderBasedVerifierTest {

    private final HeaderBasedVerifier headerBasedVerifier = new HeaderBasedVerifier();

    @BeforeEach
    public void setup() {}

    @Test
    @DisplayName("is should return verified for header value match")
    public void testWorkingWebhookRequest() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put("test", List.of("test"));
        incomingWebhookEvent.setHeaders(httpHeaders);
        Assertions.assertTrue(headerBasedVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for header value miss match")
    public void testNotWorkingWebhookRequest() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put("test", Arrays.asList("testing"));
        incomingWebhookEvent.setHeaders(httpHeaders);
        Assertions.assertFalse(headerBasedVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for multiple header value")
    public void testNot2WorkingWebhookRequest() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.put("test", Arrays.asList("test", "testing"));
        incomingWebhookEvent.setHeaders(httpHeaders);
        Assertions.assertFalse(headerBasedVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for missing header value")
    public void testNot3WorkingWebhookRequest() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        incomingWebhookEvent.setHeaders(null);
        Assertions.assertFalse(headerBasedVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    private WebhookConfig createWebhook() {
        WebhookConfig webhookConfig = new WebhookConfig();
        webhookConfig.setName("test");
        webhookConfig.setUrlVerified(false);
        webhookConfig.setSourcePlatform("SLACK");
        webhookConfig.setHeaders(Map.of("test", "test"));
        webhookConfig.setSecretKey("test");
        webhookConfig.setSecretValue("test");
        webhookConfig.setReceiverWorkflowNamesToVersions(Map.of("test", 1));
        return webhookConfig;
    }
}
