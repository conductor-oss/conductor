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
package org.conductoross.conductor.scheduler.sqlite.config;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerArchivalDAO;
import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;

/**
 * Spring auto-configuration that registers SQLite-backed {@link SchedulerDAO} and {@link
 * SchedulerArchivalDAO}.
 *
 * <p>Active when {@code conductor.db.type=sqlite} AND {@code conductor.scheduler.enabled=true}.
 * Scheduler tables are created by the core SQLite Flyway chain ({@code db/migration_sqlite},
 * V6/V7).
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${conductor.db.type:}' == 'sqlite' && '${conductor.scheduler.enabled:false}' == 'true'")
public class SqliteSchedulerConfiguration {

    @Bean
    @DependsOn("flywayForPrimaryDb")
    public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        return new SqliteSchedulerDAO(dataSource, objectMapper);
    }

    @Bean
    @DependsOn("flywayForPrimaryDb")
    public SchedulerArchivalDAO schedulerArchivalDAO(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new SqliteSchedulerArchivalDAO(dataSource, objectMapper);
    }
}
