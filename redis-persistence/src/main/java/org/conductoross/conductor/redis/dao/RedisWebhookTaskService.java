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

import java.util.Collections;
import java.util.Set;

import org.conductoross.conductor.service.webhook.WebhookTaskHashing;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis-backed {@link WebhookTaskService}. Hash → Set<taskId> mapping in Redis. Same {@link
 * WebhookTaskHashing} as the SQL impls so hashes are interchangeable across backings.
 */
@Component(value = "webhookTaskService")
@Conditional(AnyRedisCondition.class)
public class RedisWebhookTaskService extends BaseDynoDAO implements WebhookTaskService {

    private static final String WEBHOOK_HASH = "WEBHOOK_HASH";

    public RedisWebhookTaskService(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void put(TaskModel task, int workflowVersion) {
        String hash = WebhookTaskHashing.computeHash(task, workflowVersion);
        jedisProxy.sadd(nsKey(WEBHOOK_HASH, hash), task.getTaskId());
    }

    @Override
    public Set<String> get(String hash) {
        Set<String> members = jedisProxy.smembers(nsKey(WEBHOOK_HASH, hash));
        return members == null ? Collections.emptySet() : members;
    }

    @Override
    public void remove(String hash, String taskId) {
        jedisProxy.srem(nsKey(WEBHOOK_HASH, hash), taskId);
    }
}
