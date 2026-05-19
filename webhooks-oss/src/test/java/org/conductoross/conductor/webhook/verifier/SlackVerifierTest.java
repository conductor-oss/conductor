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

import java.util.HashMap;
import java.util.Map;


import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestPropertySource(properties = {"conductor.security.enabled=false"})
public class SlackVerifierTest {

    private final SlackVerifier slackVerifier = new SlackVerifier();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {}

    @Test
    @DisplayName("is should return verified for proper challenge for slack")
    public void testSlackWorkingWebhookRequest() throws JsonProcessingException {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        Map<String, String> map = new HashMap<>();
        map.put("challenge", "test");
        incomingWebhookEvent.setBody(objectMapper.writeValueAsString(map));
        Assertions.assertTrue(slackVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for bogus challenge")
    public void testNotWorkingWebhookRequest() throws JsonProcessingException {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        Map<String, String> map = new HashMap<>();
        map.put("ty", "test");
        incomingWebhookEvent.setBody(objectMapper.writeValueAsString(map));
        Assertions.assertFalse(slackVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should return not verified for empty body")
    public void testNotWorkingWebhookRequestEmptyBody() {
        WebhookConfig webhookConfig = createWebhook();
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        incomingWebhookEvent.setBody(null);
        Assertions.assertFalse(slackVerifier.verify(webhookConfig, incomingWebhookEvent).isEmpty());
    }

    @Test
    @DisplayName("is should extract proper challenge")
    public void testExtractChallenge() throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        map.put("challenge", "test");
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        WebhookConfig webhookConfig = new WebhookConfig();
        incomingWebhookEvent.setBody(objectMapper.writeValueAsString(map));
        Assertions.assertEquals("test", slackVerifier.extractChallenge(incomingWebhookEvent, webhookConfig));
    }

    @Test
    @DisplayName("is should extract challenge as empty if does not exist")
    public void testNotExtractChallenge() throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        map.put("t", "test");
        IncomingWebhookEvent incomingWebhookEvent = new IncomingWebhookEvent();
        WebhookConfig webhookConfig = new WebhookConfig();
        incomingWebhookEvent.setBody(objectMapper.writeValueAsString(map));
        Assertions.assertEquals(null, slackVerifier.extractChallenge(incomingWebhookEvent, webhookConfig));
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
