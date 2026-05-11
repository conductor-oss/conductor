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
package org.conductoross.conductor.scheduler.redis.dao;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerCacheDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * Redis-backed implementation of {@link SchedulerCacheDAO}. Intended to sit in front of a SQL
 * primary store (Postgres/MySQL) to avoid DB round-trips on the hot polling path.
 *
 * <p>Key layout (matches Orkes Conductor for drop-in compatibility):
 *
 * <ul>
 *   <li>{@code WORKFLOW_SCHEDULES} — Hash: scheduleName → JSON
 *   <li>{@code WORKFLOW_SCHEDULES_RUNTIME:{scheduleName}} — String: epoch millis
 * </ul>
 */
public class RedisSchedulerCacheDAO extends BaseDynoDAO implements SchedulerCacheDAO {

    private static final String ALL_WORKFLOW_SCHEDULES = "WORKFLOW_SCHEDULES";
    private static final String WORKFLOW_SCHEDULES_RUNTIME = "WORKFLOW_SCHEDULES_RUNTIME";

    public RedisSchedulerCacheDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties) {
        super(jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel workflowSchedule) {
        jedisProxy.hset(
                nsKey(ALL_WORKFLOW_SCHEDULES),
                workflowSchedule.getName(),
                toJson(workflowSchedule));
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        String json = jedisProxy.hget(nsKey(ALL_WORKFLOW_SCHEDULES), name);
        if (json == null) {
            return null;
        }
        return readValue(json, WorkflowScheduleModel.class);
    }

    @Override
    public boolean exists(String name) {
        return jedisProxy.hget(nsKey(ALL_WORKFLOW_SCHEDULES), name) != null;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        jedisProxy.hdel(nsKey(ALL_WORKFLOW_SCHEDULES), name);
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        String value = jedisProxy.get(nsKey(WORKFLOW_SCHEDULES_RUNTIME, scheduleName));
        if (StringUtils.isBlank(value)) {
            return -1L;
        }
        return Long.parseLong(value);
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMilli) {
        jedisProxy.set(nsKey(WORKFLOW_SCHEDULES_RUNTIME, scheduleName), Long.toString(epochMilli));
    }
}
