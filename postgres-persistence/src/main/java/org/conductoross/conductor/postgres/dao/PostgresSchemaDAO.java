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
package org.conductoross.conductor.postgres.dao;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** PostgreSQL {@link SchemaDAO} — table {@code meta_schema_def}. */
public class PostgresSchemaDAO extends PostgresBaseDAO implements SchemaDAO {

    private static final String UPSERT =
            "INSERT INTO meta_schema_def (name, version, json_data) VALUES (?, ?, ?) "
                    + "ON CONFLICT (name, version) DO UPDATE SET json_data = excluded.json_data, "
                    + "modified_on = CURRENT_TIMESTAMP";

    // DO NOTHING leaves the decision to the primary key rather than to a preceding read, and
    // reports the loss as zero rows affected instead of an exception.
    private static final String INSERT_IF_ABSENT =
            "INSERT INTO meta_schema_def (name, version, json_data) VALUES (?, ?, ?) "
                    + "ON CONFLICT (name, version) DO NOTHING";

    private static final String SELECT_BY_NAME_AND_VERSION =
            "SELECT json_data FROM meta_schema_def WHERE name = ? AND version = ?";

    private static final String SELECT_LATEST_BY_NAME =
            "SELECT json_data FROM meta_schema_def WHERE name = ? ORDER BY version DESC LIMIT 1";

    private static final String SELECT_ALL =
            "SELECT json_data FROM meta_schema_def ORDER BY name, version";

    private static final String DELETE_BY_NAME_AND_VERSION =
            "DELETE FROM meta_schema_def WHERE name = ? AND version = ?";

    private static final String DELETE_BY_NAME = "DELETE FROM meta_schema_def WHERE name = ?";

    public PostgresSchemaDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void saveSchema(SchemaDef schemaDef) {
        executeWithTransaction(
                UPSERT,
                q ->
                        q.addParameter(schemaDef.getName())
                                .addParameter(schemaDef.getVersion())
                                .addJsonParameter(schemaDef)
                                .executeUpdate());
    }

    @Override
    public boolean createSchemaIfAbsent(SchemaDef schemaDef) {
        Integer inserted =
                queryWithTransaction(
                        INSERT_IF_ABSENT,
                        q ->
                                q.addParameter(schemaDef.getName())
                                        .addParameter(schemaDef.getVersion())
                                        .addJsonParameter(schemaDef)
                                        .executeUpdate());
        return inserted != null && inserted > 0;
    }

    @Override
    public Optional<SchemaDef> getSchema(String name, int version) {
        return queryWithTransaction(
                SELECT_BY_NAME_AND_VERSION,
                q ->
                        toSchema(
                                q.addParameter(name)
                                        .addParameter(version)
                                        .executeAndFetch(String.class)));
    }

    @Override
    public Optional<SchemaDef> getLatestSchema(String name) {
        return queryWithTransaction(
                SELECT_LATEST_BY_NAME,
                q -> toSchema(q.addParameter(name).executeAndFetch(String.class)));
    }

    @Override
    public List<SchemaDef> getAllSchemas() {
        List<String> rows = queryWithTransaction(SELECT_ALL, q -> q.executeAndFetch(String.class));
        return rows.stream().map(json -> readValue(json, SchemaDef.class)).toList();
    }

    @Override
    public void deleteSchema(String name, int version) {
        executeWithTransaction(
                DELETE_BY_NAME_AND_VERSION,
                q -> q.addParameter(name).addParameter(version).executeUpdate());
    }

    @Override
    public void deleteSchemaByName(String name) {
        executeWithTransaction(DELETE_BY_NAME, q -> q.addParameter(name).executeUpdate());
    }

    private Optional<SchemaDef> toSchema(List<String> rows) {
        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(readValue(rows.get(0), SchemaDef.class));
    }
}
