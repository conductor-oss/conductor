package org.conductoross.conductor.dao.webhook;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

import java.util.List;
import java.util.Map;

public interface WebhookDAO {
    IncomingWebhookEvent getWebhookEvent(String messageId);

    WebhookConfig getWebhook(String webhookId);

    Map<String, Map<String, Object>> getMatchers(String webhookId);

    void createWebhook(String id, WebhookConfig webhookConfig);

    void removeWebhookEvent(String id);

    void removeWebhook(String id);

    void removeMatchers(String id);

    List<WebhookConfig> getAllWebhooks();

    void createMatchers(WebhookConfig webhookConfig, Map<String, Integer> receiverWorkflowNamesToVersionsOverride);

    void createIncomingWebhookEvent(String id, IncomingWebhookEvent incomingWebhookEvent);
}
