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

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerDAO;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring auto-configuration that registers a SQLite-backed {@link SchedulerDAO}.
 *
 * <p>Active when {@code conductor.db.type=sqlite} AND {@code conductor.scheduler.enabled=true}.
 * Runs Flyway migrations for the scheduler tables using a dedicated history table so they do not
 * conflict with the main Conductor migration history.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "sqlite")
@ConditionalOnProperty(name = "conductor.scheduler.enabled", havingValue = "true")
public class SqliteSchedulerConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flywayForScheduler(DataSource dataSource) {
        return Flyway.configure()
                .locations("classpath:db/migration_scheduler_sqlite")
                .dataSource(dataSource)
                .table("flyway_schema_history_scheduler")
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .mixed(true)
                .load();
    }

    @Bean
    @DependsOn("flywayForScheduler")
    public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        return new SqliteSchedulerDAO(dataSource, objectMapper);
    }
}
