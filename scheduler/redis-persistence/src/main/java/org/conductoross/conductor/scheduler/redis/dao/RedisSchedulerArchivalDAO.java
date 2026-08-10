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

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.archive.SchedulerSearchQuery;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;

/**
 * Redis implementation of {@link SchedulerArchivalDAO}.
 *
 * <p>Extends {@link BaseDynoDAO} for jedis/objectMapper management. Each archival record is stored
 * as an individual Redis string key with a TTL (default 7 days). Records automatically expire
 * without explicit cleanup.
 *
 * <p>Data model:
 *
 * <ul>
 *   <li>SCHEDULER.ARCHIVAL:{executionId} — String with TTL: JSON blob
 *   <li>SCHEDULER.ARCHIVAL_SCHED:{scheduleName} — Sorted Set: score=scheduledTime,
 *       member=executionId
 *   <li>SCHEDULER.ARCHIVAL_SCHEDNAMES — Set of schedule names with archival records
 * </ul>
 */
public class RedisSchedulerArchivalDAO extends BaseDynoDAO implements SchedulerArchivalDAO {

    private static final Logger log = LoggerFactory.getLogger(RedisSchedulerArchivalDAO.class);
    private static final long DEFAULT_TTL_SECONDS = 7 * 24 * 60 * 60; // 7 days

    private static final String DAO_NAME = "redis";
    private static final String SCHEDULER_ARCHIVAL_PREFIX = "SCHEDULER.ARCHIVAL";
    private static final String SCHEDULER_ARCHIVAL_SCHED_PREFIX = "SCHEDULER.ARCHIVAL_SCHED";
    private static final String SCHEDULER_ARCHIVAL_SCHEDNAMES = "SCHEDULER.ARCHIVAL_SCHEDNAMES";

    private final long ttlSeconds;

    public RedisSchedulerArchivalDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties) {
        this(jedisProxy, objectMapper, conductorProperties, redisProperties, DEFAULT_TTL_SECONDS);
    }

    public RedisSchedulerArchivalDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties,
            long ttlSeconds) {
        super(jedisProxy, objectMapper, conductorProperties, redisProperties);
        this.ttlSeconds = ttlSeconds;
    }

    private String archivalKey(String executionId) {
        return nsKey(SCHEDULER_ARCHIVAL_PREFIX, executionId);
    }

    private String archivalSchedKey(String scheduleName) {
        return nsKey(SCHEDULER_ARCHIVAL_SCHED_PREFIX, scheduleName);
    }

    private String keySchedNames() {
        return nsKey(SCHEDULER_ARCHIVAL_SCHEDNAMES);
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel model) {
        Monitors.recordDaoRequests(DAO_NAME, "saveArchivalRecord", "n/a", "n/a");
        String execId = model.getExecutionId();

        // Store JSON with TTL
        jedisProxy.setWithExpiry(archivalKey(execId), toJson(model), ttlSeconds);

        // Index by schedule name
        double score =
                model.getScheduledTime() != null ? model.getScheduledTime().doubleValue() : 0;
        jedisProxy.zadd(archivalSchedKey(model.getScheduleName()), score, execId);

        // Track schedule name
        jedisProxy.sadd(keySchedNames(), model.getScheduleName());
    }

    @Override
    public SearchResult<String> searchScheduledExecutions(
            String query, String freeText, int start, int count, List<String> sort) {
        Monitors.recordDaoRequests(DAO_NAME, "searchScheduledExecutions", "n/a", "n/a");

        SchedulerSearchQuery parsed = SchedulerSearchQuery.parse(query);

        // Determine which schedule names to query
        Collection<String> targetScheduleNames;
        if (parsed.hasScheduleNames()) {
            targetScheduleNames = parsed.getScheduleNames();
        } else {
            Set<String> all = jedisProxy.smembers(keySchedNames());
            targetScheduleNames = all != null ? all : Set.of();
        }

        // Collect all live models from targeted schedules
        List<WorkflowScheduleExecutionModel> allModels = new ArrayList<>();
        for (String schedName : targetScheduleNames) {
            List<String> ids = jedisProxy.zrange(archivalSchedKey(schedName), 0, -1);
            for (String id : ids) {
                String json = jedisProxy.get(archivalKey(id));
                if (json != null) {
                    allModels.add(readValue(json, WorkflowScheduleExecutionModel.class));
                }
            }
        }

        // Filter by state
        if (parsed.hasStates()) {
            Set<String> stateSet = new HashSet<>(parsed.getStates());
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getState() != null
                                                    && stateSet.contains(m.getState().name()))
                            .collect(Collectors.toList());
        }

        // Filter by time range
        if (parsed.getScheduledTimeAfter() != null) {
            long after = parsed.getScheduledTimeAfter();
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getScheduledTime() != null
                                                    && m.getScheduledTime() > after)
                            .collect(Collectors.toList());
        }
        if (parsed.getScheduledTimeBefore() != null) {
            long before = parsed.getScheduledTimeBefore();
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getScheduledTime() != null
                                                    && m.getScheduledTime() < before)
                            .collect(Collectors.toList());
        }

        // Filter by workflow name (substring match)
        if (parsed.hasWorkflowName()) {
            String term = parsed.getWorkflowName().toLowerCase();
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getWorkflowName() != null
                                                    && m.getWorkflowName()
                                                            .toLowerCase()
                                                            .contains(term))
                            .collect(Collectors.toList());
        }

        // Filter by execution ID (exact match)
        if (parsed.hasExecutionId()) {
            String execId = parsed.getExecutionId();
            allModels =
                    allModels.stream()
                            .filter(m -> execId.equals(m.getExecutionId()))
                            .collect(Collectors.toList());
        }

        // Sort by scheduledTime DESC
        allModels.sort(
                (a, b) ->
                        Long.compare(
                                b.getScheduledTime() != null ? b.getScheduledTime() : 0,
                                a.getScheduledTime() != null ? a.getScheduledTime() : 0));

        long totalHits = allModels.size();
        int end = Math.min(start + count, allModels.size());
        List<String> ids =
                allModels.subList(start < allModels.size() ? start : allModels.size(), end).stream()
                        .map(WorkflowScheduleExecutionModel::getExecutionId)
                        .collect(Collectors.toList());
        return new SearchResult<>(totalHits, ids);
    }

    @Override
    public Map<String, WorkflowScheduleExecutionModel> getExecutionsByIds(
            Set<String> executionIds) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionsByIds", "n/a", "n/a");
        if (executionIds == null || executionIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, WorkflowScheduleExecutionModel> result = new HashMap<>();
        for (String id : executionIds) {
            String json = jedisProxy.get(archivalKey(id));
            if (json != null) {
                result.put(id, readValue(json, WorkflowScheduleExecutionModel.class));
            }
        }
        return result;
    }

    @Override
    public WorkflowScheduleExecutionModel getExecutionById(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionById", "n/a", "n/a");
        String json = jedisProxy.get(archivalKey(executionId));
        return json == null ? null : readValue(json, WorkflowScheduleExecutionModel.class);
    }

    @Override
    public void cleanupOldRecords(int archivalMaxRecords, int archivalMaxRecordThreshold) {
        Monitors.recordDaoRequests(DAO_NAME, "cleanupOldRecords", "n/a", "n/a");
        Set<String> scheduleNames = jedisProxy.smembers(keySchedNames());
        if (scheduleNames == null) {
            return;
        }

        for (String scheduleName : scheduleNames) {
            String schedKey = archivalSchedKey(scheduleName);

            // First, prune stale entries (expired keys) from the sorted set using batch check
            List<String> allIds = jedisProxy.zrange(schedKey, 0, -1);
            if (!allIds.isEmpty()) {
                String[] keys = allIds.stream().map(this::archivalKey).toArray(String[]::new);
                List<String> values = jedisProxy.mget(keys);
                for (int i = 0; i < allIds.size(); i++) {
                    if (values.get(i) == null) {
                        jedisProxy.zrem(schedKey, allIds.get(i));
                    }
                }
            }

            Long count = jedisProxy.zcard(schedKey);
            if (count == null || count <= archivalMaxRecordThreshold) {
                continue;
            }

            // Refetch after pruning
            allIds = jedisProxy.zrange(schedKey, 0, -1);
            if (allIds.size() <= archivalMaxRecords) {
                continue;
            }

            int toDeleteCount = allIds.size() - archivalMaxRecords;
            List<String> toDelete = allIds.subList(0, toDeleteCount);

            for (String execId : toDelete) {
                jedisProxy.zrem(schedKey, execId);
                jedisProxy.del(archivalKey(execId));
            }
            log.info(
                    "Cleaned up {} old archival records for schedule: {}",
                    toDelete.size(),
                    scheduleName);
        }
    }

    /** Returns only the IDs whose backing string key still exists (not expired). */
    private List<String> filterLive(List<String> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        String[] keys = ids.stream().map(this::archivalKey).toArray(String[]::new);
        List<String> values = jedisProxy.mget(keys);
        List<String> live = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (values.get(i) != null) {
                live.add(ids.get(i));
            }
        }
        return live;
    }
}
