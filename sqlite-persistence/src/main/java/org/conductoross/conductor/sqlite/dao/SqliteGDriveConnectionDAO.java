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
package org.conductoross.conductor.sqlite.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.conductoross.conductor.common.integrations.gdrive.GDriveConnection;
import org.conductoross.conductor.dao.GDriveConnectionDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.sqlite.dao.SqliteBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqliteGDriveConnectionDAO extends SqliteBaseDAO implements GDriveConnectionDAO {

    private static final String UPSERT_CONNECTION =
            "INSERT INTO gdrive_connection (connection_id, account_name, oauth_token_json) "
                    + "VALUES (?, ?, ?) "
                    + "ON CONFLICT(connection_id) DO UPDATE SET "
                    + "account_name = excluded.account_name, "
                    + "oauth_token_json = excluded.oauth_token_json, updated_at = CURRENT_TIMESTAMP";

    private static final String SELECT_BY_ID =
            "SELECT connection_id, account_name, oauth_token_json, created_at, updated_at "
                    + "FROM gdrive_connection WHERE connection_id = ?";

    private static final String SELECT_ALL =
            "SELECT connection_id, account_name, oauth_token_json, created_at, updated_at "
                    + "FROM gdrive_connection ORDER BY connection_id";

    private static final String DELETE_BY_ID =
            "DELETE FROM gdrive_connection WHERE connection_id = ?";

    public SqliteGDriveConnectionDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void saveConnection(GDriveConnection connection) {
        executeWithTransaction(
                UPSERT_CONNECTION,
                q ->
                        q.addParameter(connection.getConnectionId())
                                .addParameter(connection.getAccountName())
                                .addParameter(connection.getOauthTokenJson())
                                .executeUpdate());
    }

    @Override
    public GDriveConnection getConnection(String connectionId) {
        return queryWithTransaction(
                SELECT_BY_ID,
                q ->
                        q.addParameter(connectionId)
                                .executeAndFetch(
                                        rs -> {
                                            if (!rs.next()) {
                                                return null;
                                            }
                                            return toGDriveConnection(rs);
                                        }));
    }

    @Override
    public List<GDriveConnection> getAllConnections() {
        return queryWithTransaction(SELECT_ALL, q -> q.executeAndFetch(this::toList));
    }

    @Override
    public void deleteConnection(String connectionId) {
        executeWithTransaction(DELETE_BY_ID, q -> q.addParameter(connectionId).executeUpdate());
    }

    private List<GDriveConnection> toList(ResultSet rs) throws SQLException {
        List<GDriveConnection> connections = new ArrayList<>();
        while (rs.next()) {
            connections.add(toGDriveConnection(rs));
        }
        return connections;
    }

    private GDriveConnection toGDriveConnection(ResultSet rs) throws SQLException {
        GDriveConnection connection = new GDriveConnection();
        connection.setConnectionId(rs.getString("connection_id"));
        connection.setAccountName(rs.getString("account_name"));
        connection.setOauthTokenJson(rs.getString("oauth_token_json"));
        connection.setCreatedAt(toEpochMillis(rs.getTimestamp("created_at")));
        connection.setUpdatedAt(toEpochMillis(rs.getTimestamp("updated_at")));
        return connection;
    }

    private Long toEpochMillis(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toEpochMilli();
    }
}
