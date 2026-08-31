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

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.mysql.dao.MySQLBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** MySQL {@link SchemaDAO} — table {@code meta_schema_def}. */
public class MySQLSchemaDAO extends MySQLBaseDAO implements SchemaDAO {

    private static final String UPSERT =
            "INSERT INTO meta_schema_def (name, version, json_data) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE json_data = VALUES(json_data), "
                    + "modified_on = CURRENT_TIMESTAMP";

    private static final String INSERT =
            "INSERT INTO meta_schema_def (name, version, json_data) VALUES (?, ?, ?)";

    /** MySQL's duplicate-key error. The one failure that means "another writer got here first". */
    private static final int ER_DUP_ENTRY = 1062;

    private static final String SELECT_BY_NAME_AND_VERSION =
            "SELECT json_data FROM meta_schema_def WHERE name = ? AND version = ?";

    private static final String SELECT_LATEST_BY_NAME =
            "SELECT json_data FROM meta_schema_def WHERE name = ? ORDER BY version DESC LIMIT 1";

    private static final String SELECT_ALL =
            "SELECT json_data FROM meta_schema_def ORDER BY name, version";

    private static final String DELETE_BY_NAME_AND_VERSION =
            "DELETE FROM meta_schema_def WHERE name = ? AND version = ?";

    private static final String DELETE_BY_NAME = "DELETE FROM meta_schema_def WHERE name = ?";

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

    /**
     * Plain insert, with the duplicate-key error caught by its code and reported as a lost race.
     *
     * <p>MySQL offers no exact equivalent of {@code ON CONFLICT (...) DO NOTHING}. {@code INSERT
     * IGNORE} downgrades every error to a warning, so a NOT NULL or oversized-payload failure would
     * look like a lost race and be retried; and a no-op {@code ON DUPLICATE KEY UPDATE} cannot be
     * told apart from a successful insert, because Connector/J reports matched rather than affected
     * rows by default. Reading the error code is what distinguishes the two.
     */
    @Override
    public boolean createSchemaIfAbsent(SchemaDef schemaDef) {
        try {
            executeWithTransaction(
                    INSERT,
                    q ->
                            q.addParameter(schemaDef.getName())
                                    .addParameter(schemaDef.getVersion())
                                    .addJsonParameter(schemaDef)
                                    .executeUpdate());
            return true;
        } catch (RuntimeException e) {
            if (isDuplicateKey(e)) {
                return false;
            }
            throw e;
        }
    }

    private static boolean isDuplicateKey(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == ER_DUP_ENTRY) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
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
}
