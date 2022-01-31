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
package com.netflix.conductor.postgres.dao;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.postgres.config.PostgresProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class PostgresMetadataDAO extends PostgresBaseDAO implements MetadataDAO, EventHandlerDAO {

    private final ConcurrentHashMap<String, TaskDef> taskDefCache = new ConcurrentHashMap<>();
    private static final String CLASS_NAME = PostgresMetadataDAO.class.getSimpleName();

    public PostgresMetadataDAO(
            ObjectMapper objectMapper, DataSource dataSource, PostgresProperties properties) {
        super(objectMapper, dataSource);

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
        validate(taskDef);
        insertOrUpdateTaskDef(taskDef);
    }

    @Override
    public String updateTaskDef(TaskDef taskDef) {
        validate(taskDef);
        return insertOrUpdateTaskDef(taskDef);
    }

    @Override
    public TaskDef getTaskDef(String name) {
        Preconditions.checkNotNull(name, "TaskDef name cannot be null");
        TaskDef taskDef = taskDefCache.get(name);
        if (taskDef == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Cache miss: {}", name);
            }
            taskDef = getTaskDefFromDB(name);
        }

        return taskDef;
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        return getWithRetriedTransactions(this::findAllTaskDefs);
    }

    @Override
    public void removeTaskDef(String name) {
        final String DELETE_TASKDEF_QUERY = "DELETE FROM meta_task_def WHERE name = ?";

        executeWithTransaction(
                DELETE_TASKDEF_QUERY,
                q -> {
                    if (!q.addParameter(name).executeDelete()) {
                        throw new ApplicationException(
                                ApplicationException.Code.NOT_FOUND, "No such task definition");
                    }

                    taskDefCache.remove(name);
                });
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        validate(def);

        withTransaction(
                tx -> {
                    if (workflowExists(tx, def)) {
                        throw new ApplicationException(
                                ApplicationException.Code.CONFLICT,
                                "Workflow with " + def.key() + " already exists!");
                    }

                    insertOrUpdateWorkflowDef(tx, def);
                });
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        validate(def);
        withTransaction(tx -> insertOrUpdateWorkflowDef(tx, def));
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        final String GET_LATEST_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def WHERE NAME = ? AND "
                        + "version = latest_version";

        return Optional.ofNullable(
                queryWithTransaction(
                        GET_LATEST_WORKFLOW_DEF_QUERY,
                        q -> q.addParameter(name).executeAndFetchFirst(WorkflowDef.class)));
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        final String GET_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def WHERE NAME = ? AND version = ?";
        return Optional.ofNullable(
                queryWithTransaction(
                        GET_WORKFLOW_DEF_QUERY,
                        q ->
                                q.addParameter(name)
                                        .addParameter(version)
                                        .executeAndFetchFirst(WorkflowDef.class)));
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        final String DELETE_WORKFLOW_QUERY =
                "DELETE from meta_workflow_def WHERE name = ? AND version = ?";

        withTransaction(
                tx -> {
                    // remove specified workflow
                    execute(
                            tx,
                            DELETE_WORKFLOW_QUERY,
                            q -> {
                                if (!q.addParameter(name).addParameter(version).executeDelete()) {
                                    throw new ApplicationException(
                                            ApplicationException.Code.NOT_FOUND,
                                            String.format(
                                                    "No such workflow definition: %s version: %d",
                                                    name, version));
                                }
                            });
                    // reset latest version based on remaining rows for this workflow
                    Optional<Integer> maxVersion = getLatestVersion(tx, name);
                    maxVersion.ifPresent(newVersion -> updateLatestVersion(tx, name, newVersion));
                });
    }

    public List<String> findAll() {
        final String FIND_ALL_WORKFLOW_DEF_QUERY = "SELECT DISTINCT name FROM meta_workflow_def";
        return queryWithTransaction(
                FIND_ALL_WORKFLOW_DEF_QUERY, q -> q.executeAndFetch(String.class));
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        final String GET_ALL_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def ORDER BY name, version";

        return queryWithTransaction(
                GET_ALL_WORKFLOW_DEF_QUERY, q -> q.executeAndFetch(WorkflowDef.class));
    }

    public List<WorkflowDef> getAllLatest() {
        final String GET_ALL_LATEST_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def WHERE version = " + "latest_version";

        return queryWithTransaction(
                GET_ALL_LATEST_WORKFLOW_DEF_QUERY, q -> q.executeAndFetch(WorkflowDef.class));
    }

    public List<WorkflowDef> getAllVersions(String name) {
        final String GET_ALL_VERSIONS_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def WHERE name = ? " + "ORDER BY version";

        return queryWithTransaction(
                GET_ALL_VERSIONS_WORKFLOW_DEF_QUERY,
                q -> q.addParameter(name).executeAndFetch(WorkflowDef.class));
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler.getName(), "EventHandler name cannot be null");

        final String INSERT_EVENT_HANDLER_QUERY =
                "INSERT INTO meta_event_handler (name, event, active, json_data) "
                        + "VALUES (?, ?, ?, ?)";

        withTransaction(
                tx -> {
                    if (getEventHandler(tx, eventHandler.getName()) != null) {
                        throw new ApplicationException(
                                ApplicationException.Code.CONFLICT,
                                "EventHandler with name "
                                        + eventHandler.getName()
                                        + " already exists!");
                    }

                    execute(
                            tx,
                            INSERT_EVENT_HANDLER_QUERY,
                            q ->
                                    q.addParameter(eventHandler.getName())
                                            .addParameter(eventHandler.getEvent())
                                            .addParameter(eventHandler.isActive())
                                            .addJsonParameter(eventHandler)
                                            .executeUpdate());
                });
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler.getName(), "EventHandler name cannot be null");

        // @formatter:off
        final String UPDATE_EVENT_HANDLER_QUERY =
                "UPDATE meta_event_handler SET "
                        + "event = ?, active = ?, json_data = ?, "
                        + "modified_on = CURRENT_TIMESTAMP WHERE name = ?";
        // @formatter:on

        withTransaction(
                tx -> {
                    EventHandler existing = getEventHandler(tx, eventHandler.getName());
                    if (existing == null) {
                        throw new ApplicationException(
                                ApplicationException.Code.NOT_FOUND,
                                "EventHandler with name " + eventHandler.getName() + " not found!");
                    }

                    execute(
                            tx,
                            UPDATE_EVENT_HANDLER_QUERY,
                            q ->
                                    q.addParameter(eventHandler.getEvent())
                                            .addParameter(eventHandler.isActive())
                                            .addJsonParameter(eventHandler)
                                            .addParameter(eventHandler.getName())
                                            .executeUpdate());
                });
    }

    @Override
    public void removeEventHandler(String name) {
        final String DELETE_EVENT_HANDLER_QUERY = "DELETE FROM meta_event_handler WHERE name = ?";

        withTransaction(
                tx -> {
                    EventHandler existing = getEventHandler(tx, name);
                    if (existing == null) {
                        throw new ApplicationException(
                                ApplicationException.Code.NOT_FOUND,
                                "EventHandler with name " + name + " not found!");
                    }

                    execute(
                            tx,
                            DELETE_EVENT_HANDLER_QUERY,
                            q -> q.addParameter(name).executeDelete());
                });
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        final String READ_ALL_EVENT_HANDLER_QUERY = "SELECT json_data FROM meta_event_handler";
        return queryWithTransaction(
                READ_ALL_EVENT_HANDLER_QUERY, q -> q.executeAndFetch(EventHandler.class));
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        final String READ_ALL_EVENT_HANDLER_BY_EVENT_QUERY =
                "SELECT json_data FROM meta_event_handler WHERE event = ?";
        return queryWithTransaction(
                READ_ALL_EVENT_HANDLER_BY_EVENT_QUERY,
                q -> {
                    q.addParameter(event);
                    return q.executeAndFetch(
                            rs -> {
                                List<EventHandler> handlers = new ArrayList<>();
                                while (rs.next()) {
                                    EventHandler h = readValue(rs.getString(1), EventHandler.class);
                                    if (!activeOnly || h.isActive()) {
                                        handlers.add(h);
                                    }
                                }

                                return handlers;
                            });
                });
    }

    /**
     * Use {@link Preconditions} to check for required {@link TaskDef} fields, throwing a Runtime
     * exception if validations fail.
     *
     * @param taskDef The {@code TaskDef} to check.
     */
    private void validate(TaskDef taskDef) {
        Preconditions.checkNotNull(taskDef, "TaskDef object cannot be null");
        Preconditions.checkNotNull(taskDef.getName(), "TaskDef name cannot be null");
    }

    /**
     * Use {@link Preconditions} to check for required {@link WorkflowDef} fields, throwing a
     * Runtime exception if validations fail.
     *
     * @param def The {@code WorkflowDef} to check.
     */
    private void validate(WorkflowDef def) {
        Preconditions.checkNotNull(def, "WorkflowDef object cannot be null");
        Preconditions.checkNotNull(def.getName(), "WorkflowDef name cannot be null");
    }

    /**
     * Retrieve a {@link EventHandler} by {@literal name}.
     *
     * @param connection The {@link Connection} to use for queries.
     * @param name The {@code EventHandler} name to look for.
     * @return {@literal null} if nothing is found, otherwise the {@code EventHandler}.
     */
    private EventHandler getEventHandler(Connection connection, String name) {
        final String READ_ONE_EVENT_HANDLER_QUERY =
                "SELECT json_data FROM meta_event_handler WHERE name = ?";

        return query(
                connection,
                READ_ONE_EVENT_HANDLER_QUERY,
                q -> q.addParameter(name).executeAndFetchFirst(EventHandler.class));
    }

    /**
     * Check if a {@link WorkflowDef} with the same {@literal name} and {@literal version} already
     * exist.
     *
     * @param connection The {@link Connection} to use for queries.
     * @param def The {@code WorkflowDef} to check for.
     * @return {@literal true} if a {@code WorkflowDef} already exists with the same values.
     */
    private Boolean workflowExists(Connection connection, WorkflowDef def) {
        final String CHECK_WORKFLOW_DEF_EXISTS_QUERY =
                "SELECT COUNT(*) FROM meta_workflow_def WHERE name = ? AND " + "version = ?";

        return query(
                connection,
                CHECK_WORKFLOW_DEF_EXISTS_QUERY,
                q -> q.addParameter(def.getName()).addParameter(def.getVersion()).exists());
    }

    /**
     * Return the latest version that exists for the provided {@code name}.
     *
     * @param tx The {@link Connection} to use for queries.
     * @param name The {@code name} to check for.
     * @return {@code Optional.empty()} if no versions exist, otherwise the max {@link
     *     WorkflowDef#getVersion} found.
     */
    private Optional<Integer> getLatestVersion(Connection tx, String name) {
        final String GET_LATEST_WORKFLOW_DEF_VERSION =
                "SELECT max(version) AS version FROM meta_workflow_def WHERE " + "name = ?";

        Integer val =
                query(
                        tx,
                        GET_LATEST_WORKFLOW_DEF_VERSION,
                        q -> {
                            q.addParameter(name);
                            return q.executeAndFetch(
                                    rs -> {
                                        if (!rs.next()) {
                                            return null;
                                        }

                                        return rs.getInt(1);
                                    });
                        });

        return Optional.ofNullable(val);
    }

    /**
     * Update the latest version for the workflow with name {@code WorkflowDef} to the version
     * provided in {@literal version}.
     *
     * @param tx The {@link Connection} to use for queries.
     * @param name Workflow def name to update
     * @param version The new latest {@code version} value.
     */
    private void updateLatestVersion(Connection tx, String name, int version) {
        final String UPDATE_WORKFLOW_DEF_LATEST_VERSION_QUERY =
                "UPDATE meta_workflow_def SET latest_version = ? " + "WHERE name = ?";

        execute(
                tx,
                UPDATE_WORKFLOW_DEF_LATEST_VERSION_QUERY,
                q -> q.addParameter(version).addParameter(name).executeUpdate());
    }

    private void insertOrUpdateWorkflowDef(Connection tx, WorkflowDef def) {
        final String INSERT_WORKFLOW_DEF_QUERY =
                "INSERT INTO meta_workflow_def (name, version, json_data) VALUES (?," + " ?, ?)";

        Optional<Integer> version = getLatestVersion(tx, def.getName());
        if (!workflowExists(tx, def)) {
            execute(
                    tx,
                    INSERT_WORKFLOW_DEF_QUERY,
                    q ->
                            q.addParameter(def.getName())
                                    .addParameter(def.getVersion())
                                    .addJsonParameter(def)
                                    .executeUpdate());
        } else {
            // @formatter:off
            final String UPDATE_WORKFLOW_DEF_QUERY =
                    "UPDATE meta_workflow_def "
                            + "SET json_data = ?, modified_on = CURRENT_TIMESTAMP "
                            + "WHERE name = ? AND version = ?";
            // @formatter:on

            execute(
                    tx,
                    UPDATE_WORKFLOW_DEF_QUERY,
                    q ->
                            q.addJsonParameter(def)
                                    .addParameter(def.getName())
                                    .addParameter(def.getVersion())
                                    .executeUpdate());
        }
        int maxVersion = def.getVersion();
        if (version.isPresent() && version.get() > def.getVersion()) {
            maxVersion = version.get();
        }

        updateLatestVersion(tx, def.getName(), maxVersion);
    }

    /**
     * Query persistence for all defined {@link TaskDef} data, and cache it in {@link
     * #taskDefCache}.
     */
    private void refreshTaskDefs() {
        try {
            withTransaction(
                    tx -> {
                        Map<String, TaskDef> map = new HashMap<>();
                        findAllTaskDefs(tx).forEach(taskDef -> map.put(taskDef.getName(), taskDef));

                        synchronized (taskDefCache) {
                            taskDefCache.clear();
                            taskDefCache.putAll(map);
                        }

                        if (logger.isTraceEnabled()) {
                            logger.trace("Refreshed {} TaskDefs", taskDefCache.size());
                        }
                    });
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshTaskDefs");
            logger.error("refresh TaskDefs failed ", e);
        }
    }

    /**
     * Query persistence for all defined {@link TaskDef} data.
     *
     * @param tx The {@link Connection} to use for queries.
     * @return A new {@code List<TaskDef>} with all the {@code TaskDef} data that was retrieved.
     */
    private List<TaskDef> findAllTaskDefs(Connection tx) {
        final String READ_ALL_TASKDEF_QUERY = "SELECT json_data FROM meta_task_def";

        return query(tx, READ_ALL_TASKDEF_QUERY, q -> q.executeAndFetch(TaskDef.class));
    }

    /**
     * Explicitly retrieves a {@link TaskDef} from persistence, avoiding {@link #taskDefCache}.
     *
     * @param name The name of the {@code TaskDef} to query for.
     * @return {@literal null} if nothing is found, otherwise the {@code TaskDef}.
     */
    private TaskDef getTaskDefFromDB(String name) {
        final String READ_ONE_TASKDEF_QUERY = "SELECT json_data FROM meta_task_def WHERE name = ?";

        return queryWithTransaction(
                READ_ONE_TASKDEF_QUERY,
                q -> q.addParameter(name).executeAndFetchFirst(TaskDef.class));
    }

    private String insertOrUpdateTaskDef(TaskDef taskDef) {
        final String UPDATE_TASKDEF_QUERY =
                "UPDATE meta_task_def SET json_data = ?, modified_on = CURRENT_TIMESTAMP WHERE name = ?";

        final String INSERT_TASKDEF_QUERY =
                "INSERT INTO meta_task_def (name, json_data) VALUES (?, ?)";

        return getWithRetriedTransactions(
                tx -> {
                    execute(
                            tx,
                            UPDATE_TASKDEF_QUERY,
                            update -> {
                                int result =
                                        update.addJsonParameter(taskDef)
                                                .addParameter(taskDef.getName())
                                                .executeUpdate();
                                if (result == 0) {
                                    execute(
                                            tx,
                                            INSERT_TASKDEF_QUERY,
                                            insert ->
                                                    insert.addParameter(taskDef.getName())
                                                            .addJsonParameter(taskDef)
                                                            .executeUpdate());
                                }
                            });

                    taskDefCache.put(taskDef.getName(), taskDef);
                    return taskDef.getName();
                });
    }
}
