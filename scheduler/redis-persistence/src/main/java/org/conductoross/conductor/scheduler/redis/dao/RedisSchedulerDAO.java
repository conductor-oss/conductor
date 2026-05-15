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
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * Redis implementation of {@link SchedulerDAO}.
 *
 * <p>Extends {@link BaseDynoDAO} for jedis/objectMapper management. Data model:
 *
 * <ul>
 *   <li>SCHEDULER.DEFS — Hash: scheduleName → JSON
 *   <li>SCHEDULER.ALL — Set: all schedule names
 *   <li>SCHEDULER.WF:{workflowName} — Set: schedule names using this workflow
 *   <li>SCHEDULER.NEXT_RUN — Hash: scheduleName → epoch millis string
 *   <li>SCHEDULER.EXEC — Hash: executionId → JSON
 *   <li>SCHEDULER.EXEC_SCHED:{scheduleName} — Set: execution IDs for this schedule
 *   <li>SCHEDULER.PENDING — Set: execution IDs in POLLED state
 * </ul>
 */
public class RedisSchedulerDAO extends BaseDynoDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(RedisSchedulerDAO.class);

    private static final String SCHEDULER_DEFS = "SCHEDULER.DEFS";
    private static final String SCHEDULER_ALL = "SCHEDULER.ALL";
    private static final String SCHEDULER_NEXT_RUN = "SCHEDULER.NEXT_RUN";
    private static final String SCHEDULER_EXEC = "SCHEDULER.EXEC";
    private static final String SCHEDULER_PENDING = "SCHEDULER.PENDING";
    private static final String SCHEDULER_WF_PREFIX = "SCHEDULER.WF";
    private static final String SCHEDULER_EXEC_SCHED_PREFIX = "SCHEDULER.EXEC_SCHED";

    private static final String DAO_NAME = "redis";

    public RedisSchedulerDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties) {
        super(jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    private String keyDefs() {
        return nsKey(SCHEDULER_DEFS);
    }

    private String keyAll() {
        return nsKey(SCHEDULER_ALL);
    }

    private String keyNextRun() {
        return nsKey(SCHEDULER_NEXT_RUN);
    }

    private String keyExec() {
        return nsKey(SCHEDULER_EXEC);
    }

    private String keyPending() {
        return nsKey(SCHEDULER_PENDING);
    }

    private String wfIndexKey(String workflowName) {
        return nsKey(SCHEDULER_WF_PREFIX, workflowName);
    }

    private String execSchedKey(String scheduleName) {
        return nsKey(SCHEDULER_EXEC_SCHED_PREFIX, scheduleName);
    }

    // =========================================================================
    // Schedule CRUD
    // =========================================================================

    @Override
    public void updateSchedule(WorkflowScheduleModel schedule) {
        Monitors.recordDaoRequests(DAO_NAME, "updateSchedule", "n/a", "n/a");
        String name = schedule.getName();

        // Remove from old workflow index if workflow name changed
        String oldJson = jedisProxy.hget(keyDefs(), name);
        if (oldJson != null) {
            WorkflowScheduleModel old = readValue(oldJson, WorkflowScheduleModel.class);
            if (old.getStartWorkflowRequest() != null) {
                String oldWfName = old.getStartWorkflowRequest().getName();
                String newWfName =
                        schedule.getStartWorkflowRequest() != null
                                ? schedule.getStartWorkflowRequest().getName()
                                : null;
                if (oldWfName != null && !oldWfName.equals(newWfName)) {
                    jedisProxy.srem(wfIndexKey(oldWfName), name);
                }
            }
        }

        jedisProxy.hset(keyDefs(), name, toJson(schedule));
        jedisProxy.sadd(keyAll(), name);

        if (schedule.getStartWorkflowRequest() != null
                && schedule.getStartWorkflowRequest().getName() != null) {
            jedisProxy.sadd(wfIndexKey(schedule.getStartWorkflowRequest().getName()), name);
        }

        // Sync next_run_time
        if (schedule.getNextRunTime() != null) {
            jedisProxy.hset(keyNextRun(), name, String.valueOf(schedule.getNextRunTime()));
        } else {
            jedisProxy.hdel(keyNextRun(), name);
        }
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        Monitors.recordDaoRequests(DAO_NAME, "findScheduleByName", "n/a", "n/a");
        String json = jedisProxy.hget(keyDefs(), name);
        return json == null ? null : readValue(json, WorkflowScheduleModel.class);
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules() {
        Monitors.recordDaoRequests(DAO_NAME, "getAllSchedules", "n/a", "n/a");
        Map<String, String> all = jedisProxy.hgetAll(keyDefs());
        return all.values().stream()
                .map(json -> readValue(json, WorkflowScheduleModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
        Monitors.recordDaoRequests(DAO_NAME, "findAllSchedules", "n/a", "n/a");
        Set<String> names = jedisProxy.smembers(wfIndexKey(workflowName));
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<WorkflowScheduleModel> result = new ArrayList<>();
        for (String name : names) {
            String json = jedisProxy.hget(keyDefs(), name);
            if (json != null) {
                result.add(readValue(json, WorkflowScheduleModel.class));
            }
        }
        return result;
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> names) {
        Monitors.recordDaoRequests(DAO_NAME, "findAllByNames", "n/a", "n/a");
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, WorkflowScheduleModel> result = new HashMap<>();
        for (String name : names) {
            String json = jedisProxy.hget(keyDefs(), name);
            if (json != null) {
                result.put(name, readValue(json, WorkflowScheduleModel.class));
            }
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        Monitors.recordDaoRequests(DAO_NAME, "deleteWorkflowSchedule", "n/a", "n/a");

        // NOTE: This method performs multiple independent Redis commands without transactional
        // guarantees (no MULTI/EXEC). JedisProxy does not expose a transaction API, and no
        // existing DAO in the codebase uses Redis transactions. If a crash occurs mid-operation,
        // orphaned execution records or stale index entries may remain. The scheduler's periodic
        // cleanup serves as an eventual consistency backstop.

        // Remove from workflow index
        String json = jedisProxy.hget(keyDefs(), name);
        if (json != null) {
            WorkflowScheduleModel schedule = readValue(json, WorkflowScheduleModel.class);
            if (schedule.getStartWorkflowRequest() != null
                    && schedule.getStartWorkflowRequest().getName() != null) {
                jedisProxy.srem(wfIndexKey(schedule.getStartWorkflowRequest().getName()), name);
            }
        }

        // Delete all executions for this schedule
        Set<String> execIds = jedisProxy.smembers(execSchedKey(name));
        if (execIds != null) {
            for (String execId : execIds) {
                jedisProxy.hdel(keyExec(), execId);
                jedisProxy.srem(keyPending(), execId);
            }
        }
        jedisProxy.del(execSchedKey(name));

        // Delete the schedule itself
        jedisProxy.hdel(keyDefs(), name);
        jedisProxy.srem(keyAll(), name);
        jedisProxy.hdel(keyNextRun(), name);
    }

    // =========================================================================
    // Execution records
    // =========================================================================

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel execution) {
        Monitors.recordDaoRequests(DAO_NAME, "saveExecutionRecord", "n/a", "n/a");
        String execId = execution.getExecutionId();
        jedisProxy.hset(keyExec(), execId, toJson(execution));
        jedisProxy.sadd(execSchedKey(execution.getScheduleName()), execId);

        if (execution.getState() == WorkflowScheduleExecutionModel.State.POLLED) {
            jedisProxy.sadd(keyPending(), execId);
        } else {
            jedisProxy.srem(keyPending(), execId);
        }
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "readExecutionRecord", "n/a", "n/a");
        String json = jedisProxy.hget(keyExec(), executionId);
        return json == null ? null : readValue(json, WorkflowScheduleExecutionModel.class);
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "removeExecutionRecord", "n/a", "n/a");
        String json = jedisProxy.hget(keyExec(), executionId);
        if (json != null) {
            WorkflowScheduleExecutionModel exec =
                    readValue(json, WorkflowScheduleExecutionModel.class);
            jedisProxy.srem(execSchedKey(exec.getScheduleName()), executionId);
        }
        jedisProxy.hdel(keyExec(), executionId);
        jedisProxy.srem(keyPending(), executionId);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        Monitors.recordDaoRequests(DAO_NAME, "getPendingExecutionRecordIds", "n/a", "n/a");
        Set<String> pending = jedisProxy.smembers(keyPending());
        return pending == null ? List.of() : new ArrayList<>(pending);
    }

    // =========================================================================
    // Next-run time
    // =========================================================================

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        Monitors.recordDaoRequests(DAO_NAME, "getNextRunTimeInEpoch", "n/a", "n/a");
        String val = jedisProxy.hget(keyNextRun(), scheduleName);
        if (val == null) {
            return -1L;
        }
        return Long.parseLong(val);
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        Monitors.recordDaoRequests(DAO_NAME, "setNextRunTimeInEpoch", "n/a", "n/a");
        // Store for any key: multi-cron schedules key by JSON payload strings (not schedule names)
        // so the previous hexists(SCHEDULER.DEFS) guard silently dropped those writes.
        jedisProxy.hset(keyNextRun(), scheduleName, String.valueOf(epochMillis));
    }

    // =========================================================================
    // Search
    // =========================================================================

    @Override
    public SearchResult<WorkflowScheduleModel> searchSchedules(
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        Monitors.recordDaoRequests(DAO_NAME, "searchSchedules", "n/a", "n/a");
        List<WorkflowScheduleModel> all = getAllSchedules();
        List<WorkflowScheduleModel> filtered =
                all.stream()
                        .filter(
                                s -> {
                                    if (workflowName != null
                                            && !workflowName.isEmpty()
                                            && s.getStartWorkflowRequest() != null
                                            && !workflowName.equals(
                                                    s.getStartWorkflowRequest().getName())) {
                                        return false;
                                    }
                                    if (scheduleName != null
                                            && !scheduleName.isEmpty()
                                            && !s.getName().contains(scheduleName)) {
                                        return false;
                                    }
                                    if (paused != null && s.isPaused() != paused) {
                                        return false;
                                    }
                                    return true;
                                })
                        .sorted(Comparator.comparing(WorkflowScheduleModel::getName))
                        .collect(Collectors.toList());

        long totalHits = filtered.size();
        int end = Math.min(start + size, filtered.size());
        List<WorkflowScheduleModel> page =
                start < filtered.size() ? filtered.subList(start, end) : List.of();
        return new SearchResult<>(totalHits, page);
    }
}
