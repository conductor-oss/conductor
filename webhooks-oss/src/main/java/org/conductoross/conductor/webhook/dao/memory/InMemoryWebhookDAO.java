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
package org.conductoross.conductor.webhook.dao.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.WebhookMatcherComputer;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.netflix.conductor.dao.MetadataDAO;

/**
 * Default single-node implementation of {@link WebhookDAO}.
 *
 * <p>Backed by in-process maps; suitable for single-server deployments and tests. Multi-node
 * deployments should bind a persistent implementation (lands in a later PR).
 */
@Component
@ConditionalOnMissingBean(name = "webhookDAO")
public class InMemoryWebhookDAO implements WebhookDAO {

    private final MetadataDAO metadataDAO;
    private final ConcurrentHashMap<String, WebhookConfig> configs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IncomingWebhookEvent> events =
            new ConcurrentHashMap<>();
    // workflow-name -> version override snapshot taken at createMatchers() time.
    // Stored (not recomputed) because it reflects the expression evaluation that
    // ran when the config was registered. The actual `matches` criteria are
    // looked up fresh from MetadataDAO on every getMatchers() call so updates to
    // a WorkflowDef's WAIT_FOR_WEBHOOK task input parameters take effect without
    // re-registering the webhook.
    private final ConcurrentHashMap<String, Map<String, Integer>> targetWorkflows =
            new ConcurrentHashMap<>();

    // webhookId|signature -> expires_at (epoch millis). Reaped lazily on each
    // call. Single-node store; clustered deployments use a persistent DAO.
    private final ConcurrentHashMap<String, Long> signatureDedup = new ConcurrentHashMap<>();

    public InMemoryWebhookDAO(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    @Override
    public void createWebhook(String id, WebhookConfig config) {
        configs.put(id, config);
    }

    @Override
    public WebhookConfig getWebhook(String id) {
        return configs.get(id);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        return new ArrayList<>(configs.values());
    }

    @Override
    public void removeWebhook(String id) {
        configs.remove(id);
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        events.put(id, event);
    }

    @Override
    public IncomingWebhookEvent getWebhookEvent(String id) {
        return events.get(id);
    }

    @Override
    public void removeWebhookEvent(String id) {
        events.remove(id);
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        return WebhookMatcherComputer.compute(targetWorkflows.get(webhookId), metadataDAO);
    }

    @Override
    public void createMatchers(
            WebhookConfig webhookConfig,
            @Nullable Map<String, Integer> receiverWorkflowNamesToVersionsOverride) {
        targetWorkflows.put(
                webhookConfig.getId(),
                receiverWorkflowNamesToVersionsOverride == null
                        ? Collections.emptyMap()
                        : Map.copyOf(receiverWorkflowNamesToVersionsOverride));
    }

    @Override
    public void removeMatchers(String id) {
        targetWorkflows.remove(id);
    }

    @Override
    public boolean tryRecordSignature(String webhookId, String signature, Duration ttl) {
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + ttl.toMillis();
        String key = webhookId + "|" + signature;
        // putIfAbsent is atomic. If a live (non-expired) entry exists, reject.
        // If the entry expired, prune-and-retry once.
        Long existing = signatureDedup.putIfAbsent(key, expiresAt);
        if (existing == null) {
            return true;
        }
        if (existing >= now) {
            return false;
        }
        // Expired: best-effort prune + retry. The race between prune and put is
        // benign — at worst two events race and one wins; the loser observes a
        // live entry it just lost and is correctly rejected.
        signatureDedup.remove(key, existing);
        return signatureDedup.putIfAbsent(key, expiresAt) == null;
    }
}
