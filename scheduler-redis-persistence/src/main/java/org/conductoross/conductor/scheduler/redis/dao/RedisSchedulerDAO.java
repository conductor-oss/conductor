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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis implementation of {@link SchedulerDAO} using {@link JedisProxy}.
 *
 * <p>All keys use the prefix {@code conductor_scheduler:} (underscore separator). Data structures:
 *
 * <ul>
 *   <li>HASH {@code conductor_scheduler:schedules} — all schedule JSON blobs
 *   <li>SET {@code conductor_scheduler:by_workflow:{wfName}} — schedule names per workflow
 *   <li>ZSET {@code conductor_scheduler:next_run} — schedule names scored by next-run epoch ms
 *   <li>STRING {@code conductor_scheduler:execution:{id}} — individual execution JSON blobs
 *   <li>ZSET {@code conductor_scheduler:exec_by_sched:{name}} — execution IDs scored by time
 *   <li>SET {@code conductor_scheduler:pending_execs} — execution IDs in POLLED state
 * </ul>
 */
public class RedisSchedulerDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(RedisSchedulerDAO.class);

    static final String KEY_SCHEDULES = "conductor_scheduler:schedules";
    static final String KEY_NEXT_RUN = "conductor_scheduler:next_run";
    static final String KEY_PENDING_EXECS = "conductor_scheduler:pending_execs";

    private static String keyByWorkflow(String workflowName) {
        return "conductor_scheduler:by_workflow:" + workflowName;
    }

    private static String keyExecution(String executionId) {
        return "conductor_scheduler:execution:" + executionId;
    }

    private static String keyExecBySched(String scheduleName) {
        return "conductor_scheduler:exec_by_sched:" + scheduleName;
    }

    private final JedisProxy jedis;
    private final ObjectMapper objectMapper;

    public RedisSchedulerDAO(JedisProxy jedisProxy, ObjectMapper objectMapper) {
        this.jedis = jedisProxy;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Schedule CRUD
    // -------------------------------------------------------------------------

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        // If a schedule already exists, update by_workflow membership if workflow changed
        String existingJson = jedis.hget(KEY_SCHEDULES, schedule.getName());
        if (existingJson != null) {
            WorkflowSchedule existing = fromJson(existingJson, WorkflowSchedule.class);
            String oldWf =
                    existing.getStartWorkflowRequest() != null
                            ? existing.getStartWorkflowRequest().getName()
                            : null;
            String newWf =
                    schedule.getStartWorkflowRequest() != null
                            ? schedule.getStartWorkflowRequest().getName()
                            : null;
            if (oldWf != null && !oldWf.equals(newWf)) {
                jedis.srem(keyByWorkflow(oldWf), schedule.getName());
            }
        }
        jedis.hset(KEY_SCHEDULES, schedule.getName(), toJson(schedule));
        if (schedule.getStartWorkflowRequest() != null
                && schedule.getStartWorkflowRequest().getName() != null) {
            jedis.sadd(
                    keyByWorkflow(schedule.getStartWorkflowRequest().getName()),
                    schedule.getName());
        }
        long score = schedule.getNextRunTime() != null ? schedule.getNextRunTime() : -1L;
        jedis.zadd(KEY_NEXT_RUN, (double) score, schedule.getName());
    }

    @Override
    public WorkflowSchedule findScheduleByName(String name) {
        String json = jedis.hget(KEY_SCHEDULES, name);
        return json != null ? fromJson(json, WorkflowSchedule.class) : null;
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules() {
        List<String> jsons = jedis.hvals(KEY_SCHEDULES);
        List<WorkflowSchedule> result = new ArrayList<>();
        for (String json : jsons) {
            result.add(fromJson(json, WorkflowSchedule.class));
        }
        return result;
    }

    @Override
    public List<WorkflowSchedule> findAllSchedules(String workflowName) {
        Set<String> names = jedis.smembers(keyByWorkflow(workflowName));
        List<WorkflowSchedule> result = new ArrayList<>();
        for (String name : names) {
            String json = jedis.hget(KEY_SCHEDULES, name);
            if (json != null) {
                result.add(fromJson(json, WorkflowSchedule.class));
            }
        }
        return result;
    }

    @Override
    public Map<String, WorkflowSchedule> findAllByNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, WorkflowSchedule> result = new HashMap<>();
        for (String name : names) {
            String json = jedis.hget(KEY_SCHEDULES, name);
            if (json != null) {
                result.put(name, fromJson(json, WorkflowSchedule.class));
            }
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        // Read JSON first (needed for by_workflow cleanup) before removing from HASH
        String json = jedis.hget(KEY_SCHEDULES, name);
        // Remove from HASH first — makes the schedule invisible to queries immediately,
        // bounding the inconsistency window if the process crashes mid-cleanup.
        jedis.hdel(KEY_SCHEDULES, name);
        jedis.zrem(KEY_NEXT_RUN, name);
        if (json != null) {
            WorkflowSchedule schedule = fromJson(json, WorkflowSchedule.class);
            if (schedule.getStartWorkflowRequest() != null
                    && schedule.getStartWorkflowRequest().getName() != null) {
                jedis.srem(keyByWorkflow(schedule.getStartWorkflowRequest().getName()), name);
            }
        }
        // Remove all execution records for this schedule
        List<String> execIds =
                jedis.zrevrangeByScore(keyExecBySched(name), "+inf", "-inf", 0, Integer.MAX_VALUE);
        for (String execId : execIds) {
            jedis.del(keyExecution(execId));
            jedis.srem(KEY_PENDING_EXECS, execId);
        }
        jedis.del(keyExecBySched(name));
    }

    // -------------------------------------------------------------------------
    // Execution tracking
    // -------------------------------------------------------------------------

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecution execution) {
        jedis.set(keyExecution(execution.getExecutionId()), toJson(execution));
        long score = execution.getExecutionTime() != null ? execution.getExecutionTime() : 0L;
        jedis.zadd(
                keyExecBySched(execution.getScheduleName()),
                (double) score,
                execution.getExecutionId());
        if (execution.getState() == WorkflowScheduleExecution.ExecutionState.POLLED) {
            jedis.sadd(KEY_PENDING_EXECS, execution.getExecutionId());
        } else {
            jedis.srem(KEY_PENDING_EXECS, execution.getExecutionId());
        }
    }

    @Override
    public WorkflowScheduleExecution readExecutionRecord(String executionId) {
        String json = jedis.get(keyExecution(executionId));
        return json != null ? fromJson(json, WorkflowScheduleExecution.class) : null;
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        // Read first to get scheduleName for ZSET cleanup
        String json = jedis.get(keyExecution(executionId));
        if (json != null) {
            WorkflowScheduleExecution ex = fromJson(json, WorkflowScheduleExecution.class);
            if (ex.getScheduleName() != null) {
                jedis.zrem(keyExecBySched(ex.getScheduleName()), executionId);
            }
        }
        jedis.del(keyExecution(executionId));
        jedis.srem(KEY_PENDING_EXECS, executionId);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        Set<String> ids = jedis.smembers(KEY_PENDING_EXECS);
        return new ArrayList<>(ids);
    }

    @Override
    public List<WorkflowScheduleExecution> getPendingExecutionRecords() {
        Set<String> ids = jedis.smembers(KEY_PENDING_EXECS);
        List<WorkflowScheduleExecution> result = new ArrayList<>();
        for (String id : ids) {
            String json = jedis.get(keyExecution(id));
            if (json != null) {
                result.add(fromJson(json, WorkflowScheduleExecution.class));
            }
        }
        return result;
    }

    @Override
    public List<WorkflowScheduleExecution> getExecutionRecords(String scheduleName, int limit) {
        List<String> execIds =
                jedis.zrevrangeByScore(keyExecBySched(scheduleName), "+inf", "-inf", 0, limit);
        List<WorkflowScheduleExecution> result = new ArrayList<>();
        for (String id : execIds) {
            String json = jedis.get(keyExecution(id));
            if (json != null) {
                result.add(fromJson(json, WorkflowScheduleExecution.class));
            }
        }
        return result;
    }

    @Override
    public List<WorkflowScheduleExecution> getAllExecutionRecords(int limit) {
        List<WorkflowSchedule> schedules = getAllSchedules();
        List<WorkflowScheduleExecution> all = new ArrayList<>();
        for (WorkflowSchedule schedule : schedules) {
            all.addAll(getExecutionRecords(schedule.getName(), limit));
        }
        all.sort(
                (a, b) -> {
                    long ta = a.getExecutionTime() != null ? a.getExecutionTime() : 0L;
                    long tb = b.getExecutionTime() != null ? b.getExecutionTime() : 0L;
                    return Long.compare(tb, ta);
                });
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    // -------------------------------------------------------------------------
    // Next-run time management
    // -------------------------------------------------------------------------

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        Double score = jedis.zscore(KEY_NEXT_RUN, scheduleName);
        return score != null ? score.longValue() : -1L;
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        // Guard: if the schedule does not exist, do nothing (contract: non-existent schedule
        // must still return -1L from getNextRunTimeInEpoch after this call).
        if (!jedis.hexists(KEY_SCHEDULES, scheduleName)) {
            return;
        }
        jedis.zadd(KEY_NEXT_RUN, (double) epochMillis, scheduleName);
        // Keep the JSON blob consistent with the ZSET score
        String json = jedis.hget(KEY_SCHEDULES, scheduleName);
        if (json != null) {
            WorkflowSchedule schedule = fromJson(json, WorkflowSchedule.class);
            schedule.setNextRunTime(epochMillis);
            jedis.hset(KEY_SCHEDULES, scheduleName, toJson(schedule));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new NonTransientException("Failed to deserialize from JSON", e);
        }
    }
}
