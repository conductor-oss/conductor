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
package org.conductoross.conductor.mysql.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.mysql.dao.MySQLBaseDAO;
import com.netflix.conductor.mysql.util.Query;

import com.fasterxml.jackson.databind.ObjectMapper;

/** MySQL {@link SchemaDAO} — table {@code meta_schema_def}. */
public class MySQLSchemaDAO extends MySQLBaseDAO implements SchemaDAO {

    private static final String UPSERT =
            "INSERT INTO meta_schema_def (name, version, json_data) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE json_data = VALUES(json_data), "
                    + "modified_on = CURRENT_TIMESTAMP";

    private static final String SELECT_BY_NAME_AND_VERSION =
            "SELECT json_data FROM meta_schema_def WHERE name = ? AND version = ?";

    private static final String SELECT_LATEST_BY_NAME =
            "SELECT json_data FROM meta_schema_def WHERE name = ? ORDER BY version DESC LIMIT 1";

    private static final String SELECT_ALL =
            "SELECT json_data FROM meta_schema_def ORDER BY name, version";

    private static final String DELETE_BY_NAME_AND_VERSION =
            "DELETE FROM meta_schema_def WHERE name = ? AND version = ?";

    private static final String DELETE_BY_NAME = "DELETE FROM meta_schema_def WHERE name = ?";

    private static final String SELECT_ALL_VERSIONS_BY_NAME =
            "SELECT json_data FROM meta_schema_def WHERE name = ? ORDER BY version DESC";

    // Only name and version, so nothing deserializes a schema body to list what is registered.
    // (name, version) is InnoDB's clustered key, so the scan still walks the rows themselves —
    // this saves the JSON parsing, not the I/O.
    private static final String SELECT_ALL_NAMES_AND_VERSIONS =
            "SELECT name, version FROM meta_schema_def ORDER BY name, version";

    private static final String DELETE_BY_NAMES = "DELETE FROM meta_schema_def WHERE name IN (%s)";

    public MySQLSchemaDAO(
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

    private SchemaDef toSchema(List<String> rows) {
        return rows.isEmpty() ? null : readValue(rows.get(0), SchemaDef.class);
    }

    /**
     * One statement with a binding per name, so the whole batch is a single round trip and a single
     * transaction. A null or empty list never reaches the database.
     */
    @Override
    public int deleteAllByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return 0;
        }
        String query = String.format(DELETE_BY_NAMES, Query.generateInBindings(names.size()));
        return queryWithTransaction(query, q -> q.addParameters(names).executeUpdate());
    }

    @Override
    public List<SchemaDef> findAllVersionsByName(String name) {
        List<String> rows =
                queryWithTransaction(
                        SELECT_ALL_VERSIONS_BY_NAME,
                        q -> q.addParameter(name).executeAndFetch(String.class));
        return rows.stream().map(json -> readValue(json, SchemaDef.class)).toList();
    }

    @Override
    public List<SchemaDef> getAllShortenedSchemas() {
        return queryWithTransaction(
                SELECT_ALL_NAMES_AND_VERSIONS,
                q ->
                        q.executeAndFetch(
                                rs -> {
                                    List<SchemaDef> schemas = new ArrayList<>();
                                    while (rs.next()) {
                                        schemas.add(nameAndVersion(rs.getString(1), rs.getInt(2)));
                                    }
                                    return schemas;
                                }));
    }

    /**
     * A name and a version and nothing else — no type and no document, so the result identifies a
     * registered schema but cannot be validated against.
     */
    private static SchemaDef nameAndVersion(String name, int version) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(version);
        return schema;
    }
}
