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
package org.conductoross.conductor.redis.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;
import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

/**
 * Redis-backed {@link WebhookDAO}.
 *
 * <p>Data model:
 *
 * <ul>
 *   <li>{@code WEBHOOK_CONFIG} hash: webhook_id → JSON({@link WebhookConfig})
 *   <li>{@code WEBHOOK_EVENT} hash: event_id → JSON({@link IncomingWebhookEvent})
 *   <li>{@code WEBHOOK_TARGETS} hash: webhook_id → JSON(Map&lt;workflowName, version&gt;)
 * </ul>
 *
 * <p>Matchers are recomputed from {@link MetadataDAO} on every {@link #getMatchers} call — same
 * design as the SQL impls. See {@code PostgresWebhookDAO} for rationale.
 */
@Component(value = "webhookDAO")
@Conditional(AnyRedisCondition.class)
@Slf4j
public class RedisWebhookDAO extends BaseDynoDAO implements WebhookDAO {

    private static final String WEBHOOK_CONFIG = "WEBHOOK_CONFIG";
    private static final String WEBHOOK_EVENT = "WEBHOOK_EVENT";
    private static final String WEBHOOK_TARGETS = "WEBHOOK_TARGETS";

    private final MetadataDAO metadataDAO;

    public RedisWebhookDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties,
            MetadataDAO metadataDAO) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
        this.metadataDAO = metadataDAO;
    }

    @Override
    public void createWebhook(String id, WebhookConfig webhookConfig) {
        jedisProxy.hset(nsKey(WEBHOOK_CONFIG), id, toJson(webhookConfig));
    }

    @Override
    public WebhookConfig getWebhook(String webhookId) {
        String json = jedisProxy.hget(nsKey(WEBHOOK_CONFIG), webhookId);
        return json == null ? null : readValue(json, WebhookConfig.class);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        List<String> values = jedisProxy.hvals(nsKey(WEBHOOK_CONFIG));
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<WebhookConfig> out = new ArrayList<>(values.size());
        for (String json : values) {
            out.add(readValue(json, WebhookConfig.class));
        }
        return out;
    }

    @Override
    public void removeWebhook(String id) {
        if (jedisProxy.hget(nsKey(WEBHOOK_CONFIG), id) == null) {
            throw new NotFoundException("Webhook with id " + id + " not found");
        }
        jedisProxy.hdel(nsKey(WEBHOOK_CONFIG), id);
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        jedisProxy.hset(nsKey(WEBHOOK_EVENT), id, toJson(event));
    }

    @Override
    public IncomingWebhookEvent getWebhookEvent(String messageId) {
        String json = jedisProxy.hget(nsKey(WEBHOOK_EVENT), messageId);
        return json == null ? null : readValue(json, IncomingWebhookEvent.class);
    }

    @Override
    public void removeWebhookEvent(String id) {
        jedisProxy.hdel(nsKey(WEBHOOK_EVENT), id);
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
        jedisProxy.hset(nsKey(WEBHOOK_TARGETS), webhookConfig.getId(), toJson(targets));
    }

    @Override
    public void removeMatchers(String id) {
        jedisProxy.hdel(nsKey(WEBHOOK_TARGETS), id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> loadTargets(String webhookId) {
        String json = jedisProxy.hget(nsKey(WEBHOOK_TARGETS), webhookId);
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
