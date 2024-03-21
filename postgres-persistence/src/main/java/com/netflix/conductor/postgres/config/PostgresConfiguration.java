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
package com.netflix.conductor.postgres.config;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.postgres.dao.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgresProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "postgres")
// Import the DataSourceAutoConfiguration when postgres database is selected.
// By default, the datasource configuration is excluded in the main module.
@Import(DataSourceAutoConfiguration.class)
public class PostgresConfiguration {

    DataSource dataSource;

    private final PostgresProperties properties;

    public PostgresConfiguration(DataSource dataSource, PostgresProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Bean(initMethod = "migrate")
    @PostConstruct
    public Flyway flywayForPrimaryDb() {
        FluentConfiguration config = Flyway.configure();

        if (properties.getExperimentalQueueNotify()) {
            config.locations(
                    "classpath:db/migration_postgres", "classpath:db/migration_postgres_notify");
        } else {
            config.locations("classpath:db/migration_postgres");
        }

        return config.configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .schemas(properties.getSchema())
                .dataSource(dataSource)
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public PostgresMetadataDAO postgresMetadataDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            PostgresProperties properties) {
        return new PostgresMetadataDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public PostgresExecutionDAO postgresExecutionDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new PostgresExecutionDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public PostgresPollDataDAO postgresPollDataDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            PostgresProperties properties) {
        return new PostgresPollDataDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public PostgresQueueDAO postgresQueueDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            PostgresProperties properties) {
        return new PostgresQueueDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "postgres")
    public PostgresIndexDAO postgresIndexDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            PostgresProperties properties) {
        return new PostgresIndexDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    public RetryTemplate postgresRetryTemplate(PostgresProperties properties) {
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
