/*
 * Copyright 2022 Netflix, Inc.
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
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.postgres.dao.PostgresExecutionDAO;
import com.netflix.conductor.postgres.dao.PostgresMetadataDAO;
import com.netflix.conductor.postgres.dao.PostgresQueueDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgresProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "postgres")
// Import the DataSourceAutoConfiguration when postgres database is selected.
// By default, the datasource configuration is excluded in the main module.
@Import(DataSourceAutoConfiguration.class)
public class PostgresConfiguration {

    DataSource dataSource;

    public PostgresConfiguration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean(initMethod = "migrate")
    @PostConstruct
    public Flyway flywayForPrimaryDb() {
        return Flyway.configure()
                .locations("classpath:db/migration_postgres")
                .schemas("public")
                .dataSource(dataSource)
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
    public PostgresQueueDAO postgresQueueDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new PostgresQueueDAO(retryTemplate, objectMapper, dataSource);
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
