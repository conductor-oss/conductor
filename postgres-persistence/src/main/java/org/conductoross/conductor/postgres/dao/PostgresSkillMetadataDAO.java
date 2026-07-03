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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.SkillMetadataDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** PostgreSQL {@link SkillMetadataDAO} — table {@code skill_metadata}. */
public class PostgresSkillMetadataDAO extends PostgresBaseDAO implements SkillMetadataDAO {

    private static final String CLEAR_LATEST =
            "UPDATE skill_metadata SET is_latest = ? WHERE owner_id = ? AND name = ?";

    private static final String UPSERT_LATEST =
            "INSERT INTO skill_metadata (owner_id, name, version, is_latest, detail_json, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (owner_id, name, version) DO UPDATE SET "
                    + "is_latest = excluded.is_latest, detail_json = excluded.detail_json, "
                    + "updated_at = excluded.updated_at";

    private static final String UPSERT_NO_LATEST =
            "INSERT INTO skill_metadata (owner_id, name, version, is_latest, detail_json, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (owner_id, name, version) DO UPDATE SET "
                    + "detail_json = excluded.detail_json, updated_at = excluded.updated_at";

    private static final String SELECT_DETAIL =
            "SELECT detail_json FROM skill_metadata WHERE owner_id = ? AND name = ? AND version = ?";

    private static final String SELECT_LATEST_VERSION =
            "SELECT version FROM skill_metadata WHERE owner_id = ? AND name = ? AND is_latest = ?";

    private static final String SELECT_VERSIONS =
            "SELECT detail_json FROM skill_metadata WHERE owner_id = ? AND name = ?";

    private static final String SELECT_ALL =
            "SELECT detail_json FROM skill_metadata WHERE owner_id = ?";

    private static final String SELECT_LATEST_ALL =
            "SELECT detail_json FROM skill_metadata WHERE owner_id = ? AND is_latest = ?";

    private static final String DELETE_ONE =
            "DELETE FROM skill_metadata WHERE owner_id = ? AND name = ? AND version = ?";

    private static final String SELECT_NEWEST_REMAINING =
            "SELECT version FROM skill_metadata WHERE owner_id = ? AND name = ? "
                    + "ORDER BY updated_at DESC NULLS LAST LIMIT 1";

    private static final String SET_LATEST =
            "UPDATE skill_metadata SET is_latest = ? WHERE owner_id = ? AND name = ? AND version = ?";

    public PostgresSkillMetadataDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void save(
            String ownerId,
            String name,
            String version,
            boolean makeLatest,
            String detailJson,
            Long createdAt,
            Long updatedAt) {
        if (makeLatest) {
            executeWithTransaction(
                    CLEAR_LATEST,
                    q ->
                            q.addParameter(false)
                                    .addParameter(ownerId)
                                    .addParameter(name)
                                    .executeUpdate());
            executeWithTransaction(
                    UPSERT_LATEST,
                    q ->
                            q.addParameter(ownerId)
                                    .addParameter(name)
                                    .addParameter(version)
                                    .addParameter(true)
                                    .addParameter(detailJson)
                                    .addParameter(createdAt)
                                    .addParameter(updatedAt)
                                    .executeUpdate());
        } else {
            executeWithTransaction(
                    UPSERT_NO_LATEST,
                    q ->
                            q.addParameter(ownerId)
                                    .addParameter(name)
                                    .addParameter(version)
                                    .addParameter(false)
                                    .addParameter(detailJson)
                                    .addParameter(createdAt)
                                    .addParameter(updatedAt)
                                    .executeUpdate());
        }
    }

    @Override
    public Optional<String> find(String ownerId, String name, String version) {
        String json =
                queryWithTransaction(
                        SELECT_DETAIL,
                        q ->
                                q.addParameter(ownerId)
                                        .addParameter(name)
                                        .addParameter(version)
                                        .executeAndFetch(rs -> rs.next() ? rs.getString(1) : null));
        return Optional.ofNullable(json);
    }

    @Override
    public Optional<String> latestVersion(String ownerId, String name) {
        String version =
                queryWithTransaction(
                        SELECT_LATEST_VERSION,
                        q ->
                                q.addParameter(ownerId)
                                        .addParameter(name)
                                        .addParameter(true)
                                        .executeAndFetch(rs -> rs.next() ? rs.getString(1) : null));
        return Optional.ofNullable(version);
    }

    @Override
    public List<String> listVersions(String ownerId, String name) {
        return queryWithTransaction(
                SELECT_VERSIONS,
                q -> q.addParameter(ownerId).addParameter(name).executeAndFetch(this::toJsonList));
    }

    @Override
    public List<String> list(String ownerId, boolean allVersions) {
        if (allVersions) {
            return queryWithTransaction(
                    SELECT_ALL, q -> q.addParameter(ownerId).executeAndFetch(this::toJsonList));
        }
        return queryWithTransaction(
                SELECT_LATEST_ALL,
                q -> q.addParameter(ownerId).addParameter(true).executeAndFetch(this::toJsonList));
    }

    @Override
    public void delete(String ownerId, String name, String version) {
        Optional<String> latest = latestVersion(ownerId, name);
        executeWithTransaction(
                DELETE_ONE,
                q ->
                        q.addParameter(ownerId)
                                .addParameter(name)
                                .addParameter(version)
                                .executeUpdate());
        if (latest.isPresent() && latest.get().equals(version)) {
            String newest =
                    queryWithTransaction(
                            SELECT_NEWEST_REMAINING,
                            q ->
                                    q.addParameter(ownerId)
                                            .addParameter(name)
                                            .executeAndFetch(
                                                    rs -> rs.next() ? rs.getString(1) : null));
            if (newest != null) {
                executeWithTransaction(
                        SET_LATEST,
                        q ->
                                q.addParameter(true)
                                        .addParameter(ownerId)
                                        .addParameter(name)
                                        .addParameter(newest)
                                        .executeUpdate());
            }
        }
    }

    private List<String> toJsonList(ResultSet rs) throws SQLException {
        List<String> list = new ArrayList<>();
        while (rs.next()) {
            list.add(rs.getString(1));
        }
        return list;
    }
}
