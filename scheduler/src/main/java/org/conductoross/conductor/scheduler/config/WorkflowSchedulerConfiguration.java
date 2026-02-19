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
package org.conductoross.conductor.scheduler.config;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.PostgresSchedulerDAO;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.rest.SchedulerResource;
import org.conductoross.conductor.scheduler.service.SchedulerService;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring auto-configuration for the Conductor scheduler module.
 *
 * <p>Activated when {@code conductor.scheduler.enabled=true} (the default) AND a {@link DataSource}
 * bean is present. The DataSource guard prevents this configuration from activating in test
 * contexts that use in-memory or non-Postgres persistence, where no DataSource is registered.
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        name = "conductor.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(SchedulerProperties.class)
public class WorkflowSchedulerConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flywayForScheduler(DataSource dataSource) {
        return Flyway.configure()
                .locations("classpath:db/migration_scheduler")
                .dataSource(dataSource)
                .table("flyway_schema_history_scheduler")
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    @DependsOn("flywayForScheduler")
    public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresSchedulerDAO(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(WorkflowService.class)
    public SchedulerService schedulerService(
            SchedulerDAO schedulerDAO,
            WorkflowService workflowService,
            SchedulerProperties properties) {
        return new SchedulerService(schedulerDAO, workflowService, properties);
    }

    @Bean
    @ConditionalOnBean(SchedulerService.class)
    public SchedulerResource schedulerResource(SchedulerService schedulerService) {
        return new SchedulerResource(schedulerService);
    }
}
