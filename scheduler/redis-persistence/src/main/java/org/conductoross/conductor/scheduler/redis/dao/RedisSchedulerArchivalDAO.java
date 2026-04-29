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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
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
    private static final String NAMESPACE_SEP = ".";
    private static final String SCHEDULER_ARCHIVAL_PREFIX = "SCHEDULER.ARCHIVAL";
    private static final String SCHEDULER_ARCHIVAL_SCHED_PREFIX = "SCHEDULER.ARCHIVAL_SCHED";
    private static final String SCHEDULER_ARCHIVAL_SCHEDNAMES = "SCHEDULER.ARCHIVAL_SCHEDNAMES";

    private final ConductorProperties conductorProperties;
    private final RedisProperties redisProperties;
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
        this.conductorProperties = conductorProperties;
        this.redisProperties = redisProperties;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Builds a namespaced key. Mirrors {@code BaseDynoDAO.nsKey()} which is package-private and not
     * accessible from this package.
     */
    private String nsKey(String... nsValues) {
        StringBuilder sb = new StringBuilder();
        String ns = redisProperties.getWorkflowNamespacePrefix();
        if (StringUtils.isNotBlank(ns)) {
            sb.append(ns).append(NAMESPACE_SEP);
        }
        String stack = conductorProperties.getStack();
        if (StringUtils.isNotBlank(stack)) {
            sb.append(stack).append(NAMESPACE_SEP);
        }
        String domain = redisProperties.getKeyspaceDomain();
        if (StringUtils.isNotBlank(domain)) {
            sb.append(domain).append(NAMESPACE_SEP);
        }
        for (String nsValue : nsValues) {
            sb.append(nsValue).append(NAMESPACE_SEP);
        }
        return StringUtils.removeEnd(sb.toString(), NAMESPACE_SEP);
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
            String orgId, String query, String freeText, int start, int count, List<String> sort) {
        Monitors.recordDaoRequests(DAO_NAME, "searchScheduledExecutions", "n/a", "n/a");
        if (query != null && !query.isEmpty()) {
            // Get IDs from sorted set, filter out expired
            List<String> allIds = jedisProxy.zrange(archivalSchedKey(query), 0, -1);
            List<String> liveIds = filterLive(allIds);
            Collections.reverse(liveIds); // DESC by scheduledTime

            long totalHits = liveIds.size();
            int end = Math.min(start + count, liveIds.size());
            List<String> page = start < liveIds.size() ? liveIds.subList(start, end) : List.of();
            return new SearchResult<>(totalHits, page);
        }

        // Free text search: iterate all schedules, fetch live records, filter
        Set<String> scheduleNames = jedisProxy.smembers(keySchedNames());
        if (scheduleNames == null) {
            scheduleNames = Set.of();
        }

        List<WorkflowScheduleExecutionModel> allModels = new ArrayList<>();
        for (String schedName : scheduleNames) {
            List<String> ids = jedisProxy.zrange(archivalSchedKey(schedName), 0, -1);
            for (String id : ids) {
                String json = jedisProxy.get(archivalKey(id));
                if (json != null) {
                    allModels.add(fromJson(json, WorkflowScheduleExecutionModel.class));
                }
            }
        }

        if (freeText != null && !freeText.isEmpty() && !"*".equals(freeText)) {
            String term = freeText.toLowerCase();
            allModels =
                    allModels.stream()
                            .filter(
                                    m -> {
                                        String sn =
                                                m.getScheduleName() != null
                                                        ? m.getScheduleName()
                                                        : "";
                                        String wn =
                                                m.getWorkflowName() != null
                                                        ? m.getWorkflowName()
                                                        : "";
                                        String wid =
                                                m.getWorkflowId() != null ? m.getWorkflowId() : "";
                                        return sn.toLowerCase().contains(term)
                                                || wn.toLowerCase().contains(term)
                                                || wid.toLowerCase().contains(term);
                                    })
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
            String orgId, Set<String> executionIds) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionsByIds", "n/a", "n/a");
        if (executionIds == null || executionIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, WorkflowScheduleExecutionModel> result = new HashMap<>();
        for (String id : executionIds) {
            String json = jedisProxy.get(archivalKey(id));
            if (json != null) {
                result.put(id, fromJson(json, WorkflowScheduleExecutionModel.class));
            }
        }
        return result;
    }

    @Override
    public WorkflowScheduleExecutionModel getExecutionById(String orgId, String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionById", "n/a", "n/a");
        String json = jedisProxy.get(archivalKey(executionId));
        return json == null ? null : fromJson(json, WorkflowScheduleExecutionModel.class);
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

    // =========================================================================
    // Helpers — own copies since BaseDynoDAO methods are package-private
    // =========================================================================

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new NonTransientException("Failed to deserialize JSON", e);
        }
    }
}
