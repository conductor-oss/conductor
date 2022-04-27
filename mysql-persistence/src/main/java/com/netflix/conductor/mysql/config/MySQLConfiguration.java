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
package com.netflix.conductor.mysql.config;

import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

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

import com.netflix.conductor.mysql.dao.MySQLExecutionDAO;
import com.netflix.conductor.mysql.dao.MySQLMetadataDAO;
import com.netflix.conductor.mysql.dao.MySQLQueueDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.mysql.cj.exceptions.MysqlErrorNumbers.ER_LOCK_DEADLOCK;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MySQLProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "mysql")
// Import the DataSourceAutoConfiguration when mysql database is selected.
// By default the datasource configuration is excluded in the main module.
@Import(DataSourceAutoConfiguration.class)
public class MySQLConfiguration {

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
            DataSource dataSource) {
        return new MySQLExecutionDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public MySQLQueueDAO mySqlQueueDAO(
            @Qualifier("mysqlRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLQueueDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    public RetryTemplate mysqlRetryTemplate(MySQLProperties properties) {
        SimpleRetryPolicy retryPolicy = new CustomRetryPolicy();
        retryPolicy.setMaxAttempts(properties.getDeadlockRetryMax());

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    public static class CustomRetryPolicy extends SimpleRetryPolicy {

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
            return ER_LOCK_DEADLOCK == sqlException.getErrorCode();
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
