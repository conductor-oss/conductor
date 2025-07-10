/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao.metadata;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.sqlite.dao.SqliteBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class SqliteWorkflowMetadataDAO extends SqliteBaseDAO {

    public SqliteWorkflowMetadataDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    public void createWorkflowDef(WorkflowDef def) {
        validate(def);

        withTransaction(
                tx -> {
                    if (workflowExists(tx, def)) {
                        throw new ConflictException(
                                "Workflow with " + def.key() + " already exists!");
                    }

                    insertOrUpdateWorkflowDef(tx, def);
                });
    }

    public void updateWorkflowDef(WorkflowDef def) {
        validate(def);
        withTransaction(tx -> insertOrUpdateWorkflowDef(tx, def));
    }

    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        final String GET_LATEST_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def WHERE NAME = ? AND "
                        + "version = latest_version";

        return Optional.ofNullable(
                queryWithTransaction(
                        GET_LATEST_WORKFLOW_DEF_QUERY,
                        q -> q.addParameter(name).executeAndFetchFirst(WorkflowDef.class)));
    }

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
                                    throw new NotFoundException(
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

    public List<WorkflowDef> getAllWorkflowDefs() {
        final String GET_ALL_WORKFLOW_DEF_QUERY =
                "SELECT json_data FROM meta_workflow_def ORDER BY name, version";

        return queryWithTransaction(
                GET_ALL_WORKFLOW_DEF_QUERY, q -> q.executeAndFetch(WorkflowDef.class));
    }

    public List<WorkflowDef> getAllWorkflowDefsLatestVersions() {
        final String GET_ALL_WORKFLOW_DEF_LATEST_VERSIONS_QUERY =
                "SELECT json_data FROM meta_workflow_def wd WHERE wd.version = (SELECT MAX(version) FROM meta_workflow_def wd2 WHERE wd2.name = wd.name)";
        return queryWithTransaction(
                GET_ALL_WORKFLOW_DEF_LATEST_VERSIONS_QUERY,
                q -> q.executeAndFetch(WorkflowDef.class));
    }

    public List<String> findAll() {
        final String FIND_ALL_WORKFLOW_DEF_QUERY = "SELECT DISTINCT name FROM meta_workflow_def";
        return queryWithTransaction(
                FIND_ALL_WORKFLOW_DEF_QUERY, q -> q.executeAndFetch(String.class));
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

    private Boolean workflowExists(Connection connection, WorkflowDef def) {
        final String CHECK_WORKFLOW_DEF_EXISTS_QUERY =
                "SELECT COUNT(*) FROM meta_workflow_def WHERE name = ? AND " + "version = ?";

        return query(
                connection,
                CHECK_WORKFLOW_DEF_EXISTS_QUERY,
                q -> q.addParameter(def.getName()).addParameter(def.getVersion()).exists());
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

    private void updateLatestVersion(Connection tx, String name, int version) {
        final String UPDATE_WORKFLOW_DEF_LATEST_VERSION_QUERY =
                "UPDATE meta_workflow_def SET latest_version = ? " + "WHERE name = ?";

        execute(
                tx,
                UPDATE_WORKFLOW_DEF_LATEST_VERSION_QUERY,
                q -> q.addParameter(version).addParameter(name).executeUpdate());
    }

    private void validate(WorkflowDef def) {
        Preconditions.checkNotNull(def, "WorkflowDef object cannot be null");
        Preconditions.checkNotNull(def.getName(), "WorkflowDef name cannot be null");
    }
}
