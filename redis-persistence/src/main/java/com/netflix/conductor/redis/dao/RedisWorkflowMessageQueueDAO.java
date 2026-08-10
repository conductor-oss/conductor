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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis List-backed implementation of {@link WorkflowMessageQueueDAO}.
 *
 * <p>Each workflow's queue is stored as a Redis List with key {@code wmq:{workflowId}}. Messages
 * are enqueued at the tail (RPUSH) and dequeued from the head via LRANGE + LTRIM. These two
 * commands are not atomic by themselves, but Conductor's per-workflow execution lock ensures that
 * only one decide() thread processes a given workflow at a time, so concurrent pops for the same
 * workflow ID cannot occur.
 *
 * <p>A TTL is applied on every push so that orphaned queues expire automatically.
 */
public class RedisWorkflowMessageQueueDAO extends BaseDynoDAO implements WorkflowMessageQueueDAO {

    private static final String KEY_PREFIX = "wmq:";

    private final WorkflowMessageQueueProperties wmqProperties;

    public RedisWorkflowMessageQueueDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties,
            WorkflowMessageQueueProperties wmqProperties) {
        super(jedisProxy, objectMapper, conductorProperties, redisProperties);
        this.wmqProperties = wmqProperties;
    }

    @Override
    public void push(String workflowId, WorkflowMessage message) {
        String key = queueKey(workflowId);
        long currentSize = jedisProxy.llen(key);
        if (currentSize >= wmqProperties.getMaxQueueSize()) {
            throw new IllegalStateException(
                    "Workflow message queue for workflowId="
                            + workflowId
                            + " has reached the maximum size of "
                            + wmqProperties.getMaxQueueSize());
        }
        jedisProxy.rpush(key, toJson(message));
        jedisProxy.expire(key, (int) wmqProperties.getTtlSeconds());
    }

    @Override
    public List<WorkflowMessage> pop(String workflowId, int maxCount) {
        String key = queueKey(workflowId);
        // LRANGE reads [0, maxCount-1]; LTRIM removes those items from the head.
        // Not atomic, but safe: Conductor's workflow lock prevents concurrent pops
        // for the same workflow ID.
        List<String> items = jedisProxy.lrange(key, 0, maxCount - 1L);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        jedisProxy.ltrim(key, items.size(), -1);
        return items.stream()
                .map(json -> readValue(json, WorkflowMessage.class))
                .collect(Collectors.toList());
    }

    @Override
    public long size(String workflowId) {
        return jedisProxy.llen(queueKey(workflowId));
    }

    @Override
    public void delete(String workflowId) {
        jedisProxy.del(queueKey(workflowId));
    }

    private String queueKey(String workflowId) {
        return nsKey(KEY_PREFIX + workflowId);
    }
}
