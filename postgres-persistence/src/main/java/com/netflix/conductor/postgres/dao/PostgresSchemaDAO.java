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
package com.netflix.conductor.postgres.dao;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.SchemaDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class PostgresSchemaDAO extends PostgresBaseDAO implements SchemaDAO {

    public PostgresSchemaDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void save(String orgId, SchemaDef schemaDef) {
        validate(orgId, schemaDef);

        final String UPDATE_SCHEMA_QUERY =
                "UPDATE meta_schema_def SET json_data = ?, modified_on = CURRENT_TIMESTAMP "
                        + "WHERE org_id = ? AND name = ? AND version = ?";
        final String INSERT_SCHEMA_QUERY =
                "INSERT INTO meta_schema_def "
                        + "(org_id, name, version, json_data) VALUES (?, ?, ?, ?)";

        withTransaction(
                tx ->
                        execute(
                                tx,
                                UPDATE_SCHEMA_QUERY,
                                update -> {
                                    int updated =
                                            update.addJsonParameter(schemaDef)
                                                    .addParameter(orgId)
                                                    .addParameter(schemaDef.getName())
                                                    .addParameter(schemaDef.getVersion())
                                                    .executeUpdate();
                                    if (updated == 0) {
                                        execute(
                                                tx,
                                                INSERT_SCHEMA_QUERY,
                                                insert ->
                                                        insert.addParameter(orgId)
                                                                .addParameter(schemaDef.getName())
                                                                .addParameter(
                                                                        schemaDef.getVersion())
                                                                .addJsonParameter(schemaDef)
                                                                .executeUpdate());
                                    }
                                }));
    }

    @Override
    public Optional<SchemaDef> getSchemaByNameWithLatestVersion(String orgId, String name) {
        final String QUERY =
                "SELECT json_data FROM meta_schema_def WHERE org_id = ? AND name = ? "
                        + "ORDER BY version DESC LIMIT 1";
        return Optional.ofNullable(
                queryWithTransaction(
                        QUERY,
                        q ->
                                q.addParameter(orgId)
                                        .addParameter(name)
                                        .executeAndFetchFirst(SchemaDef.class)));
    }

    @Override
    public Optional<SchemaDef> getSchemaByNameAndVersion(String orgId, String name, int version) {
        final String QUERY =
                "SELECT json_data FROM meta_schema_def "
                        + "WHERE org_id = ? AND name = ? AND version = ?";
        return Optional.ofNullable(
                queryWithTransaction(
                        QUERY,
                        q ->
                                q.addParameter(orgId)
                                        .addParameter(name)
                                        .addParameter(version)
                                        .executeAndFetchFirst(SchemaDef.class)));
    }

    @Override
    public List<SchemaDef> getAllSchemas(String orgId) {
        final String QUERY =
                "SELECT json_data FROM meta_schema_def WHERE org_id = ? ORDER BY name, version";
        return queryWithTransaction(
                QUERY, q -> q.addParameter(orgId).executeAndFetch(SchemaDef.class));
    }

    @Override
    public void deleteSchemaByName(String orgId, String name) {
        final String QUERY = "DELETE FROM meta_schema_def WHERE org_id = ? AND name = ?";
        executeWithTransaction(
                QUERY,
                q -> {
                    if (!q.addParameter(orgId).addParameter(name).executeDelete()) {
                        throw new NotFoundException("No such schema found by name: %s", name);
                    }
                });
    }

    @Override
    public void deleteSchemaByNameAndVersion(String orgId, String name, int version) {
        final String QUERY =
                "DELETE FROM meta_schema_def WHERE org_id = ? AND name = ? AND version = ?";
        executeWithTransaction(
                QUERY,
                q -> {
                    if (!q.addParameter(orgId)
                            .addParameter(name)
                            .addParameter(version)
                            .executeDelete()) {
                        throw new NotFoundException(
                                "No such schema found by name: %s, version: %d", name, version);
                    }
                });
    }

    @Override
    public Optional<Integer> getLatestVersion(String orgId, String name) {
        final String QUERY =
                "SELECT max(version) AS version FROM meta_schema_def WHERE org_id = ? AND name = ?";
        Integer version =
                queryWithTransaction(
                        QUERY,
                        q ->
                                q.addParameter(orgId)
                                        .addParameter(name)
                                        .executeAndFetch(
                                                rs -> {
                                                    if (!rs.next()) {
                                                        return null;
                                                    }
                                                    int value = rs.getInt(1);
                                                    return rs.wasNull() ? null : value;
                                                }));
        return Optional.ofNullable(version);
    }

    private void validate(String orgId, SchemaDef schemaDef) {
        Preconditions.checkNotNull(orgId, "orgId cannot be null");
        Preconditions.checkNotNull(schemaDef, "SchemaDef object cannot be null");
        Preconditions.checkNotNull(schemaDef.getName(), "SchemaDef name cannot be null");
        Preconditions.checkNotNull(schemaDef.getType(), "SchemaDef type cannot be null");
    }
}
