/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.sqlite.config;

import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.core.sync.local.LocalOnlyLock;
import com.netflix.conductor.sqlite.dao.*;
import com.netflix.conductor.sqlite.dao.metadata.SqliteEventHandlerMetadataDAO;
import com.netflix.conductor.sqlite.dao.metadata.SqliteMetadataDAO;
import com.netflix.conductor.sqlite.dao.metadata.SqliteTaskMetadataDAO;
import com.netflix.conductor.sqlite.dao.metadata.SqliteWorkflowMetadataDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqliteProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "sqlite")
@Import(DataSourceAutoConfiguration.class)
@ConfigurationProperties(prefix = "conductor.sqlite")
public class SqliteConfiguration {

    DataSource dataSource;

    private final SqliteProperties properties;

    public SqliteConfiguration(DataSource dataSource, SqliteProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Bean(initMethod = "migrate")
    @PostConstruct
    public Flyway flywayForPrimaryDb() {
        FluentConfiguration config =
                Flyway.configure()
                        .dataSource(dataSource) // SQLite doesn't need username/password
                        .locations("classpath:db/migration_sqlite") // Location of migration files
                        .sqlMigrationPrefix("V") // V1, V2, etc.
                        .sqlMigrationSeparator("__") // V1__description
                        .mixed(true) // Allow mixed migrations (both versioned and repeatable)
                        .validateOnMigrate(true)
                        .cleanDisabled(false);

        return new Flyway(config);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqliteMetadataDAO sqliteMetadataDAO(
            SqliteTaskMetadataDAO taskMetadataDAO,
            SqliteWorkflowMetadataDAO workflowMetadataDAO,
            SqliteEventHandlerMetadataDAO eventHandlerMetadataDAO) {
        return new SqliteMetadataDAO(taskMetadataDAO, workflowMetadataDAO, eventHandlerMetadataDAO);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqliteEventHandlerMetadataDAO sqliteEventHandlerMetadataDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new SqliteEventHandlerMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqliteWorkflowMetadataDAO sqliteWorkflowMetadataDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new SqliteWorkflowMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqliteTaskMetadataDAO sqliteTaskMetadataDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new SqliteTaskMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqliteExecutionDAO sqliteExecutionDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new SqliteExecutionDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqlitePollDataDAO sqlitePollDataDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new SqlitePollDataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public SqliteQueueDAO sqliteQueueDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            SqliteProperties properties) {
        return new SqliteQueueDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "sqlite")
    public SqliteIndexDAO sqliteIndexDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            SqliteProperties properties) {
        return new SqliteIndexDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.workflow-execution-lock.type", havingValue = "sqlite")
    public Lock sqliteLockDAO(
            @Qualifier("sqliteRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new LocalOnlyLock();
    }

    @Bean
    public RetryTemplate sqliteRetryTemplate(SqliteProperties properties) {
        SimpleRetryPolicy retryPolicy = new CustomRetryPolicy();
        retryPolicy.setMaxAttempts(3);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    public static class CustomRetryPolicy extends SimpleRetryPolicy {

        private static final String ER_LOCK_DEADLOCK = "40P01";
        private static final String ER_SERIALIZATION_FAILURE = "40001";

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
            return ER_LOCK_DEADLOCK.equals(sqlException.getSQLState())
                    || ER_SERIALIZATION_FAILURE.equals(sqlException.getSQLState());
        }

        private SQLException findCauseSQLException(Throwable throwable) {
            Throwable causeException = throwable;
            while (null != causeException && !(causeException instanceof SQLException)) {
                causeException = causeException.getCause();
            }
            return (SQLException) causeException;
        }
    }
}
