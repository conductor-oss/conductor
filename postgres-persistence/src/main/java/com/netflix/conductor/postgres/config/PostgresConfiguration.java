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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

import javax.sql.DataSource;

import org.conductoross.conductor.postgres.dao.PostgresFileMetadataDAO;
import org.conductoross.conductor.postgres.dao.PostgresSkillMetadataDAO;
import org.conductoross.conductor.postgres.dao.PostgresSkillPackageDAO;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.postgres.dao.*;

import jakarta.annotation.*;
import tools.jackson.databind.ObjectMapper;

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

        var locations = new ArrayList<String>();
        locations.add("classpath:db/migration_postgres");

        if (properties.getExperimentalQueueNotify()) {
            locations.add("classpath:db/migration_postgres_notify");
        }

        if (properties.isApplyDataMigrations()) {
            locations.add("classpath:db/migration_postgres_data");
        }

        config.locations(locations.toArray(new String[0]));

        return config.configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .schemas(properties.getSchema())
                .dataSource(dataSource)
                .outOfOrder(true)
                .baselineOnMigrate(true)
                // default baseline version 1 would skip V1 when the scheduler flyway
                // (same schema, own history table) migrates first
                .baselineVersion("0")
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
            ObjectMapper objectMapper,
            QueueDAO queueDAO) {
        return new PostgresExecutionDAO(retryTemplate, objectMapper, dataSource, queueDAO);
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
    public QueueDAO postgresQueueDAO(
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
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(
            name = "conductor.workflow-execution-lock.type",
            havingValue = "postgres")
    public PostgresLockDAO postgresLockDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new PostgresLockDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
    public PostgresFileMetadataDAO postgresFileMetadataDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new PostgresFileMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
    public PostgresSkillMetadataDAO postgresSkillMetadataDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new PostgresSkillMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
    public PostgresSkillPackageDAO postgresSkillPackageDAO(
            @Qualifier("postgresRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new PostgresSkillPackageDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    public RetryTemplate postgresRetryTemplate(PostgresProperties properties) {
        // Three attempts in total, so two retries, taken immediately: a deadlock victim only needs
        // the competing transaction to finish, and it already has.
        return new RetryTemplate(
                RetryPolicy.builder()
                        .maxRetries(2)
                        .delay(Duration.ZERO)
                        .predicate(CustomRetryPolicy::isDeadLockError)
                        .build());
    }

    /** Retries only the failures Postgres reports as a deadlock or a serialization conflict. */
    public static class CustomRetryPolicy {

        private static final String ER_LOCK_DEADLOCK = "40P01";
        private static final String ER_SERIALIZATION_FAILURE = "40001";

        static boolean isDeadLockError(Throwable throwable) {
            SQLException sqlException = findCauseSQLException(throwable);
            if (sqlException == null) {
                return false;
            }
            return ER_LOCK_DEADLOCK.equals(sqlException.getSQLState())
                    || ER_SERIALIZATION_FAILURE.equals(sqlException.getSQLState());
        }

        private static SQLException findCauseSQLException(Throwable throwable) {
            Throwable causeException = throwable;
            while (null != causeException && !(causeException instanceof SQLException)) {
                causeException = causeException.getCause();
            }
            return (SQLException) causeException;
        }
    }
}
