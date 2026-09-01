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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private static final String SELECT_ALL_VERSIONS_BY_NAME =
            "SELECT json_data FROM meta_schema_def WHERE name = ? ORDER BY version DESC";

    private static final String SELECT_ALL_SHORTENED =
            "SELECT name, version FROM meta_schema_def ORDER BY name, version";

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
    public void save(SchemaDef schemaDef) {
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
    public SchemaDef findByNameAndVersion(String name, Integer version) {
        Objects.requireNonNull(version, "Schema version cannot be null");
        return queryWithTransaction(
                SELECT_BY_NAME_AND_VERSION,
                q ->
                        toSchema(
                                q.addParameter(name)
                                        .addParameter(version)
                                        .executeAndFetch(String.class)));
    }

    @Override
    public SchemaDef findLatestVersionByName(String name) {
        return queryWithTransaction(
                SELECT_LATEST_BY_NAME,
                q -> toSchema(q.addParameter(name).executeAndFetch(String.class)));
    }

    @Override
    public List<SchemaDef> findAllVersionsByName(String name) {
        List<String> rows =
                queryWithTransaction(
                        SELECT_ALL_VERSIONS_BY_NAME,
                        q -> q.addParameter(name).executeAndFetch(String.class));
        return rows.stream().map(json -> readValue(json, SchemaDef.class)).toList();
    }

    /** A projection rather than a read-and-discard: the payload column is never fetched. */
    @Override
    public List<SchemaDef> getAllShortenedSchemas() {
        return queryWithTransaction(
                SELECT_ALL_SHORTENED,
                q ->
                        q.executeAndFetch(
                                rs -> {
                                    List<SchemaDef> shortened = new ArrayList<>();
                                    while (rs.next()) {
                                        SchemaDef summary = new SchemaDef();
                                        summary.setName(rs.getString(1));
                                        summary.setVersion(rs.getInt(2));
                                        shortened.add(summary);
                                    }
                                    return shortened;
                                }));
    }

    @Override
    public List<SchemaDef> getAll() {
        List<String> rows = queryWithTransaction(SELECT_ALL, q -> q.executeAndFetch(String.class));
        return rows.stream().map(json -> readValue(json, SchemaDef.class)).toList();
    }

    @Override
    public int deleteByNameAndVersion(String name, Integer version) {
        Objects.requireNonNull(version, "Schema version cannot be null");
        return queryWithTransaction(
                DELETE_BY_NAME_AND_VERSION,
                q -> q.addParameter(name).addParameter(version).executeUpdate());
    }

    @Override
    public int deleteAllByName(String name) {
        return queryWithTransaction(DELETE_BY_NAME, q -> q.addParameter(name).executeUpdate());
    }

    @Override
    public int deleteAllByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return 0;
        }
        String placeholders = names.stream().map(n -> "?").collect(Collectors.joining(","));
        String sql = "DELETE FROM meta_schema_def WHERE name IN (" + placeholders + ")";
        return queryWithTransaction(sql, q -> q.addParameters(names.toArray()).executeUpdate());
    }

    private SchemaDef toSchema(List<String> rows) {
        return rows.isEmpty() ? null : readValue(rows.get(0), SchemaDef.class);
    }
}
