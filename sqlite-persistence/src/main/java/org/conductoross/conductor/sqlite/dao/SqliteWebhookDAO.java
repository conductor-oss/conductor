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
package org.conductoross.conductor.sqlite.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.sqlite.dao.SqliteBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;
import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

/**
 * SQLite-backed {@link WebhookDAO}. Same matcher-recomputation-on-read design as the postgres impl
 * — see PostgresWebhookDAO header for rationale.
 */
@Slf4j
public class SqliteWebhookDAO extends SqliteBaseDAO implements WebhookDAO {

    private static final String INSERT_WEBHOOK =
            "INSERT INTO webhook (webhook_id, json_data) VALUES (?, ?) "
                    + "ON CONFLICT (webhook_id) DO UPDATE "
                    + "SET json_data = excluded.json_data, modified_on = CURRENT_TIMESTAMP";
    private static final String SELECT_WEBHOOK =
            "SELECT json_data FROM webhook WHERE webhook_id = ?";
    private static final String SELECT_ALL_WEBHOOKS = "SELECT json_data FROM webhook";
    private static final String DELETE_WEBHOOK = "DELETE FROM webhook WHERE webhook_id = ?";

    private static final String INSERT_EVENT =
            "INSERT INTO incoming_webhook_event (event_id, json_data) VALUES (?, ?) "
                    + "ON CONFLICT (event_id) DO UPDATE "
                    + "SET json_data = excluded.json_data";
    private static final String SELECT_EVENT =
            "SELECT json_data FROM incoming_webhook_event WHERE event_id = ?";
    private static final String DELETE_EVENT =
            "DELETE FROM incoming_webhook_event WHERE event_id = ?";

    private static final String INSERT_TARGETS =
            "INSERT INTO webhook_target_workflows (webhook_id, json_data) VALUES (?, ?) "
                    + "ON CONFLICT (webhook_id) DO UPDATE "
                    + "SET json_data = excluded.json_data";
    private static final String SELECT_TARGETS =
            "SELECT json_data FROM webhook_target_workflows WHERE webhook_id = ?";
    private static final String DELETE_TARGETS =
            "DELETE FROM webhook_target_workflows WHERE webhook_id = ?";

    private final MetadataDAO metadataDAO;

    public SqliteWebhookDAO(
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
        Map<String, Integer> targets = loadTargets(webhookId);
        if (targets.isEmpty()) {
            return Collections.emptyMap();
        }
        return computeMatchers(targets);
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

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> computeMatchers(Map<String, Integer> targets) {
        Map<String, Map<String, Object>> computed = new HashMap<>();
        targets.forEach(
                (workflowName, wfVersion) -> {
                    Optional<WorkflowDef> def = metadataDAO.getWorkflowDef(workflowName, wfVersion);
                    if (def.isEmpty()) {
                        return;
                    }
                    for (WorkflowTask task : def.get().collectTasks()) {
                        String type = task.getType();
                        if (!WAIT_FOR_WEBHOOK.equals(type)
                                && !TaskType.WAIT.toString().equals(type)) {
                            continue;
                        }
                        Object raw = task.getInputParameters().get("matches");
                        if (raw instanceof Map<?, ?> m && !CollectionUtils.isEmpty(m)) {
                            String key =
                                    workflowName
                                            + WEBHOOK_DELIMITER
                                            + wfVersion
                                            + WEBHOOK_DELIMITER
                                            + task.getTaskReferenceName();
                            computed.put(key, (Map<String, Object>) m);
                        }
                    }
                });
        return computed;
    }
}
