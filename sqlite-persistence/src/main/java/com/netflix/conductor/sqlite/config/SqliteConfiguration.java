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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
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

    @PostConstruct
    public void initializeSqlite() {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            // Enable WAL mode for better concurrent access
            stmt.execute("PRAGMA journal_mode=WAL");

            // Set busy timeout to 30 seconds (30000 ms)
            // This makes SQLite wait up to 30s when encountering locks
            stmt.execute("PRAGMA busy_timeout=30000");

            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys=ON");

            // Optimize for concurrency
            stmt.execute("PRAGMA synchronous=NORMAL");

            // Use memory for temporary storage
            stmt.execute("PRAGMA temp_store=MEMORY");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    @Bean(initMethod = "migrate")
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
        CustomRetryPolicy retryPolicy = new CustomRetryPolicy();
        retryPolicy.setMaxAttempts(10); // Increased for SQLite locking scenarios

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(50L); // Start with 50ms
        backOffPolicy.setMultiplier(2.0); // Double each time
        backOffPolicy.setMaxInterval(5000L); // Max 5 seconds

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    public static class CustomRetryPolicy extends SimpleRetryPolicy {

        // PostgreSQL error codes
        private static final String ER_LOCK_DEADLOCK = "40P01";
        private static final String ER_SERIALIZATION_FAILURE = "40001";

        // SQLite error codes
        private static final int SQLITE_BUSY = 5;
        private static final int SQLITE_LOCKED = 6;

        @Override
        public boolean canRetry(final RetryContext context) {
            final Optional<Throwable> lastThrowable =
                    Optional.ofNullable(context.getLastThrowable());
            return lastThrowable
                    .map(
                            throwable ->
                                    super.canRetry(context)
                                            && (isDeadLockError(throwable)
                                                    || isSqliteBusyError(throwable)))
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

        private boolean isSqliteBusyError(Throwable throwable) {
            SQLException sqlException = findCauseSQLException(throwable);
            if (sqlException == null) {
                return false;
            }

            // Check for SQLite BUSY (5) or LOCKED (6) error codes
            int errorCode = sqlException.getErrorCode();
            if (errorCode == SQLITE_BUSY || errorCode == SQLITE_LOCKED) {
                return true;
            }

            // Also check message for SQLite busy/locked indicators
            String message = sqlException.getMessage();
            if (message != null) {
                return message.contains("SQLITE_BUSY")
                        || message.contains("database is locked")
                        || message.contains("SQLITE_LOCKED")
                        || message.contains("table is locked");
            }

            return false;
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
