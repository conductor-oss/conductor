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
package org.conductoross.conductor.scheduler.dao;

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis implementation of {@link SchedulerCacheDAO}.
 *
 * <p>Provides a fast in-memory cache layer on top of the authoritative SQL {@link SchedulerDAO}.
 * Key scheme mirrors Orkes Conductor's {@code SchedulerRedisCacheDAO}:
 *
 * <ul>
 *   <li>Hash {@code WORKFLOW_SCHEDULES} — field=scheduleName, value=JSON of {@link WorkflowSchedule}
 *   <li>String {@code WORKFLOW_SCHEDULES_RUNTIME:<scheduleName>} — next-run epoch millis
 * </ul>
 */
public class RedisSchedulerDAO implements SchedulerCacheDAO {

    private static final Logger log = LoggerFactory.getLogger(RedisSchedulerDAO.class);

    private static final String SCHEDULES_HASH_KEY = "WORKFLOW_SCHEDULES";
    private static final String RUNTIME_KEY_PREFIX = "WORKFLOW_SCHEDULES_RUNTIME:";

    private final JedisProxy jedisProxy;
    private final ObjectMapper objectMapper;

    public RedisSchedulerDAO(JedisProxy jedisProxy, ObjectMapper objectMapper) {
        this.jedisProxy = jedisProxy;
        this.objectMapper = objectMapper;
    }

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        jedisProxy.hset(SCHEDULES_HASH_KEY, schedule.getName(), toJson(schedule));
    }

    @Override
    public WorkflowSchedule findScheduleByName(String name) {
        String json = jedisProxy.hget(SCHEDULES_HASH_KEY, name);
        if (json == null) {
            return null;
        }
        return fromJson(json, WorkflowSchedule.class);
    }

    @Override
    public boolean exists(String name) {
        return jedisProxy.hexists(SCHEDULES_HASH_KEY, name);
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        jedisProxy.hdel(SCHEDULES_HASH_KEY, name);
        jedisProxy.del(RUNTIME_KEY_PREFIX + name);
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        String value = jedisProxy.get(RUNTIME_KEY_PREFIX + scheduleName);
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid runtime value for schedule {}: {}", scheduleName, value);
            return -1L;
        }
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        jedisProxy.set(RUNTIME_KEY_PREFIX + scheduleName, Long.toString(epochMillis));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }
}
