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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Auto-configures the SQLite-backed {@link SchedulerDAO}.
 *
 * <p>Active when {@code conductor.db.type=sqlite} AND {@code conductor.scheduler.enabled=true}. The
 * DataSource is expected to be a single-connection HikariCP pool ({@code maximumPoolSize=1}) to
 * prevent in-memory SQLite database isolation issues.
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${conductor.db.type:}' == 'sqlite' && '${conductor.scheduler.enabled:false}' == 'true'")
public class SqliteSchedulerConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flywayForScheduler(DataSource dataSource) {
        return Flyway.configure()
                .locations("classpath:db/migration_scheduler_sqlite")
                .dataSource(dataSource)
                .table("flyway_schema_history_scheduler_sqlite")
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    @Bean("sqliteSchedulerRetryTemplate")
    public RetryTemplate sqliteSchedulerRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    @Bean
    @DependsOn("flywayForScheduler")
    public SchedulerDAO schedulerDAO(
            @Qualifier("sqliteSchedulerRetryTemplate") RetryTemplate retryTemplate,
            DataSource dataSource,
            ObjectMapper objectMapper) {
        return new SqliteSchedulerDAO(retryTemplate, objectMapper, dataSource);
    }
}
