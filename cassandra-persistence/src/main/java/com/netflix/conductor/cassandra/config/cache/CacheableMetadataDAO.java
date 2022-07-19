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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.cassandra.dao.CassandraMetadataDAO;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;

@Trace
public class CacheableMetadataDAO implements MetadataDAO {

    private static final String CLASS_NAME = CacheableMetadataDAO.class.getSimpleName();

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheableMetadataDAO.class);

    private final CassandraMetadataDAO cassandraMetadataDAO;
    private final CassandraProperties properties;

    private final Map<String, TaskDef> taskDefCache = new ConcurrentHashMap<>();

    public CacheableMetadataDAO(
            CassandraMetadataDAO cassandraMetadataDAO, CassandraProperties properties) {
        this.cassandraMetadataDAO = cassandraMetadataDAO;
        this.properties = properties;
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
    public void createTaskDef(TaskDef taskDef) {
        try {
            cassandraMetadataDAO.createTaskDef(taskDef);
        } finally {
            evictTaskDef(taskDef);
        }
    }

    @Override
    public String updateTaskDef(TaskDef taskDef) {
        try {
            return cassandraMetadataDAO.updateTaskDef(taskDef);
        } finally {
            evictTaskDef(taskDef);
        }
    }

    @Override
    public TaskDef getTaskDef(String name) {
        return Optional.ofNullable(taskDefCache.get(name))
                .orElseGet(() -> cassandraMetadataDAO.getTaskDef(name));
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        if (taskDefCache.size() == 0) {
            refreshTaskDefsCache();
        }
        return new ArrayList<>(taskDefCache.values());
    }

    @Override
    public void removeTaskDef(String name) {
        try {
            cassandraMetadataDAO.removeTaskDef(name);
        } finally {
            taskDefCache.remove(name);
        }
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

    private void refreshTaskDefsCache() {
        try {
            Map<String, TaskDef> map = new HashMap<>();
            cassandraMetadataDAO
                    .getAllTaskDefs()
                    .forEach(taskDef -> map.put(taskDef.getName(), taskDef));
            this.taskDefCache.putAll(map);
            LOGGER.debug("Refreshed task defs, total num: " + this.taskDefCache.size());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshTaskDefs");
            LOGGER.error("refresh TaskDefs failed ", e);
        }
    }

    private void evictTaskDef(TaskDef taskDef) {
        if (taskDef != null && taskDef.getName() != null) {
            taskDefCache.remove(taskDef.getName());
        }
    }
}
