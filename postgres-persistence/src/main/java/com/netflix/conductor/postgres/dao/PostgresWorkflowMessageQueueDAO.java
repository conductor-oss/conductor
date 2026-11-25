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

import javax.sql.DataSource;

import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;

import tools.jackson.databind.ObjectMapper;

/**
 * The durable workflow message queue on Postgres.
 *
 * <p>Rows are locked with {@code SELECT ... FOR UPDATE}. A fence row that is there is left alone
 * ({@code ON CONFLICT DO NOTHING}); the SELECT that follows locks it.
 */
public class PostgresWorkflowMessageQueueDAO extends JdbcWorkflowMessageQueueDAO {

    public PostgresWorkflowMessageQueueDAO(
            DataSource dataSource,
            ObjectMapper objectMapper,
            WorkflowMessageQueueProperties properties) {
        super(dataSource, objectMapper, properties);
    }

    @Override
    protected String forUpdate() {
        return " FOR UPDATE";
    }

    @Override
    protected String insertFenceIfAbsent() {
        return "INSERT INTO workflow_message_claim (workflow_id, claimant, ordinal) VALUES (?, ?, ?)"
                + " ON CONFLICT (workflow_id, claimant) DO NOTHING";
    }
}
