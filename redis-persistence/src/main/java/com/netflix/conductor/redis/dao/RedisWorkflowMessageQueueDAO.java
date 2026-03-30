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

import java.util.Arrays;
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
 * are enqueued at the tail and dequeued from the head. Both operations use Lua scripts executed via
 * {@code EVAL} to ensure atomicity at the Redis layer, making them safe in multi-node Conductor
 * deployments regardless of the configured execution lock type.
 *
 * <p>A TTL is applied on every push so that orphaned queues expire automatically.
 */
public class RedisWorkflowMessageQueueDAO extends BaseDynoDAO implements WorkflowMessageQueueDAO {

    private static final String KEY_PREFIX = "wmq:";

    /**
     * Atomically pops up to {@code ARGV[1]} messages from the head of {@code KEYS[1]}. Returns the
     * popped items as a list.
     */
    private static final String POP_SCRIPT =
            "local items = redis.call('LRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1)\n"
                    + "if #items == 0 then return {} end\n"
                    + "redis.call('LTRIM', KEYS[1], #items, -1)\n"
                    + "return items";

    /**
     * Atomically checks LLEN against {@code ARGV[1]} (maxSize), RPUSHes {@code ARGV[2]} if under
     * the limit, then EXPIREs the key for {@code ARGV[3]} seconds. Returns {@code 'OK'} on success
     * or an error reply {@code 'QUEUE_FULL'} if the queue is at capacity.
     */
    private static final String PUSH_SCRIPT =
            "local current = tonumber(redis.call('LLEN', KEYS[1]))\n"
                    + "if current >= tonumber(ARGV[1]) then\n"
                    + "  return redis.error_reply('QUEUE_FULL')\n"
                    + "end\n"
                    + "redis.call('RPUSH', KEYS[1], ARGV[2])\n"
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))\n"
                    + "return 'OK'";

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
        long ttl = wmqProperties.getTtlSeconds();
        if (ttl <= 0 || ttl > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "ttlSeconds must be between 1 and " + Integer.MAX_VALUE + " but was " + ttl);
        }
        try {
            jedisProxy.eval(
                    PUSH_SCRIPT,
                    Collections.singletonList(key),
                    Arrays.asList(
                            String.valueOf(wmqProperties.getMaxQueueSize()),
                            toJson(message),
                            String.valueOf(ttl)));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("QUEUE_FULL")) {
                throw new IllegalStateException(
                        "Workflow message queue for workflowId="
                                + workflowId
                                + " has reached the maximum size of "
                                + wmqProperties.getMaxQueueSize());
            }
            throw e;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WorkflowMessage> pop(String workflowId, int maxCount) {
        String key = queueKey(workflowId);
        Object result =
                jedisProxy.eval(
                        POP_SCRIPT,
                        Collections.singletonList(key),
                        Collections.singletonList(String.valueOf(maxCount)));
        List<String> items = (List<String>) result;
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
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
