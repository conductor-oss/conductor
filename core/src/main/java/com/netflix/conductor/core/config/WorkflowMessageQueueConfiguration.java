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
package com.netflix.conductor.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.dao.InMemoryWorkflowMessageQueueDAO;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;

/**
 * Spring configuration for the Workflow Message Queue (WMQ) feature.
 *
 * <p>Registers an {@link InMemoryWorkflowMessageQueueDAO} as the default DAO when no other
 * implementation (e.g. Redis) is present on the classpath and WMQ is enabled. Redis-backed
 * deployments will have the Redis DAO registered first via {@code AnyRedisCondition}, making this
 * fallback unnecessary.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.workflow-message-queue.enabled", havingValue = "true")
@EnableConfigurationProperties(WorkflowMessageQueueProperties.class)
public class WorkflowMessageQueueConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkflowMessageQueueDAO.class)
    public WorkflowMessageQueueDAO inMemoryWorkflowMessageQueueDAO(
            WorkflowMessageQueueProperties properties) {
        return new InMemoryWorkflowMessageQueueDAO(properties);
    }
}
