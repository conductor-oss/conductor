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
package com.netflix.conductor.mysql.dao;

import javax.sql.DataSource;

import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;

import tools.jackson.databind.ObjectMapper;

/**
 * The durable workflow message queue on MySQL.
 *
 * <p>Rows are locked with {@code SELECT ... FOR UPDATE}. The fence row is inserted with {@code ON
 * DUPLICATE KEY UPDATE}, which takes the exclusive lock at once. {@code INSERT IGNORE} would take a
 * shared lock on a row that is there, and the {@code SELECT ... FOR UPDATE} that follows would then
 * deadlock with another run that did the same.
 */
public class MySQLWorkflowMessageQueueDAO extends JdbcWorkflowMessageQueueDAO {

    public MySQLWorkflowMessageQueueDAO(
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
                + " ON DUPLICATE KEY UPDATE ordinal = ordinal";
    }
}
