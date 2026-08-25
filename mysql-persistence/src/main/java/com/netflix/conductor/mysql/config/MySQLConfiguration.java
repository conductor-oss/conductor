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
package com.netflix.conductor.mysql.config;

import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import org.conductoross.conductor.mysql.dao.MySQLFileMetadataDAO;
import org.conductoross.conductor.mysql.dao.MySQLSkillMetadataDAO;
import org.conductoross.conductor.mysql.dao.MySQLSkillPackageDAO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.mysql.dao.MySQLExecutionDAO;
import com.netflix.conductor.mysql.dao.MySQLMetadataDAO;
import com.netflix.conductor.mysql.dao.MySQLQueueDAO;

import tools.jackson.databind.ObjectMapper;

import static com.mysql.cj.exceptions.MysqlErrorNumbers.ER_LOCK_DEADLOCK;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MySQLProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "mysql")
// Import DataSourceAutoConfiguration and FlywayAutoConfiguration when mysql database is selected.
// By default these are excluded in the main module. FlywayAutoConfiguration is required so that
// the 'flyway' and 'flywayInitializer' beans exist before the MySQL DAOs are initialized.
@Import({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
public class MySQLConfiguration {

    // scheduler flyway shares this schema (own history table, baseline 0) — baseline-0 here too
    // so the non-empty-schema check can't fail when the scheduler migrates first
    @Bean
    public FlywayConfigurationCustomizer mysqlFlywayCustomizer() {
        return configuration -> configuration.baselineOnMigrate(true).baselineVersion("0");
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public MySQLMetadataDAO mySqlMetadataDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            MySQLProperties properties) {
        return new MySQLMetadataDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public MySQLExecutionDAO mySqlExecutionDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            QueueDAO queueDAO) {
        return new MySQLExecutionDAO(retryTemplate, objectMapper, dataSource, queueDAO);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public QueueDAO mySqlQueueDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLQueueDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    @ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
    public MySQLFileMetadataDAO mySqlFileMetadataDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLFileMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    @ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
    public MySQLSkillMetadataDAO mySqlSkillMetadataDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLSkillMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    @ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
    public MySQLSkillPackageDAO mySqlSkillPackageDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLSkillPackageDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    public RetryTemplate mysqlRetryTemplate(MySQLProperties properties) {
        // deadlockRetryMax counts attempts, so the retry budget is one less. Retries are immediate:
        // a deadlock victim only needs the competing transaction to finish, and it already has.
        return new RetryTemplate(
                RetryPolicy.builder()
                        .maxRetries(Math.max(0, properties.getDeadlockRetryMax() - 1))
                        .delay(Duration.ZERO)
                        .predicate(CustomRetryPolicy::isDeadLockError)
                        .build());
    }

    /** Retries only the failures MySQL reports as a lock deadlock. */
    public static class CustomRetryPolicy {

        static boolean isDeadLockError(Throwable throwable) {
            SQLException sqlException = findCauseSQLException(throwable);
            if (sqlException == null) {
                return false;
            }
            return ER_LOCK_DEADLOCK == sqlException.getErrorCode();
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
