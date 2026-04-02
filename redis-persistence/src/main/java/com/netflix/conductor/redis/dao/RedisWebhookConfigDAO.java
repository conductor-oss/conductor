/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.redis.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.common.webhook.WebhookConfig;
import org.conductoross.conductor.common.webhook.WebhookConfigDAO;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis-backed implementation of {@link WebhookConfigDAO}.
 *
 * <p>Stores webhook configs and their routing matchers in Redis, enabling durable persistence
 * across server restarts and sharing across multiple Conductor nodes.
 *
 * <p><strong>Bean activation:</strong> registered when any Redis configuration is present (i.e.
 * {@link AnyRedisCondition} is satisfied). The in-memory fallback ({@code
 * InMemoryWebhookConfigDAO}) is suppressed automatically by its own
 * {@code @ConditionalOnMissingBean} declaration.
 *
 * <p>Key layout:
 *
 * <ul>
 *   <li>{@code [ns.]WEBHOOK_CONFIG.[id]} — JSON-serialized {@link WebhookConfig}
 *   <li>{@code [ns.]WEBHOOK_CONFIG_IDS} — Redis Set of all known config IDs (for {@link #getAll()})
 *   <li>{@code [ns.]WEBHOOK_MATCHERS.[webhookId]} — JSON-serialized matcher index map
 * </ul>
 */
@Component
@Conditional(AnyRedisCondition.class)
public class RedisWebhookConfigDAO extends BaseDynoDAO implements WebhookConfigDAO {

    private static final String WEBHOOK_CONFIG = "WEBHOOK_CONFIG";
    private static final String WEBHOOK_CONFIG_IDS = "WEBHOOK_CONFIG_IDS";
    private static final String WEBHOOK_MATCHERS = "WEBHOOK_MATCHERS";

    private static final TypeReference<Map<String, Map<String, Object>>> MATCHERS_TYPE =
            new TypeReference<>() {};

    public RedisWebhookConfigDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void save(String id, WebhookConfig config) {
        jedisProxy.set(nsKey(WEBHOOK_CONFIG, id), toJson(config));
        jedisProxy.sadd(nsKey(WEBHOOK_CONFIG_IDS), id);
    }

    @Override
    public WebhookConfig get(String id) {
        String json = jedisProxy.get(nsKey(WEBHOOK_CONFIG, id));
        if (json == null) {
            return null;
        }
        return readValue(json, WebhookConfig.class);
    }

    @Override
    public void remove(String id) {
        jedisProxy.del(nsKey(WEBHOOK_CONFIG, id));
        jedisProxy.srem(nsKey(WEBHOOK_CONFIG_IDS), id);
    }

    @Override
    public List<WebhookConfig> getAll() {
        Set<String> ids = jedisProxy.smembers(nsKey(WEBHOOK_CONFIG_IDS));
        List<WebhookConfig> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            WebhookConfig config = get(id);
            if (config != null) {
                result.add(config);
            }
        }
        return result;
    }

    @Override
    public void saveMatchers(String webhookId, Map<String, Map<String, Object>> matchers) {
        jedisProxy.set(nsKey(WEBHOOK_MATCHERS, webhookId), toJson(matchers));
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        String json = jedisProxy.get(nsKey(WEBHOOK_MATCHERS, webhookId));
        if (json == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MATCHERS_TYPE);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to deserialize webhook matchers for id=" + webhookId, e);
        }
    }

    @Override
    public void removeMatchers(String webhookId) {
        jedisProxy.del(nsKey(WEBHOOK_MATCHERS, webhookId));
    }
}
