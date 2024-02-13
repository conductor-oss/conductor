/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.postgreslock.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.postgreslock.lock.PostgresLock;

import jakarta.annotation.PostConstruct;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgresLockProperties.class)
@ConditionalOnProperty(name = "conductor.workflow-execution-lock.type", havingValue = "postgres")
public class PostgresLockConfiguration {

    private final PostgresLockProperties properties;
    private final DataSource dataSource;

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresLockConfiguration.class);

    public PostgresLockConfiguration(PostgresLockProperties properties, DataSource dataSource) {
        this.properties = properties;
        this.dataSource = dataSource;
        LOGGER.info("Using PostgresLock for workflow execution lock");
    }

    @Bean(initMethod = "migrate")
    @PostConstruct
    public Flyway flywayForLockDb() {
        return Flyway.configure()
                .locations("classpath:db/migration_lock_postgres")
                .schemas("lock")
                .baselineOnMigrate(true)
                .dataSource(dataSource)
                .load();
    }

    @Bean
    @DependsOn({"flywayForLockDb"})
    public Lock provideLock() {
        return new PostgresLock(dataSource);
    }
}
