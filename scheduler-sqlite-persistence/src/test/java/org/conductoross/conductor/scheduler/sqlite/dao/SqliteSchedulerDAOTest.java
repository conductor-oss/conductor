/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.conductoross.conductor.scheduler.sqlite.dao;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.AbstractSchedulerDAOTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.flywaydb.core.Flyway;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration(
        classes = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            SqliteSchedulerDAOTest.SqliteTestConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:sqlite::memory:",
            "spring.datasource.driver-class-name=org.sqlite.JDBC",
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.flyway.enabled=false"
        })
public class SqliteSchedulerDAOTest extends AbstractSchedulerDAOTest {

    @TestConfiguration
    static class SqliteTestConfiguration {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapperProvider().getObjectMapper();
        }

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

        @Bean
        @DependsOn("flywayForScheduler")
        public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
            return new SqliteSchedulerDAO(dataSource, objectMapper);
        }
    }

    @Autowired private SchedulerDAO schedulerDAO;
    @Autowired private DataSource dataSource;

    @Override
    protected SchedulerDAO dao() {
        return schedulerDAO;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
