/*
 * Copyright 2026 Conductor Authors.
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
package io.orkes.conductor.scheduler.config;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.scheduler.CachingSchedulerDAO;
import io.orkes.conductor.dao.scheduler.NoOpSchedulerCacheDAO;
import io.orkes.conductor.dao.scheduler.SchedulerCacheDAO;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.health.RedisMonitor;
import io.orkes.conductor.scheduler.service.SchedulerService;
import io.orkes.conductor.scheduler.service.SchedulerServiceExecutor;
import io.orkes.conductor.scheduler.service.SchedulerTimeProvider;

@Configuration
@Conditional(SchedulerConditions.class)
public class SchedulerOssConfiguration {

    @Bean
    @ConditionalOnMissingBean(SchedulerCacheDAO.class)
    public SchedulerCacheDAO noOpSchedulerCacheDAO() {
        return new NoOpSchedulerCacheDAO();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            name = "conductor.scheduler.cache.enabled",
            havingValue = "true",
            matchIfMissing = false)
    public SchedulerDAO cachingSchedulerDAO(
            SchedulerDAO schedulerDAO, SchedulerCacheDAO schedulerCacheDAO) {
        return new CachingSchedulerDAO(schedulerDAO, schedulerCacheDAO);
    }

    @Bean
    @ConditionalOnMissingBean(SchedulerService.class)
    public SchedulerService schedulerService(
            SchedulerArchivalDAO schedulerArchivalDAO,
            SchedulerDAO schedulerDAO,
            WorkflowService workflowService,
            QueueDAO queueDAO,
            SchedulerServiceExecutor schedulerServiceExecutor,
            Optional<RedisMonitor> redisMaintenanceDAO,
            SchedulerProperties properties,
            SchedulerTimeProvider schedulerTimeProvider,
            Lock lock,
            ObjectMapper objectMapper) {
        return new SchedulerService(
                schedulerArchivalDAO,
                schedulerDAO,
                workflowService,
                queueDAO,
                schedulerServiceExecutor,
                redisMaintenanceDAO,
                properties,
                schedulerTimeProvider,
                lock,
                objectMapper);
    }
}
