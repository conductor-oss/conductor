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

import java.util.Base64;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.SkillPackageDAO;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** PostgreSQL {@link SkillPackageDAO} — table {@code skill_package} (Base64-encoded bytes). */
public class PostgresSkillPackageDAO extends PostgresBaseDAO implements SkillPackageDAO {

    private static final String UPSERT =
            "INSERT INTO skill_package (handle, data, size_bytes, created_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (handle) DO UPDATE SET data = excluded.data, size_bytes = excluded.size_bytes";

    private static final String SELECT = "SELECT data FROM skill_package WHERE handle = ?";

    private static final String EXISTS = "SELECT 1 FROM skill_package WHERE handle = ?";

    private static final String DELETE = "DELETE FROM skill_package WHERE handle = ?";

    public PostgresSkillPackageDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void put(String handle, byte[] data) {
        String encoded = Base64.getEncoder().encodeToString(data);
        executeWithTransaction(
                UPSERT,
                q ->
                        q.addParameter(handle)
                                .addParameter(encoded)
                                .addParameter((long) data.length)
                                .addParameter(System.currentTimeMillis())
                                .executeUpdate());
    }

    @Override
    public byte[] get(String handle) {
        String encoded =
                queryWithTransaction(
                        SELECT,
                        q ->
                                q.addParameter(handle)
                                        .executeAndFetch(rs -> rs.next() ? rs.getString(1) : null));
        return encoded == null ? null : Base64.getDecoder().decode(encoded);
    }

    @Override
    public boolean exists(String handle) {
        Boolean present =
                queryWithTransaction(
                        EXISTS,
                        q -> q.addParameter(handle).executeAndFetch(java.sql.ResultSet::next));
        return Boolean.TRUE.equals(present);
    }

    @Override
    public void delete(String handle) {
        executeWithTransaction(DELETE, q -> q.addParameter(handle).executeUpdate());
    }
}
