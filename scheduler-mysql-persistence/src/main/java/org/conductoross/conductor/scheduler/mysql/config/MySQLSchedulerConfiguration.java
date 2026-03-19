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
package org.conductoross.conductor.scheduler.mysql.config;

import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.mysql.dao.MySQLSchedulerDAO;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring auto-configuration that registers a MySQL-backed {@link SchedulerDAO}.
 *
 * <p>Active when {@code conductor.db.type=mysql} AND {@code conductor.scheduler.enabled=true}. Runs
 * Flyway migrations for the scheduler tables using a dedicated history table so they do not
 * conflict with the main Conductor migration history.
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${conductor.db.type:}' == 'mysql' && '${conductor.scheduler.enabled:false}' == 'true'")
public class MySQLSchedulerConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flywayForScheduler(DataSource dataSource) {
        return Flyway.configure()
                .locations("classpath:db/migration_scheduler_mysql")
                .dataSource(dataSource)
                .table("flyway_schema_history_scheduler")
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    @Bean("mysqlSchedulerRetryTemplate")
    public RetryTemplate mysqlSchedulerRetryTemplate() {
        SimpleRetryPolicy retryPolicy = new DeadlockRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    @Bean
    @DependsOn("flywayForScheduler")
    public SchedulerDAO schedulerDAO(
            @Qualifier("mysqlSchedulerRetryTemplate") RetryTemplate retryTemplate,
            DataSource dataSource,
            ObjectMapper objectMapper) {
        return new MySQLSchedulerDAO(retryTemplate, objectMapper, dataSource);
    }

    static class DeadlockRetryPolicy extends SimpleRetryPolicy {

        private static final String ER_LOCK_DEADLOCK = "1213";

        @Override
        public boolean canRetry(final RetryContext context) {
            final Optional<Throwable> lastThrowable =
                    Optional.ofNullable(context.getLastThrowable());
            return lastThrowable
                    .map(throwable -> super.canRetry(context) && isDeadLockError(throwable))
                    .orElseGet(() -> super.canRetry(context));
        }

        private boolean isDeadLockError(Throwable throwable) {
            SQLException sqlException = findCauseSQLException(throwable);
            if (sqlException == null) {
                return false;
            }
            return ER_LOCK_DEADLOCK.equals(sqlException.getSQLState());
        }

        private SQLException findCauseSQLException(Throwable throwable) {
            Throwable cause = throwable;
            while (null != cause && !(cause instanceof SQLException)) {
                cause = cause.getCause();
            }
            return (SQLException) cause;
        }
    }
}
