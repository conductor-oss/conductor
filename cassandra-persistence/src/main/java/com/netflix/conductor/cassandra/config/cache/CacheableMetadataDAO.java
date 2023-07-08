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
package com.netflix.conductor.cassandra.config.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.cassandra.dao.CassandraMetadataDAO;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;

import static com.netflix.conductor.cassandra.config.cache.CachingConfig.TASK_DEF_CACHE;

@Trace
public class CacheableMetadataDAO implements MetadataDAO {

    private static final String CLASS_NAME = CacheableMetadataDAO.class.getSimpleName();

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheableMetadataDAO.class);

    private final CassandraMetadataDAO cassandraMetadataDAO;
    private final CassandraProperties properties;

    private final CacheManager cacheManager;

    public CacheableMetadataDAO(
            CassandraMetadataDAO cassandraMetadataDAO,
            CassandraProperties properties,
            CacheManager cacheManager) {
        this.cassandraMetadataDAO = cassandraMetadataDAO;
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void scheduleCacheRefresh() {
        long cacheRefreshTime = properties.getTaskDefCacheRefreshInterval().getSeconds();
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(
                        this::refreshTaskDefsCache, 0, cacheRefreshTime, TimeUnit.SECONDS);
        LOGGER.info(
                "Scheduled cache refresh for Task Definitions, every {} seconds", cacheRefreshTime);
    }

    @Override
    @CachePut(value = TASK_DEF_CACHE, key = "#taskDef.name")
    public TaskDef createTaskDef(TaskDef taskDef) {
        cassandraMetadataDAO.createTaskDef(taskDef);
        return taskDef;
    }

    @Override
    @CachePut(value = TASK_DEF_CACHE, key = "#taskDef.name")
    public TaskDef updateTaskDef(TaskDef taskDef) {
        return cassandraMetadataDAO.updateTaskDef(taskDef);
    }

    @Override
    @Cacheable(TASK_DEF_CACHE)
    public TaskDef getTaskDef(String name) {
        return cassandraMetadataDAO.getTaskDef(name);
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        Object nativeCache = cacheManager.getCache(TASK_DEF_CACHE).getNativeCache();
        if (nativeCache != null && nativeCache instanceof ConcurrentHashMap) {
            ConcurrentHashMap cacheMap = (ConcurrentHashMap) nativeCache;
            if (!cacheMap.isEmpty()) {
                List<TaskDef> taskDefs = new ArrayList<>();
                cacheMap.values().stream()
                        .filter(element -> element != null && element instanceof TaskDef)
                        .forEach(element -> taskDefs.add((TaskDef) element));
                return taskDefs;
            }
        }

        return refreshTaskDefsCache();
    }

    @Override
    @CacheEvict(TASK_DEF_CACHE)
    public void removeTaskDef(String name) {
        cassandraMetadataDAO.removeTaskDef(name);
    }

    @Override
    public void createWorkflowDef(WorkflowDef workflowDef) {
        cassandraMetadataDAO.createWorkflowDef(workflowDef);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef workflowDef) {
        cassandraMetadataDAO.updateWorkflowDef(workflowDef);
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        return cassandraMetadataDAO.getLatestWorkflowDef(name);
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        return cassandraMetadataDAO.getWorkflowDef(name, version);
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        cassandraMetadataDAO.removeWorkflowDef(name, version);
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        return cassandraMetadataDAO.getAllWorkflowDefs();
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefsLatestVersions() {
        return cassandraMetadataDAO.getAllWorkflowDefsLatestVersions();
    }

    private List<TaskDef> refreshTaskDefsCache() {
        try {
            Cache taskDefsCache = cacheManager.getCache(TASK_DEF_CACHE);
            taskDefsCache.clear();
            List<TaskDef> taskDefs = cassandraMetadataDAO.getAllTaskDefs();
            taskDefs.forEach(taskDef -> taskDefsCache.put(taskDef.getName(), taskDef));
            LOGGER.debug("Refreshed task defs, total num: " + taskDefs.size());
            return taskDefs;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshTaskDefs");
            LOGGER.error("refresh TaskDefs failed ", e);
        }
        return Collections.emptyList();
    }
}
