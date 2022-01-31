/*
 * Copyright 2022 Netflix, Inc.
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.exception.ApplicationException.Code;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

@Component
@Conditional(AnyRedisCondition.class)
public class RedisMetadataDAO extends BaseDynoDAO implements MetadataDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMetadataDAO.class);

    // Keys Families
    private static final String ALL_TASK_DEFS = "TASK_DEFS";
    private static final String WORKFLOW_DEF_NAMES = "WORKFLOW_DEF_NAMES";
    private static final String WORKFLOW_DEF = "WORKFLOW_DEF";
    private static final String LATEST = "latest";
    private static final String className = RedisMetadataDAO.class.getSimpleName();
    private Map<String, TaskDef> taskDefCache = new HashMap<>();

    public RedisMetadataDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
        refreshTaskDefs();
        long cacheRefreshTime = properties.getTaskDefCacheRefreshInterval().getSeconds();
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(
                        this::refreshTaskDefs,
                        cacheRefreshTime,
                        cacheRefreshTime,
                        TimeUnit.SECONDS);
    }

    @Override
    public void createTaskDef(TaskDef taskDef) {
        insertOrUpdateTaskDef(taskDef);
    }

    @Override
    public String updateTaskDef(TaskDef taskDef) {
        return insertOrUpdateTaskDef(taskDef);
    }

    private String insertOrUpdateTaskDef(TaskDef taskDef) {
        // Store all task def in under one key
        String payload = toJson(taskDef);
        jedisProxy.hset(nsKey(ALL_TASK_DEFS), taskDef.getName(), payload);
        recordRedisDaoRequests("storeTaskDef");
        recordRedisDaoPayloadSize("storeTaskDef", payload.length(), taskDef.getName(), "n/a");
        refreshTaskDefs();
        return taskDef.getName();
    }

    private void refreshTaskDefs() {
        try {
            Map<String, TaskDef> map = new HashMap<>();
            getAllTaskDefs().forEach(taskDef -> map.put(taskDef.getName(), taskDef));
            this.taskDefCache = map;
            LOGGER.debug("Refreshed task defs " + this.taskDefCache.size());
        } catch (Exception e) {
            Monitors.error(className, "refreshTaskDefs");
            LOGGER.error("refresh TaskDefs failed ", e);
        }
    }

    @Override
    public TaskDef getTaskDef(String name) {
        return Optional.ofNullable(taskDefCache.get(name)).orElseGet(() -> getTaskDefFromDB(name));
    }

    private TaskDef getTaskDefFromDB(String name) {
        Preconditions.checkNotNull(name, "TaskDef name cannot be null");

        TaskDef taskDef = null;
        String taskDefJsonStr = jedisProxy.hget(nsKey(ALL_TASK_DEFS), name);
        if (taskDefJsonStr != null) {
            taskDef = readValue(taskDefJsonStr, TaskDef.class);
            recordRedisDaoRequests("getTaskDef");
            recordRedisDaoPayloadSize(
                    "getTaskDef", taskDefJsonStr.length(), taskDef.getName(), "n/a");
        }
        return taskDef;
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        List<TaskDef> allTaskDefs = new LinkedList<>();

        recordRedisDaoRequests("getAllTaskDefs");
        Map<String, String> taskDefs = jedisProxy.hgetAll(nsKey(ALL_TASK_DEFS));
        int size = 0;
        if (taskDefs.size() > 0) {
            for (String taskDefJsonStr : taskDefs.values()) {
                if (taskDefJsonStr != null) {
                    allTaskDefs.add(readValue(taskDefJsonStr, TaskDef.class));
                    size += taskDefJsonStr.length();
                }
            }
            recordRedisDaoPayloadSize("getAllTaskDefs", size, "n/a", "n/a");
        }

        return allTaskDefs;
    }

    @Override
    public void removeTaskDef(String name) {
        Preconditions.checkNotNull(name, "TaskDef name cannot be null");
        Long result = jedisProxy.hdel(nsKey(ALL_TASK_DEFS), name);
        if (!result.equals(1L)) {
            throw new ApplicationException(
                    Code.NOT_FOUND, "Cannot remove the task - no such task definition");
        }
        recordRedisDaoRequests("removeTaskDef");
        refreshTaskDefs();
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        if (jedisProxy.hexists(
                nsKey(WORKFLOW_DEF, def.getName()), String.valueOf(def.getVersion()))) {
            throw new ApplicationException(
                    Code.CONFLICT, "Workflow with " + def.key() + " already exists!");
        }
        _createOrUpdate(def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        _createOrUpdate(def);
    }

    @Override
    /*
     * @param name Name of the workflow definition
     * @return     Latest version of workflow definition
     * @see        WorkflowDef
     */
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        Preconditions.checkNotNull(name, "WorkflowDef name cannot be null");
        WorkflowDef workflowDef = null;

        Optional<Integer> optionalMaxVersion = getWorkflowMaxVersion(name);

        if (optionalMaxVersion.isPresent()) {
            String latestdata =
                    jedisProxy.hget(nsKey(WORKFLOW_DEF, name), optionalMaxVersion.get().toString());
            if (latestdata != null) {
                workflowDef = readValue(latestdata, WorkflowDef.class);
            }
        }

        return Optional.ofNullable(workflowDef);
    }

    private Optional<Integer> getWorkflowMaxVersion(String workflowName) {
        return jedisProxy.hkeys(nsKey(WORKFLOW_DEF, workflowName)).stream()
                .filter(key -> !key.equals(LATEST))
                .map(Integer::valueOf)
                .max(Comparator.naturalOrder());
    }

    public List<WorkflowDef> getAllVersions(String name) {
        Preconditions.checkNotNull(name, "WorkflowDef name cannot be null");
        List<WorkflowDef> workflows = new LinkedList<>();

        recordRedisDaoRequests("getAllWorkflowDefsByName");
        Map<String, String> workflowDefs = jedisProxy.hgetAll(nsKey(WORKFLOW_DEF, name));
        int size = 0;
        for (String key : workflowDefs.keySet()) {
            if (key.equals(LATEST)) {
                continue;
            }
            String workflowDef = workflowDefs.get(key);
            workflows.add(readValue(workflowDef, WorkflowDef.class));
            size += workflowDef.length();
        }
        recordRedisDaoPayloadSize("getAllWorkflowDefsByName", size, "n/a", name);

        return workflows;
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        Preconditions.checkNotNull(name, "WorkflowDef name cannot be null");
        WorkflowDef def = null;

        recordRedisDaoRequests("getWorkflowDef");
        String workflowDefJsonString =
                jedisProxy.hget(nsKey(WORKFLOW_DEF, name), String.valueOf(version));
        if (workflowDefJsonString != null) {
            def = readValue(workflowDefJsonString, WorkflowDef.class);
            recordRedisDaoPayloadSize(
                    "getWorkflowDef", workflowDefJsonString.length(), "n/a", name);
        }
        return Optional.ofNullable(def);
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        Preconditions.checkArgument(
                StringUtils.isNotBlank(name), "WorkflowDef name cannot be null");
        Preconditions.checkNotNull(version, "Input version cannot be null");
        Long result = jedisProxy.hdel(nsKey(WORKFLOW_DEF, name), String.valueOf(version));
        if (!result.equals(1L)) {
            throw new ApplicationException(
                    Code.NOT_FOUND,
                    String.format(
                            "Cannot remove the workflow - no such workflow"
                                    + " definition: %s version: %d",
                            name, version));
        }

        // check if there are any more versions remaining if not delete the
        // workflow name
        Optional<Integer> optionMaxVersion = getWorkflowMaxVersion(name);

        // delete workflow name
        if (!optionMaxVersion.isPresent()) {
            jedisProxy.srem(nsKey(WORKFLOW_DEF_NAMES), name);
        }

        recordRedisDaoRequests("removeWorkflowDef");
    }

    public List<String> findAll() {
        Set<String> wfNames = jedisProxy.smembers(nsKey(WORKFLOW_DEF_NAMES));
        return new ArrayList<>(wfNames);
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        List<WorkflowDef> workflows = new LinkedList<>();

        // Get all from WORKFLOW_DEF_NAMES
        recordRedisDaoRequests("getAllWorkflowDefs");
        Set<String> wfNames = jedisProxy.smembers(nsKey(WORKFLOW_DEF_NAMES));
        int size = 0;
        for (String wfName : wfNames) {
            Map<String, String> workflowDefs = jedisProxy.hgetAll(nsKey(WORKFLOW_DEF, wfName));
            for (String key : workflowDefs.keySet()) {
                if (key.equals(LATEST)) {
                    continue;
                }
                String workflowDef = workflowDefs.get(key);
                workflows.add(readValue(workflowDef, WorkflowDef.class));
                size += workflowDef.length();
            }
        }
        recordRedisDaoPayloadSize("getAllWorkflowDefs", size, "n/a", "n/a");
        return workflows;
    }

    private void _createOrUpdate(WorkflowDef workflowDef) {
        // First set the workflow def
        jedisProxy.hset(
                nsKey(WORKFLOW_DEF, workflowDef.getName()),
                String.valueOf(workflowDef.getVersion()),
                toJson(workflowDef));

        jedisProxy.sadd(nsKey(WORKFLOW_DEF_NAMES), workflowDef.getName());
        recordRedisDaoRequests("storeWorkflowDef", "n/a", workflowDef.getName());
    }
}
