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
package org.conductoross.conductor.postgres.dao;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.WebhookMatcherComputer;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Postgres-backed {@link WebhookDAO}. Stores webhook configs, incoming event payloads, and the
 * receiver-workflow target overrides. Match criteria are recomputed from {@link MetadataDAO} on
 * every {@link #getMatchers(String)} call so WorkflowDef updates take effect without re-registering
 * the webhook (mirrors the design fix applied to the in-memory impl).
 */
@Slf4j
public class PostgresWebhookDAO extends PostgresBaseDAO implements WebhookDAO {

    private static final String INSERT_WEBHOOK =
            "INSERT INTO webhook (webhook_id, json_data) VALUES (?, ?) "
                    + "ON CONFLICT (webhook_id) DO UPDATE "
                    + "SET json_data = EXCLUDED.json_data, modified_on = CURRENT_TIMESTAMP";
    private static final String SELECT_WEBHOOK =
            "SELECT json_data FROM webhook WHERE webhook_id = ?";
    private static final String SELECT_ALL_WEBHOOKS = "SELECT json_data FROM webhook";
    private static final String DELETE_WEBHOOK = "DELETE FROM webhook WHERE webhook_id = ?";

    private static final String INSERT_EVENT =
            "INSERT INTO incoming_webhook_event (event_id, json_data) VALUES (?, ?) "
                    + "ON CONFLICT (event_id) DO UPDATE "
                    + "SET json_data = EXCLUDED.json_data";
    private static final String SELECT_EVENT =
            "SELECT json_data FROM incoming_webhook_event WHERE event_id = ?";
    private static final String DELETE_EVENT =
            "DELETE FROM incoming_webhook_event WHERE event_id = ?";

    private static final String INSERT_TARGETS =
            "INSERT INTO webhook_target_workflows (webhook_id, json_data) VALUES (?, ?) "
                    + "ON CONFLICT (webhook_id) DO UPDATE "
                    + "SET json_data = EXCLUDED.json_data";

    private static final String INSERT_DEDUP =
            "INSERT INTO webhook_signature_dedup (webhook_id, signature, seen_at, expires_at) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (webhook_id, signature) DO NOTHING";
    private static final String SELECT_TARGETS =
            "SELECT json_data FROM webhook_target_workflows WHERE webhook_id = ?";
    private static final String DELETE_TARGETS =
            "DELETE FROM webhook_target_workflows WHERE webhook_id = ?";

    private final MetadataDAO metadataDAO;

    public PostgresWebhookDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            MetadataDAO metadataDAO) {
        super(retryTemplate, objectMapper, dataSource);
        this.metadataDAO = metadataDAO;
    }

    @Override
    public void createWebhook(String id, WebhookConfig webhookConfig) {
        String json = toJson(webhookConfig);
        queryWithTransaction(
                INSERT_WEBHOOK, q -> q.addParameter(id).addParameter(json).executeUpdate());
    }

    @Override
    public WebhookConfig getWebhook(String webhookId) {
        String json =
                queryWithTransaction(
                        SELECT_WEBHOOK,
                        q -> q.addParameter(webhookId).executeAndFetchFirst(String.class));
        return json == null ? null : readValue(json, WebhookConfig.class);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        List<String> rows =
                queryWithTransaction(SELECT_ALL_WEBHOOKS, q -> q.executeAndFetch(String.class));
        if (rows == null) {
            return new ArrayList<>();
        }
        List<WebhookConfig> out = new ArrayList<>(rows.size());
        for (String json : rows) {
            out.add(readValue(json, WebhookConfig.class));
        }
        return out;
    }

    @Override
    public void removeWebhook(String id) {
        WebhookConfig existing = getWebhook(id);
        if (existing == null) {
            throw new NotFoundException("Webhook with id " + id + " not found");
        }
        queryWithTransaction(DELETE_WEBHOOK, q -> q.addParameter(id).executeUpdate());
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        String json = toJson(event);
        queryWithTransaction(
                INSERT_EVENT, q -> q.addParameter(id).addParameter(json).executeUpdate());
    }

    @Override
    public IncomingWebhookEvent getWebhookEvent(String messageId) {
        String json =
                queryWithTransaction(
                        SELECT_EVENT,
                        q -> q.addParameter(messageId).executeAndFetchFirst(String.class));
        return json == null ? null : readValue(json, IncomingWebhookEvent.class);
    }

    @Override
    public void removeWebhookEvent(String id) {
        queryWithTransaction(DELETE_EVENT, q -> q.addParameter(id).executeUpdate());
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        return WebhookMatcherComputer.compute(loadTargets(webhookId), metadataDAO);
    }

    @Override
    public void createMatchers(
            WebhookConfig webhookConfig,
            @Nullable Map<String, Integer> receiverWorkflowNamesToVersionsOverride) {
        Map<String, Integer> targets =
                receiverWorkflowNamesToVersionsOverride == null
                        ? Collections.emptyMap()
                        : receiverWorkflowNamesToVersionsOverride;
        String json = toJson(targets);
        queryWithTransaction(
                INSERT_TARGETS,
                q -> q.addParameter(webhookConfig.getId()).addParameter(json).executeUpdate());
    }

    @Override
    public void removeMatchers(String id) {
        queryWithTransaction(DELETE_TARGETS, q -> q.addParameter(id).executeUpdate());
    }

    @Override
    public boolean tryRecordSignature(String webhookId, String signature, Duration ttl) {
        Instant now = Instant.now();
        Timestamp seenAt = Timestamp.from(now);
        Timestamp expiresAt = Timestamp.from(now.plus(ttl));
        Integer affected =
                queryWithTransaction(
                        INSERT_DEDUP,
                        q ->
                                q.addParameter(webhookId)
                                        .addParameter(signature)
                                        .addParameter(seenAt)
                                        .addParameter(expiresAt)
                                        .executeUpdate());
        return affected != null && affected > 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> loadTargets(String webhookId) {
        String json =
                queryWithTransaction(
                        SELECT_TARGETS,
                        q -> q.addParameter(webhookId).executeAndFetchFirst(String.class));
        if (json == null) {
            return Collections.emptyMap();
        }
        return readValue(json, Map.class);
    }
}
