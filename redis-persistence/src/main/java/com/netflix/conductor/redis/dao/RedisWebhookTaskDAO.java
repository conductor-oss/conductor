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

import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.common.webhook.WebhookTaskDAO;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis-backed implementation of {@link WebhookTaskDAO}.
 *
 * <p>Stores waiting task IDs in Redis Sets keyed by routing hash. This implementation is suitable
 * for multi-node deployments where tasks may be registered on one node and completed by a webhook
 * event arriving on another.
 *
 * <p><strong>Bean activation:</strong> registered when any Redis configuration is present (i.e.
 * {@link AnyRedisCondition} is satisfied). The in-memory fallback ({@code InMemoryWebhookTaskDAO})
 * is suppressed automatically by its own {@code @ConditionalOnMissingBean} declaration.
 *
 * <p><strong>Atomicity note:</strong> {@link #popAll(String)} uses the non-atomic default (get +
 * individual removes). Under very high concurrent load with identical routing hashes, two
 * simultaneous inbound events could both claim the same task IDs. For deployments with extreme
 * fan-out or very high webhook throughput, override {@code popAll()} with a Lua SMEMBERS+DEL script
 * for true atomicity.
 *
 * <p>Key layout: {@code [namespace.]WEBHOOK_TASKS.[hash]}
 */
@Component
@Conditional(AnyRedisCondition.class)
public class RedisWebhookTaskDAO extends BaseDynoDAO implements WebhookTaskDAO {

    private static final String WEBHOOK_TASKS = "WEBHOOK_TASKS";

    public RedisWebhookTaskDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void put(String hash, String taskId) {
        jedisProxy.sadd(nsKey(WEBHOOK_TASKS, hash), taskId);
    }

    @Override
    public List<String> get(String hash) {
        return new ArrayList<>(jedisProxy.smembers(nsKey(WEBHOOK_TASKS, hash)));
    }

    @Override
    public void remove(String hash, String taskId) {
        jedisProxy.srem(nsKey(WEBHOOK_TASKS, hash), taskId);
    }
}
